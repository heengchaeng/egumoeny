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
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.mobile.egumoney.ui.CustomMarkerView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ExpenseViewModel by viewModels()
    private lateinit var expenseAdapter: ExpenseAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentWeatherStatus = "☀️ 맑음" 
    
    // 💡 화면 갱신 때마다 예산 초과 토스트가 무한반복으로 발생하는 현상을 막는 플래그 변수
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

        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "기타")
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

        binding.btnSetBudget.setOnClickListener {
            showBudgetSettingsDialog()
        }

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

    private fun updateBudgetStatus(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }
        val totalSpent = monthlyExpenses.sumOf { it.amount }
        
        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "기타")
        val totalBudget = categories.sumOf { viewModel.getBudgetLimit(it) }
        
        val remaining = totalBudget - totalSpent
        val dec = DecimalFormat("#,###")
        
        binding.tvBudgetStatus.text = "이번 달 예산: ${dec.format(totalBudget)}원 / 잔액: ${dec.format(remaining)}원"
        
        if (remaining < 0) {
            binding.tvBudgetStatus.setTextColor(Color.RED)
            
            val exceededAmount = java.lang.Math.abs(remaining)
            
            if (!isBudgetExceededNotified) {
                Toast.makeText(
                    this, 
                    "🚨 예산을 ${dec.format(exceededAmount)}원 초과했습니다! 지출에 주의하세요.", 
                    Toast.LENGTH_LONG
                ).show()
                isBudgetExceededNotified = true 
            }
        } else {
            binding.tvBudgetStatus.setTextColor(Color.BLACK)
            isBudgetExceededNotified = false 
        }
    }

    private fun switchTab(tabIndex: Int) {
        binding.containerDashboard.visibility = View.GONE
        binding.containerAdd.visibility = View.GONE
        binding.containerHistory.visibility = View.GONE

        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)
        val activeColor = ContextCompat.getColor(this, R.color.primary)

        binding.ivTabDashboard.setColorFilter(inactiveColor)
        binding.tvTabDashboard.setTextColor(inactiveColor)
        binding.ivTabHistory.setColorFilter(inactiveColor)
        binding.tvTabHistory.setTextColor(inactiveColor)

        when (tabIndex) {
            0 -> {
                binding.containerDashboard.visibility = View.VISIBLE
                binding.ivTabDashboard.setColorFilter(activeColor)
                binding.tvTabDashboard.setTextColor(activeColor)
            }
            1 -> {
                binding.containerAdd.visibility = View.VISIBLE
            }
            2 -> {
                binding.containerHistory.visibility = View.VISIBLE
                binding.ivTabHistory.setColorFilter(activeColor)
                binding.tvTabHistory.setTextColor(activeColor)
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            loadCurrentWeather()
        } else {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadCurrentWeather() {
        binding.tvWeatherStatus.text = "📍 현재 위치 확인 중..."
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    val weatherResp = viewModel.fetchWeather(location.latitude, location.longitude)
                    if (weatherResp != null && weatherResp.weatherList.isNotEmpty()) {
                        val mainWeather = weatherResp.weatherList[0].main
                        val desc = weatherResp.weatherList[0].description
                        val temp = weatherResp.mainInfo.temp
                        
                        val emoji = when (mainWeather.lowercase()) {
                            "clear" -> "☀️"
                            "clouds" -> "☁️"
                            "rain", "drizzle" -> "🌧️"
                            "thunderstorm" -> "⛈️"
                            "snow" -> "❄️"
                            else -> "🌫️"
                        }
                        
                        currentWeatherStatus = "$emoji $desc (${temp}℃)"
                        binding.tvWeatherStatus.text = "📍 날씨: $currentWeatherStatus"
                    } else {
                        binding.tvWeatherStatus.text = "📍 날씨 데이터 연동 실패 (기본값 설정)"
                    }
                }
            } else {
                binding.tvWeatherStatus.text = "📍 최근 위치 획득 실패 (기본값 설정)"
            }
        }
    }

    private fun updateTotalExpenseText(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }
        val totalSum = monthlyExpenses.sumOf { it.amount }
        val dec = DecimalFormat("#,###")
        binding.tvTotalExpense.text = "${dec.format(totalSum)}원"
    }

    private fun updateCharts(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }

        val markerPopup = CustomMarkerView(this, R.layout.custom_marker_view)

        val categoryGroups = monthlyExpenses.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val pieEntries = ArrayList<PieEntry>()
        categoryGroups.forEach { (cat, sum) ->
            pieEntries.add(PieEntry(sum.toFloat(), cat))
        }

        // 🎨 [안전성 보완 수정구간] 데이터 개수에 상관없이 카테고리별 고유 색상을 명확하게 동기화합니다.
        val pieDataSet = PieDataSet(pieEntries, "").apply {
            val colorList = ArrayList<Int>()
            
            for (entry in pieEntries) {
                val color = when (entry.label) {
                    "식비" -> ContextCompat.getColor(this@MainActivity, R.color.cat_food)        // 파스텔 핑크
                    "교통비" -> ContextCompat.getColor(this@MainActivity, R.color.cat_transport) // 파스텔 블루
                    "쇼핑" -> ContextCompat.getColor(this@MainActivity, R.color.cat_shopping)   // 파스텔 옐로우
                    "문화" -> ContextCompat.getColor(this@MainActivity, R.color.cat_culture)    // 파스텔 퍼플
                    else -> ContextCompat.getColor(this@MainActivity, R.color.cat_etc)           // 파스텔 민트 (기타 등)
                }
                colorList.add(color)
            }
            
            colors = colorList
            valueTextSize = 13f
            valueTextColor = ContextCompat.getColor(this@MainActivity, R.color.text_primary)
        }

        binding.pieChart.apply {
            data = PieData(pieDataSet)
            description.isEnabled = false
            centerText = "이번 달 소비"
            setCenterTextSize(14f)
            marker = markerPopup
            animateY(800)
            legend.isEnabled = true
            invalidate()
        }

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

        val barDataSet = BarDataSet(barEntries, "지출액 (원)").apply {
            color = ContextCompat.getColor(this@MainActivity, R.color.cat_culture)
            valueTextSize = 10f
            valueTextColor = ContextCompat.getColor(this@MainActivity, R.color.text_primary)
        }

        binding.barChart.apply {
            data = BarData(barDataSet)
            description.isEnabled = false
            xAxis.valueFormatter = IndexAxisValueFormatter(dateList)
            xAxis.granularity = 1f
            xAxis.isGranularityEnabled = true
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            axisRight.isEnabled = false
            marker = markerPopup
            animateY(800)
            invalidate()
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

        dialogBinding.btnEditCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        dialogBinding.btnEditSave.setOnClickListener {
            val title = dialogBinding.etEditTitle.text.toString().trim()
            val amountStr = dialogBinding.etEditAmount.text.toString().trim()
            val category = dialogBinding.spinnerEditCategory.selectedItem.toString()

            if (title.isNotEmpty() && amountStr.isNotEmpty()) {
                val amount = amountStr.toIntOrNull() ?: 0
                val updatedExpense = expense.copy(
                    title = title,
                    amount = amount,
                    category = category
                )
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
            .setNegativeButton("취se", null)
            .show()
    }

    private fun showBudgetSettingsDialog() {
        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "기타")
        val dialogBinding = DialogEditExpenseBinding.inflate(layoutInflater)
        
        dialogBinding.spinnerEditCategory.visibility = View.VISIBLE
        dialogBinding.etEditTitle.visibility = View.GONE 
        dialogBinding.etEditAmount.hint = "한 달 예산 금액(원)"
        
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.spinnerEditCategory.adapter = spinnerAdapter
        
        val currentLimit = viewModel.getBudgetLimit(categories[0])
        dialogBinding.etEditAmount.setText(currentLimit.toString())
        
        val builder = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            
        val alertDialog = builder.create()
        
        dialogBinding.btnEditCancel.setOnClickListener {
            alertDialog.dismiss()
        }
        
        dialogBinding.btnEditSave.setOnClickListener {
            val limitStr = dialogBinding.etEditAmount.text.toString().trim()
            val category = dialogBinding.spinnerEditCategory.selectedItem.toString()
            
            if (limitStr.isNotEmpty()) {
                val limit = limitStr.toIntOrNull() ?: 0
                viewModel.setBudgetLimit(category, limit)
                alertDialog.dismiss()
                Toast.makeText(this, "[$category] 예산 한도가 ${limit}원으로 설정되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "예산을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }
        
        alertDialog.show()
    }
}