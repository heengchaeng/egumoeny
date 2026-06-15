package com.mobile.egumoney.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mobile.egumoney.R
import com.mobile.egumoney.data.ExpenseEntity
import com.mobile.egumoney.databinding.ItemCalendarDayBinding
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class CalendarAdapter : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    private var days = listOf<Date?>()
    private var expenses = listOf<ExpenseEntity>()
    private val moneyFormatter = DecimalFormat("#,###")

    fun setData(days: List<Date?>, expenses: List<ExpenseEntity>) {
        this.days = days
        this.expenses = expenses
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val binding = ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CalendarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val date = days[position]
        if (date == null) {
            holder.clear()
        } else {
            val calendar = Calendar.getInstance()
            calendar.time = date
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            
            // 🎯 오늘 날짜 확인
            val today = Calendar.getInstance()
            val isToday = today.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                          today.get(Calendar.DAY_OF_YEAR) == calendar.get(Calendar.DAY_OF_YEAR)
            
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
            val dayExpenses = expenses.filter { it.date.startsWith(dateStr) }
            
            val income = dayExpenses.filter { it.category == "수입" }.sumOf { it.amount }
            val expense = dayExpenses.filter { it.category != "수입" }.sumOf { it.amount }

            holder.bind(day, income, expense, isToday)
        }
    }

    override fun getItemCount(): Int = days.size

    class CalendarViewHolder(private val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root) {
        private val moneyFormatter = DecimalFormat("#,###")

        fun bind(day: Int, income: Int, expense: Int, isToday: Boolean) {
            binding.tvDay.text = day.toString()
            
            // 🎯 오늘 날짜 강조 (검은색 동그라미 배경)
            if (isToday) {
                binding.tvDay.setBackgroundResource(R.drawable.bg_circle_black)
                binding.tvDay.setTextColor(Color.WHITE)
            } else {
                binding.tvDay.background = null
                binding.tvDay.setTextColor(Color.parseColor("#111827")) // text_primary
            }

            if (income > 0) {
                binding.tvIncome.text = "+${moneyFormatter.format(income)}"
                binding.tvIncome.visibility = View.VISIBLE
            } else {
                binding.tvIncome.visibility = View.GONE
            }

            if (expense > 0) {
                binding.tvExpense.text = "-${moneyFormatter.format(expense)}"
                binding.tvExpense.visibility = View.VISIBLE
            } else {
                binding.tvExpense.visibility = View.GONE
            }
        }

        fun clear() {
            binding.tvDay.text = ""
            binding.tvDay.background = null
            binding.tvIncome.text = ""
            binding.tvIncome.visibility = View.GONE
            binding.tvExpense.text = ""
            binding.tvExpense.visibility = View.GONE
        }
    }
}
