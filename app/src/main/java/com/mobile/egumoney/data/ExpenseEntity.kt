package com.mobile.egumoney.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// 🚨 괄호 안에 tableName = "expense_items"가 완벽하게 적혀있는지 꼭 보세요!
@Entity(tableName = "expense_items") 
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val title: String,
    val amount: Int,
    val category: String,
    val weather: String
)