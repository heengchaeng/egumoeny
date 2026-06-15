package com.mobile.egumoney.ui

import android.Manifest
import android.annotation.SuppressLint
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.mobile.egumoney.R
import com.mobile.egumoney.data.ExpenseEntity
import com.mobile.egumoney.databinding.ActivityMainBinding
import com.mobile.egumoney.databinding.DialogEditExpenseBinding
import com.mobile.egumoney.util.NotificationHelper
import com.mobile.egumoney.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ExpenseViewModel by viewModels()
    private lateinit var expenseAdapter: ExpenseAdapter
    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var weeklyCalendarAdapter: WeeklyCalendarAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationHelper: NotificationHelper

    private var currentCalendarDate = Calendar.getInstance()

    private var currentWeatherStatus = "☀️ 맑음"
    private var isBudgetExceededNotified = false
    private val moneyFormatter = DecimalFormat("#,###")
    private var isIncomeMode = false

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
        binding.tvHomeGreeting.text = "반가워요! ${viewModel.getUserNickname()}님!"
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
        setupAmountTextWatcher()
        observeViewModel()
        checkPermissions()

        val nickname = viewModel.getUserNickname()
        binding.tvHomeGreeting.text = "반가워요! $nickname 님!"

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

        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "수입", "기타")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        binding.spinnerManualCategory.adapter = spinnerAdapter

        calendarAdapter = CalendarAdapter()
        binding.rvCalendar.adapter = calendarAdapter

        weeklyCalendarAdapter = WeeklyCalendarAdapter()
        binding.rvWeeklyCalendar.adapter = weeklyCalendarAdapter

        binding.btnPrevMonth.setOnClickListener {
            currentCalendarDate.add(Calendar.MONTH, -1)
            updateCalendar(viewModel.allExpenses.value ?: emptyList())
        }
        binding.btnNextMonth.setOnClickListener {
            currentCalendarDate.add(Calendar.MONTH, 1)
            updateCalendar(viewModel.allExpenses.value ?: emptyList())
        }
    }

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
                switchTab(4)
            } else {
                Toast.makeText(this, "내역을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnManualRegister.setOnClickListener {
            val title = binding.etManualTitle.text.toString().trim()
            val rawAmountStr = binding.etManualAmount.text.toString().trim().replace(",", "")
            val category = binding.spinnerManualCategory.selectedItem?.toString()?.trim() ?: "기타"

            if (title.isNotEmpty() && rawAmountStr.isNotEmpty()) {
                val expense = ExpenseEntity(
                    date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
                    title = title,
                    amount = rawAmountStr.toIntOrNull() ?: 0,
                    category = category,
                    weather = currentWeatherStatus
                )
                viewModel.insertExpense(expense)
                binding.etManualTitle.setText("")
                binding.etManualAmount.setText("")
                Toast.makeText(this, "✅ 저장 완료!", Toast.LENGTH_SHORT).show()
                switchTab(4)
            }
        }

        binding.btnSetBudget.setOnClickListener { showBudgetSettingsDialog() }
        
        binding.tabHome.setOnClickListener { switchTab(0) }
        binding.tabDashboard.setOnClickListener { switchTab(1) }
        binding.tabAdd.setOnClickListener { switchTab(2) }
        binding.tabCalendar.setOnClickListener { switchTab(3) }
        binding.tabHistory.setOnClickListener { switchTab(4) }

        binding.flTabAddBg.setOnClickListener {
            toggleAddMode()
        }
    }

    private fun toggleAddMode() {
        isIncomeMode = !isIncomeMode
        updateAddTabUI()
        if (binding.containerAdd.visibility == View.VISIBLE) {
            val modeText = if (isIncomeMode) "수입 모드" else "지출 모드"
            Toast.makeText(this, "${modeText}로 전환되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            switchTab(2)
        }
    }

    private fun updateAddTabUI() {
        if (isIncomeMode) {
            binding.flTabAddBg.setBackgroundResource(R.drawable.bg_circle_blue)
            binding.ivTabAdd.setImageResource(android.R.drawable.ic_input_add)
            binding.btnRegister.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.income))
            binding.etExpenseInput.hint = "수입 내역을 입력하세요 (예: 월급 300만원)"
            binding.spinnerManualCategory.setSelection(5) // '수입' category index
        } else {
            binding.flTabAddBg.setBackgroundResource(R.drawable.bg_circle_black)
            binding.ivTabAdd.setImageResource(android.R.drawable.ic_input_add)
            binding.btnRegister.backgroundTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
            binding.etExpenseInput.hint = getString(R.string.input_hint)
            binding.spinnerManualCategory.setSelection(0) // Default '식비' or other
        }
    }

    private fun observeViewModel() {
        viewModel.allExpenses.observe(this) { expenses ->
            expenseAdapter.submitList(expenses)
            updateCharts(expenses)
            updateBudgetStatus(expenses)
            updateCalendar(expenses)
            updateAssetInfo(expenses)
            viewModel.updateBudgetStatus(expenses, this, notificationHelper)
        }
        viewModel.aiGreeting.observe(this) { 
            binding.tvAiSummary.text = it 
        }
        viewModel.aiReport.observe(this) {
            binding.tvAiFeedback.text = it
        }
        viewModel.isAiLoading.observe(this) { isLoading ->
            binding.pbAiSummary.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.pbAiFeedback.visibility = if (isLoading) View.VISIBLE else View.GONE
            
            // 자연어 입력 시에만 전체 화면 로딩을 사용하거나, 
            // 아예 제거하고 카드 내 로딩만 보여줄 수도 있습니다.
            // 여기서는 카드 내 로딩으로 대체하므로 전체 로딩은 숨깁니다.
            binding.layoutLoading.visibility = View.GONE
        }
    }

    private fun updateBudgetStatus(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }
        val totalSpent = monthlyExpenses.filter { it.category.trim() != "수입" }.sumOf { it.amount }
        
        val totalBudget = viewModel.getTotalBudget()
        val remaining = totalBudget - totalSpent

        // 🎯 예산 초과 시에도 실제 퍼센트가 표시되도록 계산 (100% 한도 없음)
        val percent = if (totalBudget > 0) {
            (totalSpent.toDouble() / totalBudget.toDouble() * 100).toInt()
        } else if (totalSpent > 0) {
            // 예산이 0원인데 지출이 있는 경우 100% 이상임을 표시
            100
        } else {
            0
        }
        
        binding.pbBudget.progress = percent.coerceIn(0, 100)
        binding.tvBudgetPercentage.text = if (totalBudget == 0 && totalSpent > 0) "초과!" else "$percent%" 
        
        if (percent >= 100) {
            binding.tvBudgetPercentage.setTextColor(ContextCompat.getColor(this, R.color.expense))
            binding.tvBudgetPercentage.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.pbBudget.progressTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.expense))
        } else {
            binding.tvBudgetPercentage.setTextColor(ContextCompat.getColor(this, R.color.primary))
            binding.tvBudgetPercentage.setTypeface(null, android.graphics.Typeface.NORMAL)
            binding.pbBudget.progressTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
        }

        binding.tvRemainingBudget.text = "${moneyFormatter.format(remaining)}원"
        
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        val lastDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val remainingDays = (lastDay - today + 1).coerceAtLeast(1)
        val dailyAllowance = if (remaining > 0) remaining / remainingDays else 0
        binding.tvDailyAllowance.text = "${moneyFormatter.format(dailyAllowance)}원"

        binding.layoutCategoryStatus.removeAllViews()
        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "수입", "기타")
        val emojis = mapOf("식비" to "🍴", "교통비" to "🚌", "쇼핑" to "🛍️", "문화" to "🎬", "투자" to "📈", "수입" to "💰", "기타" to "🏷️")

        categories.filter { it != "수입" }.forEach { cat ->
            val catLimit = viewModel.getBudgetLimit(cat)
            val catSpent = monthlyExpenses.filter { it.category.trim() == cat.trim() }.sumOf { it.amount }
            
            if (catLimit > 0 || catSpent > 0) {
                val catRemaining = catLimit - catSpent
                val itemView = layoutInflater.inflate(R.layout.item_category_status, binding.layoutCategoryStatus, false)
                
                val tvName = itemView.findViewById<TextView>(R.id.tv_cat_name)
                val tvPercent = itemView.findViewById<TextView>(R.id.tv_cat_percent)
                val tvAmount = itemView.findViewById<TextView>(R.id.tv_cat_amount)
                val pbBudget = itemView.findViewById<android.widget.ProgressBar>(R.id.pb_cat_budget)
                
                val emoji = emojis[cat] ?: "•"
                tvName.text = "$emoji $cat"
                
                // 🎯 퍼센트 계산 로직: 100% 초과 시에도 실제 퍼센트 표시 (예: 233%)
                val progress = if (catLimit > 0) {
                    ((catSpent.toDouble() / catLimit.toDouble()) * 100.0).toInt()
                } else if (catSpent > 0) {
                    100
                } else {
                    0
                }
                
                tvPercent.text = "$progress%"
                
                if (progress >= 100) {
                    tvPercent.setTextColor(ContextCompat.getColor(this, R.color.expense))
                    tvPercent.setTypeface(null, android.graphics.Typeface.BOLD)
                    pbBudget.progressTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.expense))
                } else {
                    tvPercent.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
                    tvPercent.setTypeface(null, android.graphics.Typeface.NORMAL)
                    pbBudget.progressTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primary))
                }

                pbBudget.progress = progress.coerceIn(0, 100) 

                if (catRemaining < 0) {
                    tvAmount.text = "초과 ${moneyFormatter.format(kotlin.math.abs(catRemaining))}원 🚨"
                    tvAmount.setTextColor(ContextCompat.getColor(this, R.color.expense))
                } else {
                    tvAmount.text = "남음 ${moneyFormatter.format(catRemaining)}원"
                    tvAmount.setTextColor(ContextCompat.getColor(this, R.color.income))
                }
                
                binding.layoutCategoryStatus.addView(itemView)
            }
        }

        if (totalBudget > 0 && remaining < 0) {
            if (!isBudgetExceededNotified) {
                notificationHelper.showBudgetExceededNotification(
                    "🚨 예산 초과 알림",
                    "이번 달 총 예산을 ${moneyFormatter.format(kotlin.math.abs(remaining))}원 초과했습니다!"
                )
                Toast.makeText(this, "🚨 설정하신 총 예산을 초과했습니다!", Toast.LENGTH_LONG).show()
                isBudgetExceededNotified = true
            }
        } else {
            isBudgetExceededNotified = false
        }
    }

    private fun updateCharts(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) && it.category.trim() != "수입" }

        binding.pieChart.clear()
        binding.barChart.clear()

        val categoryGroups = monthlyExpenses.groupBy { it.category.trim() }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        if (categoryGroups.isEmpty()) {
            binding.pieChart.invalidate()
            binding.barChart.invalidate()
            return
        }

        val pieEntries = categoryGroups.map { PieEntry(it.value.toFloat(), it.key) }
        val pieDataSet = PieDataSet(pieEntries, "")
        
        pieDataSet.colors = pieEntries.map {
            val colorRes = when (it.label.trim()) {
                "식비" -> R.color.cat_food
                "교통비" -> R.color.cat_transport
                "쇼핑" -> R.color.cat_shopping
                "문화" -> R.color.cat_culture
                "투자" -> R.color.cat_investment
                "기타" -> R.color.cat_etc
                else -> R.color.text_secondary
            }
            ContextCompat.getColor(this, colorRes)
        }

        pieDataSet.valueTextSize = 11f
        pieDataSet.valueTextColor = Color.BLACK
        pieDataSet.sliceSpace = 2f
        
        val pieData = PieData(pieDataSet)
        binding.pieChart.data = pieData
        binding.pieChart.description.isEnabled = false
        binding.pieChart.legend.isEnabled = true
        binding.pieChart.legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        binding.pieChart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        binding.pieChart.setUsePercentValues(true)
        binding.pieChart.setEntryLabelColor(Color.BLACK)
        binding.pieChart.setHoleColor(Color.TRANSPARENT)
        binding.pieChart.animateY(1000)
        binding.pieChart.invalidate()

        // Bar Chart (주간 지출 추이 - 스택 바 차트로 카테고리 색상 적용)
        val displayCategories = listOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
        val categoryColors = displayCategories.map { cat ->
            val colorRes = when (cat) {
                "식비" -> R.color.cat_food
                "교통비" -> R.color.cat_transport
                "쇼핑" -> R.color.cat_shopping
                "문화" -> R.color.cat_culture
                "투자" -> R.color.cat_investment
                else -> R.color.cat_etc
            }
            ContextCompat.getColor(this, colorRes)
        }

        val barEntries = mutableListOf<BarEntry>()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        
        val dates = mutableListOf<String>()
        repeat(7) {
            dates.add(sdf.format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        val reversedDates = dates.reversed()

        reversedDates.forEachIndexed { index, dateStr ->
            val dayExpenses = monthlyExpenses.filter { it.date.startsWith(dateStr) }
            val values = FloatArray(displayCategories.size)
            displayCategories.forEachIndexed { catIndex, cat ->
                values[catIndex] = dayExpenses.filter { it.category.trim() == cat }.sumOf { it.amount }.toFloat()
            }
            barEntries.add(BarEntry(index.toFloat(), values))
        }

        val barDataSet = BarDataSet(barEntries, "카테고리별 지출")
        barDataSet.colors = categoryColors
        barDataSet.setDrawValues(false)
        
        val barData = BarData(barDataSet)
        binding.barChart.data = barData
        binding.barChart.description.isEnabled = false
        binding.barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        binding.barChart.xAxis.setDrawGridLines(false)
        binding.barChart.xAxis.granularity = 1f
        binding.barChart.xAxis.valueFormatter = object : ValueFormatter() {
            private val labelSdf = SimpleDateFormat("MM/dd", Locale.US)
            override fun getFormattedValue(value: Float): String {
                val idx = value.toInt()
                if (idx in 0 until 7) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, idx - 6)
                    return labelSdf.format(cal.time)
                }
                return ""
            }
        }

        binding.barChart.legend.isEnabled = false
        binding.barChart.animateY(1000)
        binding.barChart.invalidate()
    }

    private fun updateCalendar(expenses: List<ExpenseEntity>) {
        val calendar = currentCalendarDate.clone() as Calendar
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        
        val monthFormat = SimpleDateFormat("yyyy년 MM월", Locale.getDefault())
        binding.tvCalendarMonth.text = monthFormat.format(calendar.time)

        val monthBeginningCell = calendar.get(Calendar.DAY_OF_WEEK) - 1
        calendar.add(Calendar.DAY_OF_MONTH, -monthBeginningCell)

        val days = mutableListOf<Date?>()
        while (days.size < 42) {
            val dateInCurrentMonth = Calendar.getInstance().apply { time = calendar.time }.get(Calendar.MONTH) == currentCalendarDate.get(Calendar.MONTH)
            if (dateInCurrentMonth) {
                days.add(calendar.time)
            } else {
                days.add(null)
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            if (days.size >= monthBeginningCell + currentCalendarDate.getActualMaximum(Calendar.DAY_OF_MONTH) && days.size % 7 == 0) break
        }
        
        calendarAdapter.setData(days, expenses)
        updateWeeklySummary(expenses)
    }

    private fun updateWeeklySummary(expenses: List<ExpenseEntity>) {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH) + 1
        val weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH)
        binding.tvWeeklySummaryTitle.text = "${month}월 ${weekOfMonth}주차 요약"

        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        
        val weeklyDates = mutableListOf<Date>()
        val weekStart = calendar.time
        repeat(7) {
            weeklyDates.add(calendar.time)
            calendar.add(Calendar.DAY_OF_WEEK, 1)
        }
        
        calendar.time = weekStart
        val weekEndCalendar = Calendar.getInstance()
        weekEndCalendar.time = weekStart
        weekEndCalendar.add(Calendar.DAY_OF_WEEK, 6)
        weekEndCalendar.set(Calendar.HOUR_OF_DAY, 23)
        weekEndCalendar.set(Calendar.MINUTE, 59)
        weekEndCalendar.set(Calendar.SECOND, 59)
        val weekEnd = weekEndCalendar.time

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val weeklyExpenses = expenses.filter {
            try {
                val datePart = if (it.date.length >= 10) it.date.substring(0, 10) else it.date
                val expenseDate = sdf.parse(datePart)
                expenseDate != null && !expenseDate.before(weekStart) && !expenseDate.after(weekEnd)
            } catch (ignored: Exception) {
                false
            }
        }

        val income = weeklyExpenses.filter { it.category.trim() == "수입" }.sumOf { it.amount }
        val expense = weeklyExpenses.filter { it.category.trim() != "수입" }.sumOf { it.amount }

        binding.tvWeeklyIncome.text = "${moneyFormatter.format(income)}원"
        binding.tvWeeklyExpense.text = "${moneyFormatter.format(expense)}원"
        
        weeklyCalendarAdapter.setData(weeklyDates, expenses)
    }

    private fun updateAssetInfo(expenses: List<ExpenseEntity>) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }
        
        val monthlyIncome = monthlyExpenses.filter { it.category.trim() == "수입" }.sumOf { it.amount }
        val monthlyExpense = monthlyExpenses.filter { it.category.trim() != "수입" }.sumOf { it.amount }
        
        val totalIncome = expenses.filter { it.category.trim() == "수입" }.sumOf { it.amount }
        val totalExpense = expenses.filter { it.category.trim() != "수입" }.sumOf { it.amount }
        val currentAsset = totalIncome - totalExpense
        
        binding.tvTotalAsset.text = "${moneyFormatter.format(currentAsset)}원"
        binding.tvMonthlyIncome.text = "+${moneyFormatter.format(monthlyIncome)}원"
        binding.tvMonthlyExpense.text = "-${moneyFormatter.format(monthlyExpense)}원"

        binding.tvTotalAsset.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        binding.tvTotalAssetLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        binding.tvMonthlyIncome.setTextColor(ContextCompat.getColor(this, R.color.income))
        binding.tvMonthlyIncomeLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        binding.tvMonthlyExpense.setTextColor(ContextCompat.getColor(this, R.color.expense))
        binding.tvMonthlyExpenseLabel.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
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
        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "수입", "기타")
        dialogBinding.spinnerEditCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.etEditTitle.visibility = View.GONE
        
        dialogBinding.etEditAmount.hint = "카테고리별 예산(원)"
        
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

    private fun switchTab(tabIndex: Int) {
        binding.containerHome.visibility = if (tabIndex == 0) View.VISIBLE else View.GONE
        binding.containerAnalysis.visibility = if (tabIndex == 1) View.VISIBLE else View.GONE
        binding.containerAdd.visibility = if (tabIndex == 2) View.VISIBLE else View.GONE
        binding.containerCalendar.visibility = if (tabIndex == 3) View.VISIBLE else View.GONE
        binding.containerHistory.visibility = if (tabIndex == 4) View.VISIBLE else View.GONE

        if (tabIndex == 1) {
            viewModel.generateAiFeedback()
        }

        updateAddTabUI()

        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)
        val activeColor = ContextCompat.getColor(this, R.color.primary)

        val tabs = listOf(
            Pair(binding.ivTabHome, binding.tvTabHome),
            Pair(binding.ivTabDashboard, binding.tvTabDashboard),
            null, 
            Pair(binding.ivTabCalendar, binding.tvTabCalendar),
            Pair(binding.ivTabHistory, binding.tvTabHistory)
        )

        tabs.forEachIndexed { index, pair ->
            pair?.let { (iv, tv) ->
                val color = if (index == tabIndex) activeColor else inactiveColor
                iv.setColorFilter(color)
                tv.setTextColor(color)
                tv.setTypeface(null, if (index == tabIndex) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
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
        
        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
            if (lastLoc != null) {
                loadWeatherDirectly(lastLoc.latitude, lastLoc.longitude)
            }
            
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
        val categories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "수입", "기타")
        dialogBinding.spinnerEditCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.spinnerEditCategory.setSelection(categories.indexOf(expense.category.trim()).coerceAtLeast(0))
        dialogBinding.etEditTitle.setText(expense.title)
        dialogBinding.etEditAmount.setText(moneyFormatter.format(expense.amount))
        
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
