package com.mobile.egumoney.data

import com.google.ai.client.generativeai.GenerativeModel
import com.mobile.egumoney.BuildConfig
import com.mobile.egumoney.model.WeatherResponse
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()

    // 🚨 핵심 수정: 모델명에서 'models/'를 제거하고 정확히 "gemini-1.5-flash"만 입력해야 404 에러가 해결됩니다.
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash", 
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun insert(expense: ExpenseEntity) {
        expenseDao.insert(expense)
    }

    suspend fun update(expense: ExpenseEntity) {
        expenseDao.update(expense)
    }

    suspend fun delete(expense: ExpenseEntity) {
        expenseDao.delete(expense)
    }

    // 자연어 분석 파싱 (프로젝트 구현 방식에 맞게 유지)
    suspend fun parseExpense(sentence: String, weather: String): ExpenseEntity? {
        return try {
            val prompt = """
                사용자의 지출 문장을 분석해서 JSON 형식으로만 반환해줘.
                문장: "$sentence"
                출력 형식 예시:
                {"title": "점심 식사", "amount": 9000, "category": "식비"}
                카테고리는 반드시 '식비', '교통비', '쇼핑', '문화', '투자', '기타' 중 하나여야 해.
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            val jsonText = response.text ?: return null
            
            // 단순 파싱 예시 (실제 프로젝트 구조에 맞게 파싱 로직이 구현되어 있다면 그대로 두셔도 됩니다)
            val title = jsonText.substringAfter("\"title\": \"").substringBefore("\"")
            val amount = jsonText.substringAfter("\"amount\": ").substringBefore(",").substringBefore("}").trim().toIntOrNull() ?: 0
            val category = jsonText.substringAfter("\"category\": \"").substringBefore("\"")

            ExpenseEntity(
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                title = title,
                amount = amount,
                category = category,
                weather = weather
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // AI 소비 분석 잔소리 생성
    suspend fun getAiFeedback(summary: String): String {
        val prompt = """
            당신은 사용자의 자산 관리를 돕는 깐깐하고 위트 있는 AI 가계부 비서입니다.
            다음 이번 달 지출 현황 요약을 보고, 지출이 많다면 따끔하게 잔소리를 해주고 잘 아꼈다면 칭찬을 해주는 피드백을 친근한 말투로 3줄 이내로 작성해 주세요.
            
            $summary
        """.trimIndent()

        val response = generativeModel.generateContent(prompt)
        return response.text ?: "소비 분석을 가져오지 못했습니다."
    }

    // 날씨 API 호출 (기존 연동용 placeholder 또는 실제 구현 유지)
    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResponse? {
        // 실제 Retrofit 연동 코드가 있다면 여기에 포함됩니다.
        return null 
    }
}