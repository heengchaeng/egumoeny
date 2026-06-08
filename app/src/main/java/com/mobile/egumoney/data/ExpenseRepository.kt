package com.mobile.egumoney.data

import com.mobile.egumoney.BuildConfig
import com.mobile.egumoney.model.WeatherResponse
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()

    private val weatherService: WeatherService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherService::class.java)
    }

    private val geminiParser = GeminiParser()

    fun getExpensesByCategory(category: String): Flow<List<ExpenseEntity>> {
        return expenseDao.getExpensesByCategory(category)
    }

    suspend fun insert(expense: ExpenseEntity) {
        expenseDao.insertExpense(expense)
    }

    suspend fun update(expense: ExpenseEntity) {
        expenseDao.updateExpense(expense)
    }

    suspend fun delete(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)
    }

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResponse? {
        val apiKey = BuildConfig.WEATHER_API_KEY
        if (apiKey.isEmpty() || apiKey == "YOUR_WEATHER_API_KEY") return null
        return try {
            weatherService.getCurrentWeather(lat, lon, apiKey)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun parseExpense(sentence: String, weather: String): ExpenseEntity? {
        return geminiParser.parseExpense(sentence, weather)
    }

    suspend fun getAiFeedback(expensesSummary: String): String {
        return geminiParser.generateFeedback(expensesSummary)
    }
}
