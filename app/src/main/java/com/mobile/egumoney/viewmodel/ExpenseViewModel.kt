package com.mobile.egumoney.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import androidx.room.Room
import com.mobile.egumoney.data.*
import com.mobile.egumoney.model.WeatherResponse
import com.mobile.egumoney.util.NotificationHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository
    val allExpenses: LiveData<List<ExpenseEntity>>
    
    private val _aiGreeting = MutableLiveData<String>()
    val aiGreeting: LiveData<String> = _aiGreeting

    private val _aiReport = MutableLiveData<String>()
    val aiReport: LiveData<String> = _aiReport

    private val _isAiLoading = MutableLiveData<Boolean>(false)
    val isAiLoading: LiveData<Boolean> = _isAiLoading

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
        _isAiLoading.value = true
        try {
            val parsed = repository.parseExpense(sentence, weather)
            repository.insert(parsed)
            generateAiFeedback()
        } finally {
            _isAiLoading.value = false
        }
    }

    fun generateAiFeedback() = viewModelScope.launch {
        _isAiLoading.value = true
        try {
            val expenses = repository.getExpensesSync()
            val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
            val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonth) }
            
            if (monthlyExpenses.isEmpty()) {
                val emptyMsg = "반가워요! ${getUserNickname()}님, 첫 지출을 기록해 보시면 분석을 시작할게요! ✨"
                _aiGreeting.value = emptyMsg
                _aiReport.value = emptyMsg
                return@launch
            }

            val totalSpent = monthlyExpenses.filter { it.category.trim() != "수입" }.sumOf { it.amount }
            val totalIncome = monthlyExpenses.filter { it.category.trim() == "수입" }.sumOf { it.amount }
            val actualExpenses = monthlyExpenses.filter { it.category.trim() != "수입" }
            
            val categorySummary = actualExpenses
                .groupBy { it.category.trim() }
                .map { "${it.key}: ${it.value.sumOf { e -> e.amount }}원" }
                .joinToString(", ")
                
            val maxExpense = actualExpenses.maxByOrNull { it.amount }
            val maxExpenseText = if (maxExpense != null) "가장 큰 지출은 '${maxExpense.title}'에 ${maxExpense.amount}원이야." else ""
            val recentHistory = actualExpenses.take(5).joinToString(", ") { "${it.title}(${it.amount}원)" }

            val summary = """
                - 사용자 닉네임: ${getUserNickname()}
                - 이번 달 총 수입: ${totalIncome}원
                - 이번 달 총 지출: ${totalSpent}원 (예산: ${getTotalBudget()}원)
                - 카테고리별: [$categorySummary]
                - $maxExpenseText
                - 최근 지출 내역: [$recentHistory]
            """.trimIndent()

            // 두 가지 타입의 피드백을 동시에 생성 (또는 필요할 때 각각 호출 가능)
            launch { _aiGreeting.value = repository.getAiFeedback(summary, "GREETING") }
            launch { _aiReport.value = repository.getAiFeedback(summary, "REPORT") }
            
        } catch (e: Exception) {
            e.printStackTrace()
            _aiGreeting.value = "🤖 언제나 당신의 현명한 소비 생활을 응원합니다! ✨"
            _aiReport.value = "🤖 지출 내역을 분석하는 데 문제가 발생했어요. 하지만 아껴 쓰시는 모습 보기 좋습니다!"
        } finally {
            _isAiLoading.value = false
        }
    }

    fun setTotalBudget(amount: Int) {
        sharedPrefs.edit().putInt("total_budget", amount).apply()
    }

    fun getTotalBudget(): Int = sharedPrefs.getInt("total_budget", 0)

    fun getBudgetLimit(category: String): Int = sharedPrefs.getInt("budget_$category", 0)

    fun setBudgetLimit(category: String, amount: Int) {
        sharedPrefs.edit().putInt("budget_$category", amount).apply()
    }

    private var lastBudgetExceededNotifiedDate: String? = null

    fun updateBudgetStatus(expenses: List<ExpenseEntity>, context: Context, notificationHelper: NotificationHelper) {
        val currentMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        
        val monthlyExpenses = expenses.filter { it.date.startsWith(currentMonthStr) }
        val totalSpent = monthlyExpenses.filter { it.category.trim() != "수입" }.sumOf { it.amount }
        val totalBudget = getTotalBudget()

        if (totalBudget > 0 && totalSpent > totalBudget) {
            if (lastBudgetExceededNotifiedDate != todayStr) {
                notificationHelper.showBudgetExceededNotification(
                    "🚨 예산 초과 알림",
                    "이번 달 총 예산을 초과했습니다! 현재 지출: ${totalSpent}원"
                )
                lastBudgetExceededNotifiedDate = todayStr
            }
        }
    }

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
