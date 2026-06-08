package com.mobile.egumoney.data

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.mobile.egumoney.BuildConfig
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeminiParser {
    private val apiKey = BuildConfig.GEMINI_API_KEY

    // JSON parsing target model
    private val parserModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
        }
    )

    // Text feedback model
    private val feedbackModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    suspend fun parseExpense(sentence: String, weather: String): ExpenseEntity? {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // API 키가 없거나 유효하지 않으면 바로 폴백으로 저장
        if (apiKey.isBlank() || !apiKey.startsWith("AI")) {
            return makeFallbackExpense(sentence, dateStr, weather)
        }

        val prompt = """
            사용자의 지출 내역 문장: "$sentence"
            위 문장을 읽고 아래 JSON 스키마 형식에 맞추어 지출 항목, 금액, 카테고리를 파싱해줘.
            반드시 순수 JSON만 반환하고, 마크다운 코드 블록이나 설명 텍스트를 절대 포함하지 마.
            
            JSON 스키마:
            {
              "title": "지출 항목명",
              "amount": 10000, 
              "category": "식비" 
            }
            
            제약사항:
            - 카테고리는 반드시 '식비', '교통비', '쇼핑', '문화', '기타' 중 하나여야 해. 어울리는 카테고리가 없으면 '기타'로 매칭해줘.
            - 만약 금액이 언급되어 있지 않거나 추정하기 어려우면 amount는 0으로 채워줘.
        """.trimIndent()

        return try {
            val response = parserModel.generateContent(prompt)
            val rawText = response.text ?: return makeFallbackExpense(sentence, dateStr, weather)
            // 마크다운 코드 블록(```json ... ```) 제거 처리
            val jsonText = rawText
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val jsonObject = JSONObject(jsonText)
            val title = jsonObject.optString("title", sentence)
            val amount = jsonObject.optInt("amount", 0)
            val category = jsonObject.optString("category", "기타")

            ExpenseEntity(
                date = dateStr,
                title = title,
                amount = amount,
                category = category,
                weather = weather
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Gemini 파싱 실패 시 폴백: 입력 문장을 그대로 저장
            makeFallbackExpense(sentence, dateStr, weather)
        }
    }

    /**
     * Gemini API 실패 시 폴백: 입력 문장에서 숫자(금액)를 추출해 저장
     */
    private fun makeFallbackExpense(sentence: String, dateStr: String, weather: String): ExpenseEntity {
        // 숫자 추출 시도 (예: "커피 4500원" -> 4500)
        val amountRegex = Regex("(\\d[\\d,]*)\\s*원?")
        val matchResult = amountRegex.find(sentence)
        val amount = matchResult?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
        // 문장에서 금액 부분 제거해 항목명 추출
        val title = sentence.replace(amountRegex, "").trim().ifBlank { sentence.trim() }
        return ExpenseEntity(
            date = dateStr,
            title = title,
            amount = amount,
            category = "기타",
            weather = weather
        )
    }

    suspend fun generateFeedback(expensesSummary: String): String {
        val prompt = """
            사용자의 최근 지출 내역 요약 정보:
            $expensesSummary
            
            이 데이터를 분석해서 사용자의 소비 상태에 대한 "에구머니나!" 맞춤형 한 줄 평(잔소리 또는 칭찬)을 작성해줘.
            - 위트 있고 친근하면서도 뼈 때리는 잔소리 톤으로 작성해줘.
            - 한 줄 평인 만큼 1~2문장으로 아주 명료하고 간결하게 작성해줘.
            - 날씨와 소비 패턴(예: 비 오는 날에 충동 쇼핑을 많이 했다 등)의 연관성이 보인다면 이를 언급해줘.
        """.trimIndent()

        return try {
            val response = feedbackModel.generateContent(prompt)
            response.text ?: "소비 분석을 가져오지 못했습니다. 열심히 아껴보세요!"
        } catch (e: Exception) {
            e.printStackTrace()
            "AI 비서가 잔소리할 힘을 잃었습니다: ${e.message}"
        }
    }
}
