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
import java.text.SimpleDateFormat
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
        val categorySum = expenses.filter {
            it.category.trim() == category.trim() && it.date.startsWith(currentMonthStr)
        }.sumOf { it.amount }

        val limit = getBudgetLimit(category) // 새로 만든 메서드 사용
        if (categorySum > limit) {
            withContext(Dispatchers.Main) {
                notificationHelper.sendBudgetAlert(category, categorySum, limit)
            }
        }
    }

    fun generateAiFeedback() {
        viewModelScope.launch(Dispatchers.IO) {
            val expenses = allExpenses.value ?: repository.allExpenses.first()
            if (expenses.isEmpty()) {
                withContext(Dispatchers.Main) { _aiFeedback.value = "지출 내역을 기록하시면 소비 패턴을 분석해 드릴게요!" }
                return@launch
            }

            val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }

            val totalSum = monthlyExpenses.sumOf { it.amount }
            val catGroup = monthlyExpenses.groupBy { it.category.trim() }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            val weatherGroup = monthlyExpenses.groupBy { it.weather }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            val summaryText = StringBuilder().apply {
                append("이번 달 총 지출: ${totalSum}원\n")
                append("카테고리별 지출:\n")
                catGroup.forEach { (cat, sum) -> append("- $cat: ${sum}원\n") }
                append("날씨별 지출 현황:\n")
                weatherGroup.forEach { (weather, sum) -> append("- $weather: ${sum}원\n") }
            }.toString()

            val feedback = repository.getAiFeedback(summaryText)
            withContext(Dispatchers.Main) { _aiFeedback.value = feedback }
        }
    }
}