package com.mobile.egumoney.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mobile.egumoney.data.ExpenseEntity
import com.mobile.egumoney.databinding.ItemCalendarDayBinding
import java.text.DecimalFormat
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
            
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
            val dayExpenses = expenses.filter { it.date.startsWith(dateStr) }
            
            // 현재는 ExpenseEntity에 지출만 저장되어 있다고 가정 (추후 수입 구분 필요 시 amount 양수/음수 활용)
            // 여기서는 임시로 카테고리가 "수입"인 경우 빨간색, 그 외는 파란색으로 표시
            val income = dayExpenses.filter { it.category == "수입" }.sumOf { it.amount }
            val expense = dayExpenses.filter { it.category != "수입" }.sumOf { it.amount }

            holder.bind(day, income, expense)
        }
    }

    override fun getItemCount(): Int = days.size

    class CalendarViewHolder(private val binding: ItemCalendarDayBinding) : RecyclerView.ViewHolder(binding.root) {
        private val moneyFormatter = DecimalFormat("#,###")

        fun bind(day: Int, income: Int, expense: Int) {
            binding.tvDay.text = day.toString()
            
            if (income > 0) {
                binding.tvIncome.text = "+${moneyFormatter.format(income)}"
                binding.tvIncome.visibility = android.view.View.VISIBLE
            } else {
                binding.tvIncome.visibility = android.view.View.GONE
            }

            if (expense > 0) {
                binding.tvExpense.text = "-${moneyFormatter.format(expense)}"
                binding.tvExpense.visibility = android.view.View.VISIBLE
            } else {
                binding.tvExpense.visibility = android.view.View.GONE
            }
        }

        fun clear() {
            binding.tvDay.text = ""
            binding.tvIncome.text = ""
            binding.tvExpense.text = ""
        }
    }
}
