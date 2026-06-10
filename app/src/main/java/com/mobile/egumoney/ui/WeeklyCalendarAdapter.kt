package com.mobile.egumoney.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mobile.egumoney.data.ExpenseEntity
import com.mobile.egumoney.databinding.ItemWeeklyDayBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class WeeklyCalendarAdapter : RecyclerView.Adapter<WeeklyCalendarAdapter.WeeklyViewHolder>() {

    private var days = listOf<Date>()
    private var expenses = listOf<ExpenseEntity>()
    private val moneyFormatter = DecimalFormat("#,###")
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dayNameFormat = SimpleDateFormat("E", Locale.KOREAN)
    private val dateFormat = SimpleDateFormat("d", Locale.US)

    fun setData(days: List<Date>, expenses: List<ExpenseEntity>) {
        this.days = days
        this.expenses = expenses
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeeklyViewHolder {
        val binding = ItemWeeklyDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WeeklyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WeeklyViewHolder, position: Int) {
        val date = days[position]
        val dateStr = sdf.format(date)
        val dayExpenses = expenses.filter { it.date.startsWith(dateStr) }

        val income = dayExpenses.filter { it.category.trim() == "수입" }.sumOf { it.amount }
        val expense = dayExpenses.filter { it.category.trim() != "수입" }.sumOf { it.amount }

        holder.bind(date, income, expense)
    }

    override fun getItemCount(): Int = days.size

    inner class WeeklyViewHolder(private val binding: ItemWeeklyDayBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(date: Date, income: Int, expense: Int) {
            binding.tvWeeklyDayName.text = dayNameFormat.format(date)
            binding.tvWeeklyDate.text = dateFormat.format(date)

            // 🎯 데이터가 없어도 0원으로 항상 표시하여 내역을 보여줌
            binding.tvWeeklyDayIncome.text = "+${moneyFormatter.format(income)}"
            binding.tvWeeklyDayIncome.visibility = View.VISIBLE

            binding.tvWeeklyDayExpense.text = "-${moneyFormatter.format(expense)}"
            binding.tvWeeklyDayExpense.visibility = View.VISIBLE
            
            // 🎨 지출이 있으면 빨간색으로 강조
            if (expense > 0) {
                binding.tvWeeklyDayExpense.setTextColor(android.graphics.Color.parseColor("#FF5252")) // R.color.expense 대응
            } else {
                binding.tvWeeklyDayExpense.setTextColor(android.graphics.Color.parseColor("#9CA3AF")) // 회색
            }

            if (income > 0) {
                binding.tvWeeklyDayIncome.setTextColor(android.graphics.Color.parseColor("#4CAF50")) // R.color.income 대응
            } else {
                binding.tvWeeklyDayIncome.setTextColor(android.graphics.Color.parseColor("#9CA3AF"))
            }
            
            // 오늘 날짜 강조
            val today = sdf.format(Date())
            val target = sdf.format(date)
            if (today == target) {
                binding.root.setCardBackgroundColor(android.graphics.Color.parseColor("#E0E7FF"))
                binding.tvWeeklyDate.setTextColor(android.graphics.Color.parseColor("#4F46E5"))
            } else {
                binding.root.setCardBackgroundColor(android.graphics.Color.parseColor("#F9FAFB"))
                binding.tvWeeklyDate.setTextColor(android.graphics.Color.parseColor("#111827"))
            }
        }
    }
}
