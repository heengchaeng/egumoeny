package com.mobile.egumoney.data

import com.google.ai.client.generativeai.GenerativeModel
import com.mobile.egumoney.BuildConfig
import com.mobile.egumoney.model.WeatherResponse
import kotlinx.coroutines.flow.Flow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash", 
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    private val weatherService: WeatherService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherService::class.java)
    }

    suspend fun insert(expense: ExpenseEntity) = expenseDao.insert(expense)
    suspend fun update(expense: ExpenseEntity) = expenseDao.update(expense)
    suspend fun delete(expense: ExpenseEntity) = expenseDao.delete(expense)

    suspend fun parseExpense(sentence: String, weather: String): ExpenseEntity? {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        return try {
            val prompt = """
                당신은 가계부 전문가입니다. 다음 문장에서 항목, 금액, 카테고리를 추출해 JSON으로만 답하세요.
                문장: "$sentence"
                
                [카테고리 분류 규칙 - 반드시 이 중 하나만 선택]
                - '식비': 모든 음식, 식당, 카페, 커피, 배달, 마트, 편의점, 술, 간식, 야식
                - '교통비': 택시, 버스, 지하철, 주유, 주차, 기차, 하이패스, 비행기, 통행료
                - '쇼핑': 옷, 신발, 화장품, 다이소, 백화점, 생필품, 가전, 가구, 안경, 렌즈
                - '문화': 영화, 게임, 취미, 운동, 헬스, 책, 서점, 노래방, 여행, 숙박, 웹툰, 유튜브
                - '투자': 주식, 코인, 저축, 적금, 금리, 보험, 이자, 세금
                - '기타': 위 분류에 없는 모든 것
                
                구조: {"title": "항목명", "amount": 숫자, "category": "카테고리"}
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            var jsonText = response.text?.trim() ?: ""
            
            // JSON 응답 정제 로직 (마크다운 제거 및 순수 JSON 추출)
            if (jsonText.contains("{")) {
                jsonText = jsonText.substring(jsonText.indexOf("{"), jsonText.lastIndexOf("}") + 1)
            }

            val jsonObject = JSONObject(jsonText)
            val title = jsonObject.optString("title", "").ifBlank { extractTitleFallback(sentence) }
            val amount = jsonObject.optInt("amount", 0).let { if (it == 0) extractAmountFallback(sentence) else it }
            var category = jsonObject.optString("category", "기타").trim()
            
            val validCategories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "기타")
            if (category !in validCategories) category = guessCategory(sentence)

            ExpenseEntity(date = dateStr, title = title, amount = amount, category = category, weather = weather)
        } catch (e: Exception) {
            // AI 실패 시: 강화된 키워드 매칭 로직으로 분류 (차트가 '기타'로 도배되는 것 방지)
            ExpenseEntity(
                date = dateStr,
                title = extractTitleFallback(sentence),
                amount = extractAmountFallback(sentence),
                category = guessCategory(sentence),
                weather = weather
            )
        }
    }

    // 🎯 AI가 고장 나도 웬만한 건 다 맞추는 '마스터 분류 사전'
    private fun guessCategory(sentence: String): String {
        val s = sentence.lowercase()
        return when {
            s.contains(Regex("밥|카페|커피|식사|술|음식|배달|식당|빵|디저트|스타벅스|편의점|마트|점심|저녁|치킨|피자|간식|마라탕|분식|김밥|우유|맥주|소주|라면|요기요|배민|쿠팡이츠|삼겹살|포차|커피빈|투썸")) -> "식비"
            s.contains(Regex("버스|지하철|택시|주유|기름|교통|기차|하이패스|주차|카카오택시|따릉이|전동|정비|통행료|srt|ktx|비행기|항공|공항|톨비")) -> "교통비"
            s.contains(Regex("쇼핑|옷|신발|가방|쿠팡|선물|백화점|올리브영|지그재그|무신사|다이소|화장품|정장|가전|가구|이마트|홈플러스|안경|렌즈|문구|생활용품")) -> "쇼핑"
            s.contains(Regex("영화|공연|전시|취미|운동|헬스|넷플릭스|pc방|게임|책|서점|노래방|유튜브|축구|야구|웹툰|필라테스|연극|공연|골프|테니스|수영|탁구")) -> "문화"
            s.contains(Regex("주식|코인|적금|예금|투자|배당|삼성전자|비트코인|달러|금리|환전|부동산|보험|이자|청약|연금|신탁")) -> "투자"
            else -> "기타"
        }
    }

    private fun extractAmountFallback(sentence: String): Int {
        val match = Regex("(\\d[\\d,]*)\\s*원?").find(sentence)
        return match?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
    }

    private fun extractTitleFallback(sentence: String): String {
        val amountRegex = Regex("(\\d[\\d,]*)\\s*원?")
        return sentence.replace(amountRegex, "").trim().ifBlank { "지출 내역" }
    }

    suspend fun getAiFeedback(summary: String): String {
        return try {
            val response = generativeModel.generateContent("다음 가계부 요약을 보고 짧고 위트 있게 피드백해줘: $summary")
            response.text ?: "기록이 더 쌓이면 멋진 분석을 해드릴게요!"
        } catch (e: Exception) {
            "🤖 AI 비서가 새 키를 인식할 수 있게 [Build] -> [Clean Project] 후 앱을 다시 실행해 주세요!"
        }
    }

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResponse? {
        return try { weatherService.getCurrentWeather(lat, lon, BuildConfig.WEATHER_API_KEY) } catch (e: Exception) { null }
    }
}
