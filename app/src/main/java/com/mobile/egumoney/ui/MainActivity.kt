package com.mobile.egumoney.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mobile.egumoney.R
import com.mobile.egumoney.data.ExpenseEntity
import com.mobile.egumoney.databinding.ActivityMainBinding
import com.mobile.egumoney.databinding.DialogEditExpenseBinding
import com.mobile.egumoney.viewmodel.ExpenseViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ExpenseViewModel by viewModels()
    private lateinit var expenseAdapter: ExpenseAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentWeatherStatus = "☀️ 맑음"
    private var isBudgetExceededNotified = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            loadCurrentWeather()
        } else {
            binding.tvWeatherStatus.text = "📍 위치 권한 거부됨 (날씨: ☀️ 맑음 고정)"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupRecyclerView()
        setupListeners()
        observeViewModel()
        checkLocationPermission()

        switchTab(0)
        viewModel.generateAiFeedback()
    }

    private fun setupRecyclerView() {
        expenseAdapter = ExpenseAdapter(
            onEditClick = { expense -> showEditDialog(expense) },
            onDeleteClick = { expense -> showDeleteConfirmDialog(expense) }
        )
        binding.rvExpenseList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = expenseAdapter
        }

        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        binding.spinnerManualCategory.adapter = spinnerAdapter
    }

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            val sentence = binding.etExpenseInput.text.toString().trim()
            if (sentence.isNotEmpty()) {
                viewModel.addExpenseFromNaturalLanguage(sentence, currentWeatherStatus)
                binding.etExpenseInput.setText("")
                switchTab(2)
            } else {
                Toast.makeText(this, "내역을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnManualRegister.setOnClickListener {
            val title = binding.etManualTitle.text.toString().trim()
            val amountStr = binding.etManualAmount.text.toString().trim()
            val category = binding.spinnerManualCategory.selectedItem?.toString() ?: "기타"

            if (title.isNotEmpty() && amountStr.isNotEmpty()) {
                val amount = amountStr.toIntOrNull() ?: 0
                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val expense = ExpenseEntity(
                    date = dateStr,
                    title = title,
                    amount = amount,
                    category = category,
                    weather = currentWeatherStatus
                )
                viewModel.insertExpense(expense)
                binding.etManualTitle.setText("")
                binding.etManualAmount.setText("")
                Toast.makeText(this, "✅ '$title' 저장 완료!", Toast.LENGTH_SHORT).show()
                switchTab(2)
            } else {
                Toast.makeText(this, "항목명과 금액을 모두 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSetBudget.setOnClickListener { showBudgetSettingsDialog() }
        binding.tabDashboard.setOnClickListener { switchTab(0) }
        binding.tabAdd.setOnClickListener { switchTab(1) }
        binding.tabHistory.setOnClickListener { switchTab(2) }
    }

    private fun observeViewModel() {
        viewModel.allExpenses.observe(this) { expenses ->
            expenseAdapter.submitList(expenses)
            updateCharts(expenses)
            updateTotalExpenseText(expenses)
            updateBudgetStatus(expenses)
        }
        viewModel.isLoading.observe(this) { isLoading ->
            binding.layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        viewModel.aiFeedback.observe(this) { feedback ->
            binding.tvAiFeedback.text = feedback
        }
    }

    // [수정된 차트 업데이트 함수]
    private fun updateCharts(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }

        // 1. 차트 잔상 제거
        binding.pieChart.clear()
        binding.barChart.clear()

        // 2. 데이터 그룹화
        val categoryGroups = monthlyExpenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        if (categoryGroups.isEmpty()) return

        // 3. 파이 차트 데이터 세팅
        val pieEntries = categoryGroups.map { PieEntry(it.value.toFloat(), it.key) }
        val pieDataSet = PieDataSet(pieEntries, "")
        
        // 4. 색상 매핑 (순서대로)
  // 수정된 색상 매핑 부분
// 수정된 색상 매핑 부분
        pieDataSet.colors = pieEntries.map {
    when (it.label.trim()) {
        "식비" -> ContextCompat.getColor(this, R.color.cat_food)
        "교통비" -> ContextCompat.getColor(this, R.color.cat_transport)
        "쇼핑" -> ContextCompat.getColor(this, R.color.cat_shopping)
        "문화" -> ContextCompat.getColor(this, R.color.cat_culture)
        "투자" -> ContextCompat.getColor(this, R.color.cat_investment) // 추가함
        "기타" -> ContextCompat.getColor(this, R.color.cat_etc)        // 명시적으로 추가함
        else -> ContextCompat.getColor(this, R.color.cat_etc)         // 정의되지 않은 카테고리는 기본적으로 기타 색상
    }
}
        pieDataSet.valueTextSize = 13f

        binding.pieChart.apply {
            data = PieData(pieDataSet)
            description.isEnabled = false
            centerText = "이번 달 소비"
            marker = null // 마커 제거
            invalidate()
        }

        // 바 차트 로직
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateList = ArrayList<String>()
        val barEntries = ArrayList<BarEntry>()
        val calendar = Calendar.getInstance()
        val currentDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        for (i in 0..6) {
            calendar.set(Calendar.DAY_OF_YEAR, currentDayOfYear - (6 - i))
            val dateStr = sdf.format(calendar.time)
            dateList.add(dateStr.substring(5))
            val dailySum = expenses.filter { it.date == dateStr }.sumOf { it.amount }
            barEntries.add(BarEntry(i.toFloat(), dailySum.toFloat()))
        }

        val barDataSet = BarDataSet(barEntries, "지출액").apply {
            color = ContextCompat.getColor(this@MainActivity, R.color.cat_culture)
        }

        binding.barChart.apply {
            data = BarData(barDataSet)
            description.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(dateList)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            marker = null
            invalidate()
        }
    }

    private fun updateBudgetStatus(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }
        val totalSpent = monthlyExpenses.sumOf { it.amount }
        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
        val totalBudget = categories.sumOf { viewModel.getBudgetLimit(it) }
        val remaining = totalBudget - totalSpent
        val dec = DecimalFormat("#,###")

        binding.tvBudgetStatus.text = "이번 달 예산: ${dec.format(totalBudget)}원 / 잔액: ${dec.format(remaining)}원"
        if (remaining < 0) {
            binding.tvBudgetStatus.setTextColor(Color.RED)
            if (!isBudgetExceededNotified) {
                Toast.makeText(this, "🚨 예산을 ${dec.format(Math.abs(remaining))}원 초과했습니다!", Toast.LENGTH_LONG).show()
                isBudgetExceededNotified = true
            }
        } else {
            binding.tvBudgetStatus.setTextColor(Color.BLACK)
            isBudgetExceededNotified = false
        }
    }

    private fun updateTotalExpenseText(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }
        binding.tvTotalExpense.text = "${DecimalFormat("#,###").format(monthlyExpenses.sumOf { it.amount })}원"
    }

    private fun switchTab(tabIndex: Int) {
        binding.containerDashboard.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        binding.containerAdd.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        binding.containerHistory.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            loadCurrentWeather()
        } else {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadCurrentWeather() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    val weatherResp = viewModel.fetchWeather(location.latitude, location.longitude)
                    if (weatherResp != null) {
                        currentWeatherStatus = "${weatherResp.weatherList[0].description} (${weatherResp.mainInfo.temp}℃)"
                        binding.tvWeatherStatus.text = "📍 날씨: $currentWeatherStatus"
                    }
                }
            }
        }
    }

    private fun showEditDialog(expense: ExpenseEntity) {
        val dialogBinding = DialogEditExpenseBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(this)
            .setView(dialogBinding.root)

        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "기타")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.spinnerEditCategory.adapter = spinnerAdapter

        val catIndex = categories.indexOf(expense.category).coerceAtLeast(0)
        dialogBinding.spinnerEditCategory.setSelection(catIndex)
        dialogBinding.etEditTitle.setText(expense.title)
        dialogBinding.etEditAmount.setText(expense.amount.toString())

        val alertDialog = builder.create()

        dialogBinding.btnEditCancel.setOnClickListener { alertDialog.dismiss() }

        dialogBinding.btnEditSave.setOnClickListener {
            val title = dialogBinding.etEditTitle.text.toString().trim()
            val amountStr = dialogBinding.etEditAmount.text.toString().trim()
            val category = dialogBinding.spinnerEditCategory.selectedItem.toString()

            if (title.isNotEmpty() && amountStr.isNotEmpty()) {
                val amount = amountStr.toIntOrNull() ?: 0
                val updatedExpense = expense.copy(title = title, amount = amount, category = category)
                viewModel.updateExpense(updatedExpense)
                alertDialog.dismiss()
                Toast.makeText(this, "수정되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "모든 필드를 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }
        alertDialog.show()
    }

    private fun showDeleteConfirmDialog(expense: ExpenseEntity) {
        AlertDialog.Builder(this)
            .setTitle("소비 내역 삭제")
            .setMessage("'${expense.title}' 내역을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deleteExpense(expense)
                Toast.makeText(this, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showBudgetSettingsDialog() {
        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
        val dialogBinding = DialogEditExpenseBinding.inflate(layoutInflater)
        
        dialogBinding.spinnerEditCategory.visibility = View.VISIBLE
        dialogBinding.etEditTitle.visibility = View.GONE 
        dialogBinding.etEditAmount.hint = "한 달 예산 금액(원)"
        
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.spinnerEditCategory.adapter = spinnerAdapter
        
        val currentLimit = viewModel.getBudgetLimit(categories[0])
        dialogBinding.etEditAmount.setText(currentLimit.toString())
        
        val builder = AlertDialog.Builder(this).setView(dialogBinding.root)
        val alertDialog = builder.create()
        
        dialogBinding.btnEditCancel.setOnClickListener { alertDialog.dismiss() }
        
        dialogBinding.btnEditSave.setOnClickListener {
            val limitStr = dialogBinding.etEditAmount.text.toString().trim()
            val category = dialogBinding.spinnerEditCategory.selectedItem.toString()
            
            if (limitStr.isNotEmpty()) {
                val limit = limitStr.toIntOrNull() ?: 0
                viewModel.setBudgetLimit(category, limit)
                alertDialog.dismiss()
                Toast.makeText(this, "[$category] 예산이 ${limit}원으로 설정되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "예산을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }
        alertDialog.show()
    }
}