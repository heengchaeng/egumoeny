package com.mobile.egumoney.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,     // "yyyy-MM-dd"
    val title: String,    // 지출 항목명
    val amount: Int,      // 금액
    val category: String, // 식비, 교통비, 쇼핑, 문화, 기타
    val weather: String   // 날씨 상태 (예: "☀️ 맑음", "🌧️ 비" 등)
)
