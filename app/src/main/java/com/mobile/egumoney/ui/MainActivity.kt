package com.mobile.egumoney.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.*
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
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
            binding.tvWeatherStatus.text = "📍 위치 권한 거부됨"
        }
        
        if (permissions[Manifest.permission.POST_NOTIFICATIONS] == false) {
            Toast.makeText(this, "알림 권한이 거부되어 예산 초과 알림을 받을 수 없습니다.", Toast.LENGTH_SHORT).show()
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
        checkPermissions()
        setupAmountTextWatcher() // 실시간 쉼표 추가

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

    // 금액 입력 시 실시간으로 쉼표를 찍어주는 함수 (커서 위치 유지 기능 추가)
    private fun setupAmountTextWatcher() {
        binding.etManualAmount.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    binding.etManualAmount.removeTextChangedListener(this)
                    val clean = s.toString().replace(",", "")
                    if (clean.isNotEmpty()) {
                        val formatted = DecimalFormat("#,###").format(clean.toDouble())
                        current = formatted
                        binding.etManualAmount.setText(formatted)
                        binding.etManualAmount.setSelection(formatted.length)
                    } else {
                        current = ""
                        binding.etManualAmount.setText("")
                    }
                    binding.etManualAmount.addTextChangedListener(this)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
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
            val amountStr = binding.etManualAmount.text.toString().trim().replace(",", "")
            val category = binding.spinnerManualCategory.selectedItem?.toString()?.trim() ?: "기타"

            if (title.isNotEmpty() && amountStr.isNotEmpty()) {
                val expense = ExpenseEntity(
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    title = title,
                    amount = amountStr.toIntOrNull() ?: 0,
                    category = category,
                    weather = currentWeatherStatus
                )
                viewModel.insertExpense(expense)
                binding.etManualTitle.setText("")
                binding.etManualAmount.setText("")
                Toast.makeText(this, "✅ 저장 완료!", Toast.LENGTH_SHORT).show()
                switchTab(2)
            }
        }

        // 맥북 키보드 포커스 문제 방지를 위해 클릭 시 강제 포커스 요청 추가
        binding.etManualTitle.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
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
        viewModel.aiFeedback.observe(this) { binding.tvAiFeedback.text = it }
    }

    private fun updateBudgetStatus(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }
        val totalSpent = monthlyExpenses.sumOf { it.amount }
        
        val totalBudget = viewModel.getTotalBudget()
        val remaining = totalBudget - totalSpent
        val dec = DecimalFormat("#,###")

        val spannableBuilder = SpannableStringBuilder()
        
        // 1. 총 예산 표시
        spannableBuilder.append("이번 달 총 예산: ${dec.format(totalBudget)}원\n")
        
        // 2. 총 남은 금액 표시 (초과 시 금액만 파스텔 레드, 남으면 파스텔 블루)
        spannableBuilder.append("총 남은 금액: ")
        val remainingAmountStart = spannableBuilder.length
        spannableBuilder.append("${dec.format(remaining)}원\n")
        
        val statusColor = if (remaining < 0) {
            ContextCompat.getColor(this, R.color.pastel_red)
        } else {
            ContextCompat.getColor(this, R.color.pastel_blue)
        }
        
        spannableBuilder.setSpan(
            ForegroundColorSpan(statusColor),
            remainingAmountStart,
            spannableBuilder.length - 1,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        
        spannableBuilder.append("\n[카테고리별 남은 예산]\n")

        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
        val categoryEmojis = mapOf(
            "식비" to "🍱",
            "교통비" to "🚌",
            "쇼핑" to "🛍️",
            "문화" to "🎬",
            "투자" to "📈",
            "기타" to "💾"
        )

        for (cat in categories) {
            val emoji = categoryEmojis[cat] ?: "💰"
            val catLimit = viewModel.getBudgetLimit(cat)
            val catSpent = monthlyExpenses.filter { it.category.trim() == cat }.sumOf { it.amount }
            val catRemaining = catLimit - catSpent
            
            spannableBuilder.append("$emoji $cat: ")
            val catAmountStart = spannableBuilder.length
            
            if (catRemaining < 0) {
                spannableBuilder.append("초과 ${dec.format(Math.abs(catRemaining))}원 🚨\n")
                spannableBuilder.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, R.color.pastel_red)),
                    catAmountStart,
                    spannableBuilder.length - 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                spannableBuilder.append("남음 ${dec.format(catRemaining)}원\n")
                spannableBuilder.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(this, R.color.pastel_blue)),
                    catAmountStart,
                    spannableBuilder.length - 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        binding.tvBudgetStatus.text = spannableBuilder
        binding.tvBudgetStatus.setTextColor(Color.BLACK) // 기본은 블랙, 초과 항목만 Span으로 레드
        
        if (totalBudget > 0 && remaining < 0) {
            if (!isBudgetExceededNotified) {
                Toast.makeText(this, "🚨 총 예산을 초과했습니다!", Toast.LENGTH_LONG).show()
                isBudgetExceededNotified = true
            }
        } else {
            isBudgetExceededNotified = false
        }
    }

    // 🚨 핵심 수정: 차트 색상을 고정 컬러 코드로 다양하게 분배했습니다.
    private fun updateCharts(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }

        binding.pieChart.clear()
        binding.barChart.clear()

        val categoryGroups = monthlyExpenses.groupBy { it.category.trim() }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        if (categoryGroups.isEmpty()) return

        val pieEntries = categoryGroups.map { PieEntry(it.value.toFloat(), it.key) }
        val pieDataSet = PieDataSet(pieEntries, "")
        
        // 🎨 리스트와 동일한 리소스 색상을 차트에도 적용
        pieDataSet.colors = pieEntries.map {
            when (it.label) {
                "식비" -> ContextCompat.getColor(this, R.color.cat_food)
                "교통비" -> ContextCompat.getColor(this, R.color.cat_transport)
                "쇼핑" -> ContextCompat.getColor(this, R.color.cat_shopping)
                "문화" -> ContextCompat.getColor(this, R.color.cat_culture)
                "투자" -> ContextCompat.getColor(this, R.color.cat_investment)
                else -> ContextCompat.getColor(this, R.color.cat_etc)
            }
        }
        pieDataSet.valueTextSize = 12f
        pieDataSet.valueTextColor = Color.BLACK
        
        binding.pieChart.data = PieData(pieDataSet)
        binding.pieChart.animateY(800)
        binding.pieChart.invalidate()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val barEntries = ArrayList<BarEntry>()
        val calendar = Calendar.getInstance()
        for (i in 0..6) {
            calendar.set(Calendar.DAY_OF_YEAR, Calendar.getInstance().get(Calendar.DAY_OF_YEAR) - (6 - i))
            val dateStr = sdf.format(calendar.time)
            barEntries.add(BarEntry(i.toFloat(), expenses.filter { it.date == dateStr }.sumOf { it.amount }.toFloat()))
        }
        val barDataSet = BarDataSet(barEntries, "일별 지출액")
        barDataSet.color = Color.parseColor("#A7CBD9")
        
        binding.barChart.data = BarData(barDataSet)
        binding.barChart.animateY(800)
        binding.barChart.invalidate()
    }

    private fun showBudgetSettingsDialog() {
        val options = arrayOf("총 예산 설정", "카테고리별 예산 분배")
        AlertDialog.Builder(this)
            .setTitle("예산 관리")
            .setItems(options) { _, which ->
                if (which == 0) showTotalBudgetDialog()
                else showCategoryBudgetDialog()
            }.show()
    }

    private fun showTotalBudgetDialog() {
        val dec = DecimalFormat("#,###")
        val input = EditText(this).apply { 
            inputType = InputType.TYPE_CLASS_NUMBER 
            setText(dec.format(viewModel.getTotalBudget()))
            setSelection(text.length)
        }
        
        // 총 예산 설정 창에도 쉼표 기능 추가
        input.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    input.removeTextChangedListener(this)
                    val clean = s.toString().replace(",", "")
                    if (clean.isNotEmpty()) {
                        val formatted = dec.format(clean.toDouble())
                        current = formatted
                        input.setText(formatted)
                        input.setSelection(formatted.length)
                    } else {
                        current = ""
                        input.setText("")
                    }
                    input.addTextChangedListener(this)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        AlertDialog.Builder(this)
            .setTitle("이번 달 총 예산(원)")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val total = input.text.toString().replace(",", "").toIntOrNull() ?: 0
                viewModel.setTotalBudget(total)
                Toast.makeText(this, "총 예산 ${dec.format(total)}원 설정 완료", Toast.LENGTH_SHORT).show()
                viewModel.allExpenses.value?.let { updateBudgetStatus(it) }
            }.setNegativeButton("취소", null).show()
    }

    private fun showCategoryBudgetDialog() {
        val dec = DecimalFormat("#,###")
        val dialogBinding = DialogEditExpenseBinding.inflate(layoutInflater)
        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
        dialogBinding.spinnerEditCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.etEditTitle.visibility = View.GONE
        dialogBinding.etEditAmount.hint = "카테고리별 예산(원)"

        // 다이얼로그 안의 입력창에도 쉼표 기능 및 커서 유지 추가
        dialogBinding.etEditAmount.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    dialogBinding.etEditAmount.removeTextChangedListener(this)
                    val clean = s.toString().replace(",", "")
                    if (clean.isNotEmpty()) {
                        val formatted = dec.format(clean.toDouble())
                        current = formatted
                        dialogBinding.etEditAmount.setText(formatted)
                        dialogBinding.etEditAmount.setSelection(formatted.length)
                    } else {
                        current = ""
                        dialogBinding.etEditAmount.setText("")
                    }
                    dialogBinding.etEditAmount.addTextChangedListener(this)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialogBinding.btnEditSave.setOnClickListener {
            val cat = dialogBinding.spinnerEditCategory.selectedItem.toString().trim()
            val amount = dialogBinding.etEditAmount.text.toString().replace(",", "").toIntOrNull() ?: 0
            viewModel.setBudgetLimit(cat, amount)
            dialog.dismiss()
            Toast.makeText(this, "[$cat] 예산 수정 완료", Toast.LENGTH_SHORT).show()
            viewModel.allExpenses.value?.let { updateBudgetStatus(it) }
        }
        dialog.show()
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

        if (tabIndex == 1) {
            // 💡 맥북 에뮬레이터 버그 해결: 200ms 지연 후 포커스 요청 (애니메이션 완료 대기)
            binding.etExpenseInput.postDelayed({
                binding.etExpenseInput.requestFocus()
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(binding.etExpenseInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                
                // 터치해도 키보드가 안 뜨는 경우를 위해 클릭 이벤트 강제 발생
                binding.etExpenseInput.dispatchTouchEvent(android.view.MotionEvent.obtain(android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_DOWN, 0f, 0f, 0))
                binding.etExpenseInput.dispatchTouchEvent(android.view.MotionEvent.obtain(android.os.SystemClock.uptimeMillis(), android.os.SystemClock.uptimeMillis(), android.view.MotionEvent.ACTION_UP, 0f, 0f, 0))
            }, 200)
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val needsRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needsRequest.isEmpty()) {
            loadCurrentWeather()
        } else {
            requestPermissionLauncher.launch(needsRequest.toTypedArray())
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadCurrentWeather() {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                CoroutineScope(Dispatchers.Main).launch {
                    val weatherResp = viewModel.fetchWeather(location.latitude, location.longitude)
                    if (weatherResp != null) {
                        currentWeatherStatus = weatherResp.weatherList[0].description
                        binding.tvWeatherStatus.text = "📍 날씨: $currentWeatherStatus"
                    }
                }
            }
        }
    }

    private fun showEditDialog(expense: ExpenseEntity) {
        val dec = DecimalFormat("#,###")
        val dialogBinding = DialogEditExpenseBinding.inflate(layoutInflater)
        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
        dialogBinding.spinnerEditCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.spinnerEditCategory.setSelection(categories.indexOf(expense.category.trim()).coerceAtLeast(0))
        dialogBinding.etEditTitle.setText(expense.title)
        dialogBinding.etEditAmount.setText(dec.format(expense.amount))

        // 수정 다이얼로그 금액 입력창에도 쉼표 및 커서 유지 적용
        dialogBinding.etEditAmount.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    dialogBinding.etEditAmount.removeTextChangedListener(this)
                    val clean = s.toString().replace(",", "")
                    if (clean.isNotEmpty()) {
                        val formatted = dec.format(clean.toDouble())
                        current = formatted
                        dialogBinding.etEditAmount.setText(formatted)
                        dialogBinding.etEditAmount.setSelection(formatted.length)
                    } else {
                        current = ""
                        dialogBinding.etEditAmount.setText("")
                    }
                    dialogBinding.etEditAmount.addTextChangedListener(this)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialogBinding.btnEditSave.setOnClickListener {
            val updated = expense.copy(
                title = dialogBinding.etEditTitle.text.toString().trim(),
                amount = dialogBinding.etEditAmount.text.toString().replace(",", "").toIntOrNull() ?: 0,
                category = dialogBinding.spinnerEditCategory.selectedItem.toString().trim()
            )
            viewModel.updateExpense(updated)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showDeleteConfirmDialog(expense: ExpenseEntity) {
        AlertDialog.Builder(this)
            .setTitle("삭제")
            .setMessage("'${expense.title}' 내역을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ -> viewModel.deleteExpense(expense) }
            .setNegativeButton("취소", null)
            .show()
    }
}