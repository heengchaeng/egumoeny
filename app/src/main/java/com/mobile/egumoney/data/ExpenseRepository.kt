package com.mobile.egumoney.data

import android.util.Log
import com.mobile.egumoney.BuildConfig
import com.mobile.egumoney.model.WeatherResponse
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject

class ExpenseRepository(private val expenseDao: ExpenseDao) {

    val allExpenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()

    // 💡 API 키가 제대로 들어왔는지 로그로 확인 (보안상 앞자리만)
    init {
        val key = BuildConfig.GROQ_API_KEY
        if (key.isEmpty()) {
            Log.e("GroqAPI", "❌ API 키가 비어있습니다! local.properties를 확인하세요.")
        } else {
            Log.d("GroqAPI", "✅ API 키 로드됨: ${key.take(5)}***")
        }
    }

    private val groqService: GroqService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.groq.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(GroqService::class.java)
    }

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
                [카테고리] 식비, 교통비, 쇼핑, 문화, 투자, 기타
                구조: {"title": "항목명", "amount": 숫자, "category": "카테고리"}
            """.trimIndent()

            val request = GroqRequest(
                messages = listOf(
                    GroqMessage(role = "user", content = prompt)
                )
            )
            
            val response = groqService.getChatCompletion("Bearer ${BuildConfig.GROQ_API_KEY}", request)
            var jsonText = response.choices.firstOrNull()?.message?.content?.trim() ?: ""
            
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
            Log.e("GroqAPI", "Parsing Error: ${e.message}")
            // 💡 에러 발생 시 수동 추출 로직으로 대체
            ExpenseEntity(
                date = dateStr,
                title = extractTitleFallback(sentence),
                amount = extractAmountFallback(sentence),
                category = guessCategory(sentence),
                weather = weather
            )
        }
    }

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
            val systemPrompt = """
                당신은 사용자의 소비를 분석해주는 AI 금융 비서 '에구머니'입니다. 다음 지침에 따라 친절하고 명확하게 분석 보고를 작성해 주세요.

                [메시지 구성]
                1. 첫 문장은 반드시 "에구머니! [분석 내용]" 형식으로 시작하세요.
                2. 데이터에서 가장 지출이 큰 카테고리와 금액을 언급하며 현황을 알려주세요.
                3. 날씨 데이터나 소비 패턴을 결합하여 주의할 점이나 팁을 한 문장으로 제안하세요. (예: 비 오는 날의 특정 지출 조합 경고 등)
                4. 적절한 이모지를 사용하세요.

                [말투 및 제약사항]
                - "~입니다", "~하세요"와 같은 정중한 존댓말을 사용하세요.
                - 너무 길지 않게 두 문장 정도로 핵심만 전달하세요.
                - 제공된 [지출 요약] 데이터를 바탕으로 팩트에 기반하되, 비서처럼 조언을 덧붙이세요.
            """.trimIndent()

            val request = GroqRequest(
                messages = listOf(
                    GroqMessage(role = "system", content = systemPrompt),
                    GroqMessage(role = "user", content = "내 소비 요약 데이터야, 분석해줘!\n\n$summary")
                )
            )
            val response = groqService.getChatCompletion("Bearer ${BuildConfig.GROQ_API_KEY}", request)
            response.choices.firstOrNull()?.message?.content ?: "지출을 좀 더 기록해봐, 분석해줄게!"
        } catch (e: Exception) {
            Log.e("GroqAPI", "Feedback Error: ${e.message}")
            if (BuildConfig.GROQ_API_KEY.isEmpty()) "❌ API 키가 설정되지 않았습니다."
            else "🤖 AI가 응답하지 않습니다. (네트워크나 키 권한 확인 필요)"
        }
    }

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherResponse? {
        return try { weatherService.getCurrentWeather(lat, lon, BuildConfig.WEATHER_API_KEY) } catch (e: Exception) { null }
    }
}
