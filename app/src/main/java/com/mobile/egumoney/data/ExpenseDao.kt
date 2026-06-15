package com.mobile.egumoney.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
// 🚨 ExpenseDao.kt 파일의 쿼리 부분을 꼭 확인하세요!
    @Query("SELECT * FROM expense_items ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expense_items ORDER BY date DESC")
    suspend fun getExpensesSync(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)
}

