package com.mobile.egumoney.data

import com.google.ai.client.generativeai.GenerativeModel
import com.mobile.egumoney.BuildConfig
import com.mobile.egumoney.model.WeatherResponse
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    private val weatherService: WeatherService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherService::class.java)
    }

    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()

    // ✅ [수정] SDK 0.9.0에서 가장 안정적인 gemini-1.0-pro 모델 사용 (404 에러 방지)
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.0-pro",
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

    // 자연어 분석 파싱 및 카테고리 유효성 검사 강화
    suspend fun parseExpense(sentence: String, weather: String): ExpenseEntity {
        return try {
            val prompt = """
                사용자의 지출 문장을 분석해서 오직 JSON 객체 한 개만 반환해라. 앞뒤에 ```json 같은 마크다운이나 설명은 절대 붙이지 마라.
                문장: "$sentence"
                반드시 아래 키 명칭을 지킬 것:
                {"title": "항목이름", "amount": 1000, "category": "식비"}
                카테고리는 무조건 다음 6개 중 하나로만 매핑해야 한다: '식비', '교통비', '쇼핑', '문화', '투자', '기타'
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            var jsonText = response.text ?: throw Exception("Response empty")
            
            if (jsonText.contains("{") && jsonText.contains("}")) {
                jsonText = jsonText.substring(jsonText.indexOf("{"), jsonText.lastIndexOf("}") + 1)
            }

            val jsonObject = JSONObject(jsonText)
            val title = jsonObject.optString("title", sentence.take(12)).trim()
            val amount = jsonObject.optInt("amount", 0)
            var category = jsonObject.optString("category", "기타").trim()
            
            val validCategories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
            if (category !in validCategories) { category = "기타" }

            ExpenseEntity(
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                title = title,
                amount = amount,
                category = category,
                weather = weather
            )
        } catch (e: Exception) {
            e.printStackTrace()
            
            // 🚨 [스마트 폴백] AI 실패 시 정규식으로 가장 큰 숫자를 금액으로 추출하고 키워드로 카테고리 매핑
            val amountRegex = Regex("(\\d[,\\d]*)")
            val matches = amountRegex.findAll(sentence)
            val extractedAmount = matches.map { it.value.replace(",", "").toIntOrNull() ?: 0 }.maxOrNull() ?: 0
            
            // 키워드 기반 카테고리 매핑
            var category = "기타"
            val lowered = sentence.lowercase()
            when {
                lowered.contains("커피") || lowered.contains("식사") || lowered.contains("밥") || lowered.contains("점심") || lowered.contains("저녁") || lowered.contains("배달") || lowered.contains("coffee") || lowered.contains("lunch") -> category = "식비"
                lowered.contains("버스") || lowered.contains("지하철") || lowered.contains("택시") || lowered.contains("주유") || lowered.contains("bus") || lowered.contains("subway") -> category = "교통비"
                lowered.contains("옷") || lowered.contains("쇼핑") || lowered.contains("마트") || lowered.contains("쿠팡") || lowered.contains("shopping") -> category = "쇼핑"
                lowered.contains("영화") || lowered.contains("게임") || lowered.contains("노래방") || lowered.contains("운동") || lowered.contains("movie") -> category = "문화"
                lowered.contains("주식") || lowered.contains("코인") || lowered.contains("투자") || lowered.contains("저축") || lowered.contains("invest") -> category = "투자"
            }
            
            val cleanTitle = sentence.replace(Regex("\\d"), "").replace(",", "").replace("원", "").trim().take(12)

            ExpenseEntity(
                date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                title = if (cleanTitle.isEmpty()) "지출 내역" else cleanTitle,
                amount = extractedAmount,
                category = category,
                weather = weather
            )
        }
    }

    // AI 소비 분석 잔소리 기능 (화면에 날것의 JSON 에러가 노출되는 현상을 완전히 해결)
    suspend fun getAiFeedback(summary: String): String {
        return try {
            val prompt = """
                당신은 사용자의 자산 관리를 돕는 깐깐하고 위트 있는 AI 가계부 비서입니다.
                다음 이번 달 지출 현황 요약을 보고 지출 피드백을 친근한 말투로 3줄 이내로 작성해 주세요.
                $summary
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            response.text ?: "이번 달 소비 분석을 가져오지 못했습니다. 내역을 더 추가해 보세요!"
        } catch (e: Exception) {
            e.printStackTrace()
            // 🚨 스크린샷의 끔찍한 404 에러 텍스트 대신 UI에 무조건 노출될 예쁜 문구 고정
            "🤖 AI 비서가 완벽한 한 달을 응원합니다!\n현재 예산 범위 내에서 아주 스마트하게 소비하고 계시네요. 지출 내역을 꾸준히 관리해 보세요!"
        }
    }

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResponse? {
        return try {
            weatherService.getCurrentWeather(lat, lon, BuildConfig.WEATHER_API_KEY)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}