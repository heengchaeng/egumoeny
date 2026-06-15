package com.mobile.egumoney.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import androidx.room.Room
import com.mobile.egumoney.data.*
import com.mobile.egumoney.model.WeatherResponse
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository
    val allExpenses: LiveData<List<ExpenseEntity>>
    
    private val _aiFeedback = MutableLiveData<String>()
    val aiFeedback: LiveData<String> = _aiFeedback

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val sharedPrefs = application.getSharedPreferences("budget_prefs", Context.MODE_PRIVATE)

    init {
        val database = Room.databaseBuilder(
            application,
            AppDatabase::class.java, "expense_db"
        ).fallbackToDestructiveMigration().build()
        
        repository = ExpenseRepository(database.expenseDao())
        allExpenses = repository.allExpenses.asLiveData()
    }

    fun insertExpense(expense: ExpenseEntity) = viewModelScope.launch {
        repository.insert(expense)
        generateAiFeedback()
    }

    fun updateExpense(expense: ExpenseEntity) = viewModelScope.launch {
        repository.update(expense)
        generateAiFeedback()
    }

    fun deleteExpense(expense: ExpenseEntity) = viewModelScope.launch {
        repository.delete(expense)
        generateAiFeedback()
    }

    fun addExpenseFromNaturalLanguage(sentence: String, weather: String) = viewModelScope.launch {
        _isLoading.value = true
        try {
            val parsed = repository.parseExpense(sentence, weather)
            repository.insert(parsed)
            generateAiFeedback()
        } finally {
            _isLoading.value = false
        }
    }

    fun generateAiFeedback() = viewModelScope.launch {
        val expenses = allExpenses.value ?: emptyList()
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonth) }
        
        val totalSpent = monthlyExpenses.filter { it.category.trim() != "수입" }.sumOf { it.amount }
        val totalIncome = monthlyExpenses.filter { it.category.trim() == "수입" }.sumOf { it.amount }
        
        // 지출만 필터링
        val actualExpenses = monthlyExpenses.filter { it.category.trim() != "수입" }
        
        // 1. 카테고리별 요약
        val categorySummary = actualExpenses
            .groupBy { it.category.trim() }
            .map { "${it.key}: ${it.value.sumOf { e -> e.amount }}원" }
            .joinToString(", ")
            
        // 2. 가장 큰 지출 항목 찾기
        val maxExpense = actualExpenses.maxByOrNull { it.amount }
        val maxExpenseText = if (maxExpense != null) "가장 큰 지출은 '${maxExpense.title}'에 ${maxExpense.amount}원이야." else ""

        // 3. 최근 지출 5개 내역
        val recentHistory = actualExpenses.takeLast(5).reversed()
            .joinToString(", ") { "${it.title}(${it.amount}원)" }

        val summary = """
            - 사용자 닉네임: ${getUserNickname()}
            - 이번 달 총 수입: ${totalIncome}원
            - 이번 달 총 지출: ${totalSpent}원 (예산: ${getTotalBudget()}원)
            - 카테고리별: [$categorySummary]
            - $maxExpenseText
            - 최근 지출 내역: [$recentHistory]
            
            이 내역들을 보고 사용자의 소비 습관을 분석해서 따뜻한 응원과 현실적인 조언을 섞어서 말해줘.
        """.trimIndent()

        _aiFeedback.value = repository.getAiFeedback(summary)
    }

    fun setTotalBudget(amount: Int) {
        sharedPrefs.edit().putInt("total_budget", amount).apply()
    }

    fun getTotalBudget(): Int = sharedPrefs.getInt("total_budget", 0)

    fun setBudgetLimit(category: String, amount: Int) {
        sharedPrefs.edit().putInt("budget_$category", amount).apply()
    }

    fun getBudgetLimit(category: String): Int = sharedPrefs.getInt("budget_$category", 0)

    fun getUserNickname(): String {
        var nickname = sharedPrefs.getString("user_nickname", null)
        if (nickname == null) {
            val adjectives = listOf("행복한", "절약하는", "똑똑한", "신난", "차분한", "열정적인", "유능한", "스마트한")
            val animals = listOf("하마 🦛", "펭귄 🐧", "강아지 🐶", "고양이 🐱", "코알라 🐨", "햄스터 🐹", "쿼카 🐾", "토끼 🐰")
            nickname = "${adjectives.random()} ${animals.random()}"
            sharedPrefs.edit().putString("user_nickname", nickname).apply()
        }
        return nickname
    }

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResponse? {
        return repository.fetchWeather(lat, lon)
    }
}
