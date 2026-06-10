package com.mobile.egumoney.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.data.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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
import com.mobile.egumoney.util.NotificationHelper
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ExpenseViewModel by viewModels()
    private lateinit var expenseAdapter: ExpenseAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationHelper: NotificationHelper

    private var currentWeatherStatus = "☀️ 맑음"
    private var isBudgetExceededNotified = false
    private val moneyFormatter = DecimalFormat("#,###")

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
            Toast.makeText(this, "알림 권한이 꺼져 있어 예산 경고를 볼 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationHelper = NotificationHelper(this)

        setupRecyclerView()
        setupListeners()
        setupAmountTextWatcher() // 쉼표 실시간 감지기 부착
        observeViewModel()
        checkPermissions()

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

    // 🚨 [신규] 수동 등록 금액창에 천 단위 실시간 쉼표 기능 구현
    private fun setupAmountTextWatcher() {
        binding.etManualAmount.addTextChangedListener(object : TextWatcher {
            private var currentStr = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != currentStr) {
                    binding.etManualAmount.removeTextChangedListener(this)
                    
                    val cleanString = s.toString().replace(",", "")
                    if (cleanString.isNotEmpty()) {
                        val parsed = cleanString.toDoubleOrNull() ?: 0.0
                        val formatted = moneyFormatter.format(parsed)
                        currentStr = formatted
                        binding.etManualAmount.setText(formatted)
                        binding.etManualAmount.setSelection(formatted.length)
                    } else {
                        currentStr = ""
                    }
                    
                    binding.etManualAmount.addTextChangedListener(this)
                }
            }
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
            // 🚨 [수정] 데이터 저장할 때는 쉼표(,)를 깨끗하게 지우고 순수 숫자로 파싱합니다.
            val rawAmountStr = binding.etManualAmount.text.toString().trim().replace(",", "")
            val category = binding.spinnerManualCategory.selectedItem?.toString()?.trim() ?: "기타"

            if (title.isNotEmpty() && rawAmountStr.isNotEmpty()) {
                val expense = ExpenseEntity(
                    date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    title = title,
                    amount = rawAmountStr.toIntOrNull() ?: 0,
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
        viewModel.isLoading.observe(this) { isLoading ->
            binding.layoutLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    // 🚨 [수정] 예산 현황 리스트 및 일일 권장 소비액 계산 추가
    private fun updateBudgetStatus(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }
        val totalSpent = monthlyExpenses.sumOf { it.amount }
        
        val totalBudget = viewModel.getTotalBudget()
        val remaining = totalBudget - totalSpent

        // 남은 일수 계산
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val remainingDays = (lastDay - today + 1).coerceAtLeast(1)
        val dailyAllowance = if (remaining > 0) remaining / remainingDays else 0

        val statusBuilder = android.text.SpannableStringBuilder()
        
        statusBuilder.append("💰 이번 달 총 예산 :   ${moneyFormatter.format(totalBudget)}원\n")
        statusBuilder.append("📉 총 남은 금액     :   ${moneyFormatter.format(remaining)}원\n")
        
        val allowanceStart = statusBuilder.length
        statusBuilder.append("📅 일일 권장 소비   :   ${moneyFormatter.format(dailyAllowance)}원 (남은 ${remainingDays}일)\n")
        statusBuilder.setSpan(
            android.text.style.ForegroundColorSpan(Color.parseColor("#4CAF50")),
            allowanceStart,
            statusBuilder.length,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        statusBuilder.append("\n━━━━━━━━━━━━━━━━━━━━━━\n")
        statusBuilder.append("📂 [카테고리별 예산 현황]\n\n")

        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
        val emojis = mapOf("식비" to "🍴", "교통비" to "🚌", "쇼핑" to "🛍️", "문화" to "🎬", "투자" to "📈", "기타" to "🏷️")

        for (cat in categories) {
            val catLimit = viewModel.getBudgetLimit(cat)
            val catSpent = monthlyExpenses.filter { it.category.trim() == cat.trim() }.sumOf { it.amount }
            val catRemaining = catLimit - catSpent
            
            val emoji = emojis[cat] ?: "•"
            val paddedCategory = cat.padEnd(4, ' ')
            
            statusBuilder.append("$emoji $paddedCategory :  ")
            
            val valueStart = statusBuilder.length
            if (catRemaining < 0) {
                statusBuilder.append("초과 ${moneyFormatter.format(Math.abs(catRemaining))}원 🚨\n")
                statusBuilder.setSpan(
                    android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, R.color.pastel_red)),
                    valueStart,
                    statusBuilder.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                statusBuilder.append("남음 ${moneyFormatter.format(catRemaining)}원\n")
                statusBuilder.setSpan(
                    android.text.style.ForegroundColorSpan(ContextCompat.getColor(this, R.color.pastel_blue)),
                    valueStart,
                    statusBuilder.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        binding.tvBudgetStatus.text = statusBuilder
        
        if (totalBudget > 0 && remaining < 0) {
            if (!isBudgetExceededNotified) {
                // 🚨 [신규] 푸시 알림 발송
                notificationHelper.showBudgetExceededNotification(
                    "🚨 예산 초과 알림",
                    "이번 달 총 예산을 ${moneyFormatter.format(Math.abs(remaining))}원 초과했습니다!"
                )
                Toast.makeText(this, "🚨 설정하신 총 예산을 초과했습니다!", Toast.LENGTH_LONG).show()
                isBudgetExceededNotified = true
            }
        } else {
            isBudgetExceededNotified = false
        }

        // 카테고리별 초과 알림 (개별 카테고리 알림도 추가하고 싶다면 여기서 구현 가능)
        for (cat in categories) {
            val catLimit = viewModel.getBudgetLimit(cat)
            val catSpent = monthlyExpenses.filter { it.category.trim() == cat.trim() }.sumOf { it.amount }
            if (catLimit > 0 && catSpent > catLimit) {
                // 카테고리별 알림은 너무 자주 올 수 있으므로 필요 시 추가 로직 필요
            }
        }
    }

    // 🚨 [수정] 원형 차트 색상 완전 다양화 구현법 (기타 고정 탈출)
    private fun updateCharts(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }

        binding.pieChart.clear()
        binding.barChart.clear()

        // 공백 트림 연동 강화로 정확한 카테고리 그룹화 보장
        val categoryGroups = monthlyExpenses.groupBy { it.category.trim() }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        if (categoryGroups.isEmpty()) return

        val pieEntries = categoryGroups.map { PieEntry(it.value.toFloat(), it.key) }
        val pieDataSet = PieDataSet(pieEntries, "")
        
        // 데이터가 나뉘는 즉시 다양한 색상 칩 부여
        pieDataSet.colors = pieEntries.map {
            val colorRes = when (it.label.trim()) {
                "식비" -> R.color.cat_food
                "교통비" -> R.color.cat_transport
                "쇼핑" -> R.color.cat_shopping
                "문화" -> R.color.cat_culture
                "투자" -> R.color.cat_investment
                else -> R.color.cat_etc
            }
            ContextCompat.getColor(this, colorRes)
        }
        pieDataSet.valueTextSize = 13f
        pieDataSet.valueTextColor = Color.DKGRAY
        pieDataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return moneyFormatter.format(value.toInt()) + "원"
            }
        }
        
        binding.pieChart.data = PieData(pieDataSet)
        binding.pieChart.description.isEnabled = false
        binding.pieChart.legend.isEnabled = false // 하단 범례 대신 리스트 가독성 중점
        binding.pieChart.animateXY(600, 600)
        binding.pieChart.invalidate()

        // 바 차트 일주일 연동
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val barEntries = ArrayList<BarEntry>()
        val calendar = Calendar.getInstance()
        for (i in 0..6) {
            calendar.set(Calendar.DAY_OF_YEAR, Calendar.getInstance().get(Calendar.DAY_OF_YEAR) - (6 - i))
            val dateStr = sdf.format(calendar.time)
            barEntries.add(BarEntry(i.toFloat(), expenses.filter { it.date == dateStr }.sumOf { it.amount }.toFloat()))
        }
        val barDataSet = BarDataSet(barEntries, "지출 추이")
        barDataSet.color = Color.parseColor("#A7CBD9")
        barDataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return moneyFormatter.format(value.toInt())
            }
        }
        binding.barChart.data = BarData(barDataSet)
        binding.barChart.description.isEnabled = false
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
        val input = EditText(this).apply { 
            inputType = InputType.TYPE_CLASS_NUMBER 
            setText(moneyFormatter.format(viewModel.getTotalBudget()))
        }
        
        // 입력 시 실시간 쉼표 적용
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val str = s.toString()
                if (str.isEmpty()) return
                input.removeTextChangedListener(this)
                val cleanString = str.replace(",", "")
                val formatted = moneyFormatter.format(cleanString.toDouble())
                input.setText(formatted)
                input.setSelection(formatted.length)
                input.addTextChangedListener(this)
            }
        })

        AlertDialog.Builder(this)
            .setTitle("이번 달 총 예산(원)")
            .setView(input)
            .setPositiveButton("저장") { _, _ ->
                val cleanAmount = input.text.toString().replace(",", "")
                val total = cleanAmount.toIntOrNull() ?: 0
                viewModel.setTotalBudget(total)
                Toast.makeText(this, "총 예산 설정 완료", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("취소", null).show()
    }

    private fun showCategoryBudgetDialog() {
        val dialogBinding = DialogEditExpenseBinding.inflate(layoutInflater)
        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
        dialogBinding.spinnerEditCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.etEditTitle.visibility = View.GONE
        
        dialogBinding.etEditAmount.hint = "카테고리별 예산(원)"
        
        // 입력 시 실시간 쉼표 적용
        dialogBinding.etEditAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val str = s.toString()
                if (str.isEmpty()) return
                dialogBinding.etEditAmount.removeTextChangedListener(this)
                val cleanString = str.replace(",", "")
                val parsed = cleanString.toDoubleOrNull() ?: 0.0
                val formatted = moneyFormatter.format(parsed)
                dialogBinding.etEditAmount.setText(formatted)
                dialogBinding.etEditAmount.setSelection(formatted.length)
                dialogBinding.etEditAmount.addTextChangedListener(this)
            }
        })

        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialogBinding.btnEditSave.setOnClickListener {
            val cat = dialogBinding.spinnerEditCategory.selectedItem.toString().trim()
            val cleanAmount = dialogBinding.etEditAmount.text.toString().replace(",", "").trim()
            val amount = cleanAmount.toIntOrNull() ?: 0
            viewModel.setBudgetLimit(cat, amount)
            dialog.dismiss()
            Toast.makeText(this, "[$cat] 예산 제한 적용 완료", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun updateTotalExpenseText(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }
        binding.tvTotalExpense.text = "${moneyFormatter.format(monthlyExpenses.sumOf { it.amount })}원"
    }

    private fun switchTab(tabIndex: Int) {
        binding.containerDashboard.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        binding.containerAdd.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        binding.containerHistory.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needsRequest = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needsRequest.isEmpty()) loadCurrentWeather() else requestPermissionLauncher.launch(needsRequest.toTypedArray())
    }

    @SuppressLint("MissingPermission")
    private fun loadCurrentWeather() {
        binding.tvWeatherStatus.text = "📍 위치 확인 중..."
        
        // 1. 마지막 위치 즉시 시도 (빠른 응답)
        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null) {
                loadWeatherDirectly(lastLoc.latitude, lastLoc.longitude)
            }
            
            // 2. 최신 위치 요청 (정확도 향상)
            val cts = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        loadWeatherDirectly(location.latitude, location.longitude)
                    } else if (lastLoc == null) {
                        binding.tvWeatherStatus.text = "📍 날씨: 위치 정보 없음"
                    }
                }
                .addOnFailureListener {
                    if (lastLoc == null) {
                        binding.tvWeatherStatus.text = "📍 날씨: 위치 획득 실패"
                    }
                }
        }
    }

    private fun loadWeatherDirectly(lat: Double, lon: Double) {
        lifecycleScope.launch {
            try {
                val weatherResp = viewModel.fetchWeather(lat, lon)
                if (weatherResp != null && weatherResp.weatherList.isNotEmpty()) {
                    val weatherDesc = weatherResp.weatherList[0].description
                    val temp = weatherResp.mainInfo.temp.toInt()
                    currentWeatherStatus = "$weatherDesc (${temp}℃)"
                    binding.tvWeatherStatus.text = "📍 날씨: $currentWeatherStatus"
                } else {
                    binding.tvWeatherStatus.text = "📍 날씨: 정보 로드 실패"
                    currentWeatherStatus = "날씨 정보 없음"
                }
            } catch (e: Exception) {
                binding.tvWeatherStatus.text = "📍 날씨: 로드 오류"
                currentWeatherStatus = "오류"
            }
        }
    }


    private fun showEditDialog(expense: ExpenseEntity) {
        val dialogBinding = DialogEditExpenseBinding.inflate(layoutInflater)
        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
        dialogBinding.spinnerEditCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.spinnerEditCategory.setSelection(categories.indexOf(expense.category.trim()).coerceAtLeast(0))
        dialogBinding.etEditTitle.setText(expense.title)
        dialogBinding.etEditAmount.setText(moneyFormatter.format(expense.amount))
        
        // 입력 시 실시간 쉼표 적용
        dialogBinding.etEditAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val str = s.toString()
                if (str.isEmpty()) return
                dialogBinding.etEditAmount.removeTextChangedListener(this)
                val cleanString = str.replace(",", "")
                val parsed = cleanString.toDoubleOrNull() ?: 0.0
                val formatted = moneyFormatter.format(parsed)
                dialogBinding.etEditAmount.setText(formatted)
                dialogBinding.etEditAmount.setSelection(formatted.length)
                dialogBinding.etEditAmount.addTextChangedListener(this)
            }
        })

        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()
        dialogBinding.btnEditSave.setOnClickListener {
            val cleanAmount = dialogBinding.etEditAmount.text.toString().replace(",", "").trim()
            val updated = expense.copy(
                title = dialogBinding.etEditTitle.text.toString().trim(),
                amount = cleanAmount.toIntOrNull() ?: 0,
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