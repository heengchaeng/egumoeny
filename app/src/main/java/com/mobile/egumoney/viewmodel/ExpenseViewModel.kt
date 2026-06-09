package com.mobile.egumoney.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.mobile.egumoney.data.AppDatabase
import com.mobile.egumoney.data.ExpenseEntity
import com.mobile.egumoney.data.ExpenseRepository
import com.mobile.egumoney.model.WeatherResponse
import com.mobile.egumoney.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository
    val allExpenses: LiveData<List<ExpenseEntity>>
    
    // [추가] 예산 저장을 위한 SharedPreferences
    private val prefs = application.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)

    private val notificationHelper = NotificationHelper(application)
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> get() = _isLoading
    private val _aiFeedback = MutableLiveData<String>()
    val aiFeedback: LiveData<String> get() = _aiFeedback

    init {
        val expenseDao = AppDatabase.getDatabase(application).expenseDao()
        repository = ExpenseRepository(expenseDao)
        allExpenses = repository.allExpenses.asLiveData()
    }

    // --- [예산 관리 기능] ---

    // 총 예산 저장/불러오기
    fun setTotalBudget(amount: Int) {
        prefs.edit().putInt("total_budget", amount).apply()
    }

    fun getTotalBudget(): Int {
        return prefs.getInt("total_budget", 0) // 기본값 0
    }

    // 카테고리별 예산 저장/불러오기
    fun setBudgetLimit(category: String, limit: Int) {
        prefs.edit().putInt("budget_${category.trim()}", limit).apply()
    }

    fun getBudgetLimit(category: String): Int {
        return prefs.getInt("budget_${category.trim()}", 100000) // 기본값 100,000
    }

    // -----------------------

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResponse? {
        return withContext(Dispatchers.IO) { repository.fetchWeather(lat, lon) }
    }

    fun addExpenseFromNaturalLanguage(sentence: String, weatherStatus: String) {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val parsedExpense = repository.parseExpense(sentence, weatherStatus)
            if (parsedExpense != null) {
                repository.insert(parsedExpense)
                checkBudgetLimit(parsedExpense.category)
                generateAiFeedback()
            }
            withContext(Dispatchers.Main) { _isLoading.value = false }
        }
    }

    fun insertExpense(expense: ExpenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(expense)
            checkBudgetLimit(expense.category)
            generateAiFeedback()
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.update(expense)
            checkBudgetLimit(expense.category)
            generateAiFeedback()
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(expense)
            generateAiFeedback()
        }
    }

    private suspend fun checkBudgetLimit(category: String) {
        val expenses = allExpenses.value ?: repository.allExpenses.first()
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        
        // 해당 카테고리 이번 달 총 지출
        val categoryExpenses = expenses.filter {
            it.category.trim() == category.trim() && it.date.startsWith(currentMonthStr)
        }
        val totalSpent = categoryExpenses.sumOf { it.amount }
        val limit = getBudgetLimit(category)

        if (totalSpent > limit) {
            // 이번 지출 항목이 추가되기 전의 합계 계산
            val lastExpenseAmount = categoryExpenses.maxByOrNull { it.id }?.amount ?: 0
            val previousSpent = totalSpent - lastExpenseAmount
            
            // "방금 전까지는 예산 안이었는데, 이번 지출로 처음 넘었을 때"만 알림
            if (previousSpent <= limit) {
                withContext(Dispatchers.Main) {
                    notificationHelper.sendBudgetAlert(category, totalSpent, limit)
                }
            }
        }
    }

    fun generateAiFeedback() {
        viewModelScope.launch(Dispatchers.IO) {
            val expenses = allExpenses.value ?: repository.allExpenses.first()
            if (expenses.isEmpty()) {
                withContext(Dispatchers.Main) { _aiFeedback.value = "지출 내역을 기록하시면 뱅크샐러드보다 날카롭게 분석해 드릴게요!" }
                return@launch
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            val calendar = Calendar.getInstance()
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
            val daysLeft = daysInMonth - currentDay + 1

            // 이번 달 지출 (오늘까지)
            val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }
            val totalSum = monthlyExpenses.sumOf { it.amount }

            // 저번 달 이맘때(오늘 날짜까지) 지출 계산
            val lastMonthCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
            val lastMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(lastMonthCal.time)
            val lastMonthExpensesUntilToday = expenses.filter { 
                it.date.startsWith(lastMonthStr) && 
                it.date.substringAfterLast("-").toInt() <= currentDay 
            }
            val lastMonthTotalUntilToday = lastMonthExpensesUntilToday.sumOf { it.amount }
            
            // 뱅크샐러드 스타일 추가 지표 계산
            val totalBudget = getTotalBudget()
            val remainingBudget = if (totalBudget > 0) totalBudget - totalSum else 0
            val dailyRecommended = if (daysLeft > 0) remainingBudget / daysLeft else 0
            
            val catGroup = monthlyExpenses.groupBy { it.category.trim() }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
            
            val mostSpentCategory = catGroup.maxByOrNull { it.value }?.key ?: "없음"
            
            // 무지출 일수 계산
            val spentDays = monthlyExpenses.map { it.date }.distinct().size
            val noSpendDays = currentDay - spentDays

            val dec = DecimalFormat("#,###")
            val summaryText = StringBuilder().apply {
                append("[지출 요약]\n")
                append("- 이번 달 현재까지 총 지출: ${dec.format(totalSum)}원\n")
                append("- 지난 달 이맘때까지 지출: ${dec.format(lastMonthTotalUntilToday)}원\n")
                append("- 남은 예산: ${dec.format(remainingBudget)}원\n")
                append("- 오늘부터 하루 권장 지출: ${dec.format(dailyRecommended)}원\n")
                append("- 가장 많이 쓴 카테고리: $mostSpentCategory\n")
                append("- 이번 달 무지출 일수: ${noSpendDays}일\n")
                append("\n[카테고리별 상세]\n")
                catGroup.forEach { (cat, sum) -> append("- $cat: ${dec.format(sum)}원\n") }
                append("\n[날씨별 지출]\n")
                val weatherGroup = monthlyExpenses.groupBy { it.weather }
                    .mapValues { it.value.sumOf { v -> v.amount } }
                weatherGroup.forEach { (weather, sum) -> append("- $weather: ${dec.format(sum)}원\n") }
            }.toString()

            val feedback = repository.getAiFeedback(summaryText)
            withContext(Dispatchers.Main) { _aiFeedback.value = feedback }
        }
    }
}