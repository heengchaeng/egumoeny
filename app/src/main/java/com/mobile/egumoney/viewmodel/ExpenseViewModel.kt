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
        val monthlyTotal = expenses.filter { it.date.startsWith(currentMonth) }.sumOf { it.amount }
        
        val summary = "이번 달 총 지출은 ${monthlyTotal}원입니다. 예산은 ${getTotalBudget()}원입니다."
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

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResponse? {
        return repository.fetchWeather(lat, lon)
    }
}
