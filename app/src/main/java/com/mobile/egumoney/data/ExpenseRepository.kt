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

    // ✅ 최신 모델명 적용 및 안정화
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash", 
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    suspend fun getExpensesSync(): List<ExpenseEntity> {
        return expenseDao.getExpensesSync()
    }

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
                사용자의 지출 또는 수입 문장을 분석해서 오직 JSON 객체 한 개만 반환해라. 앞뒤에 ```json 같은 마크다운이나 설명은 절대 붙이지 마라.
                문장: "$sentence"
                반드시 아래 키 명칭을 지킬 것:
                {"title": "항목이름", "amount": 1000, "category": "식비"}
                카테고리는 무조건 다음 7개 중 하나로만 매핑해야 한다: '식비', '교통비', '쇼핑', '문화', '투자', '수입', '기타'
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
            
            val validCategories = arrayOf("식비", "교통비", "쇼핑", "문화", "투자", "수입", "기타")
            if (category !in validCategories) { category = "기타" }

            ExpenseEntity(
                date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
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
                lowered.contains("월급") || lowered.contains("용돈") || lowered.contains("입금") || lowered.contains("보너스") || lowered.contains("수입") || lowered.contains("급여") -> category = "수입"
            }
            
            val cleanTitle = sentence.replace(Regex("\\d"), "").replace(",", "").replace("원", "").trim().take(12)

            ExpenseEntity(
                date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()),
                title = if (cleanTitle.isEmpty()) "지출 내역" else cleanTitle,
                amount = extractedAmount,
                category = category,
                weather = weather
            )
        }
    }

    // AI 소비 분석 기능 (GREETING: 응원, REPORT: 잔소리/분석)
    suspend fun getAiFeedback(summary: String, type: String = "GREETING"): String {
        return try {
            val prompt = if (type == "GREETING") {
                """
                당신은 사용자의 자산 관리를 돕는 아주 친절하고 유능한 AI 가계부 비서입니다.
                사용자의 이번 달 지출 현황과 최근 내역을 바탕으로 따뜻한 '응원의 한마디'를 작성해 주세요.
                [분석 데이터]: $summary
                - 첫 문장은 사용자의 이름을 부르며 친근하게 시작 (예: "행복한 펭귄님, 오늘도 고생 많으셨어요!")
                - 따뜻하고 긍정적인 톤으로 2~3줄 내외 작성.
                - 최근 지출이 줄었다면 칭찬을, 늘었다면 부드러운 격려를 포함하세요.
                """.trimIndent()
            } else {
                """
                당신은 사용자의 과소비를 막기 위해 아주 냉철하고 깐깐하게 분석하는 자산 관리 전문가입니다.
                사용자의 지출 데이터와 예산 상황을 보고 아주 날카로운 '소비 잔소리' 리포트를 작성해 주세요.
                [분석 데이터]: $summary
                - 단순히 요약하지 말고, '소비 패턴의 급격한 변화'나 '특정 카테고리에 편중된 지출'을 포착해서 지적하세요.
                - 예산을 초과했거나 지출이 많은 항목이 있다면 아주 따끔하게 지적하세요. (예: "또 식비에 이만큼이나 쓰셨나요?")
                - 말투는 깐깐하지만 유머러스하게(츤데레 느낌) 3~4줄 정도로 작성하세요.
                - 구체적인 금액과 항목을 언급하며 팩트 폭격을 하세요.
                - 해결책(예: "내일은 무지출 챌린지 어때요?")을 한 가지 제안하세요.
                """.trimIndent()
            }

            val response = generativeModel.generateContent(prompt)
            response.text ?: "데이터 분석 중... 잠시 후 다시 시도해 주세요!"
        } catch (e: Exception) {
            e.printStackTrace()
            if (type == "GREETING") "🤖 오늘도 당신의 현명한 소비 생활을 응원합니다! ✨"
            else "🤖 지출 내역을 보니 반성할 점이 보이네요. 내일은 좀 더 아껴볼까요? 💸"
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