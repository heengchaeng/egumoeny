package com.mobile.egumoney.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expense_items") // 👈 이 이름이 'expense_items' 여야 Dao의 Query 에러가 해결됩니다!
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val title: String,
    val amount: Int,
    val category: String,
    val weather: String
)