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
        val categorySummary = monthlyExpenses.filter { it.category.trim() != "수입" }
            .groupBy { it.category.trim() }
            .map { "${it.key}: ${it.value.sumOf { e -> e.amount }}원" }
            .joinToString(", ")
            
        val summary = "이번 달 총 예산은 ${getTotalBudget()}원인데, 현재까지 총 ${totalSpent}원 썼어. 카테고리별로는 [$categorySummary] 이렇게 썼네."
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
