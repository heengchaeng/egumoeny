package com.mobile.egumoney.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense_items ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity) // 👈 이 이름이 'insert'로 되어있는지 확인!

    @Update
    suspend fun update(expense: ExpenseEntity) // 👈 이 이름이 'update'로 되어있는지 확인!

    @Delete
    suspend fun delete(expense: ExpenseEntity) // 👈 이 이름이 'delete'로 되어있는지 확인!
}