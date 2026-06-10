package com.mobile.egumoney.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
// 🚨 ExpenseDao.kt 파일의 쿼리 부분을 꼭 확인하세요!
@Query("SELECT * FROM expense_items ORDER BY date DESC") // 👈 이전 에러 방지를 위해 엔티티와 일치시킴
fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)
}

