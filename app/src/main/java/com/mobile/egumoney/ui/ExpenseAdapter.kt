package com.mobile.egumoney.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mobile.egumoney.R
import com.mobile.egumoney.data.ExpenseEntity
import com.mobile.egumoney.databinding.ItemExpenseBinding
import java.text.DecimalFormat

class ExpenseAdapter(
    private val onEditClick: (ExpenseEntity) -> Unit,
    private val onDeleteClick: (ExpenseEntity) -> Unit
) : ListAdapter<ExpenseEntity, ExpenseAdapter.ExpenseViewHolder>(ExpenseDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val binding = ItemExpenseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ExpenseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ExpenseViewHolder(private val binding: ItemExpenseBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(expense: ExpenseEntity) {
            val context = binding.root.context
            val dec = DecimalFormat("#,###")

            // 🎯 리스트의 카테고리를 레포지토리와 동일한 기준으로 표시
            val category = expense.category.trim()

            binding.tvItemTitle.text = expense.title
            
            // 🎨 수입/지출 색상 구분
            if (category == "수입") {
                binding.tvItemAmount.text = "+${dec.format(expense.amount)}원"
                binding.tvItemAmount.setTextColor(ContextCompat.getColor(context, R.color.income))
            } else {
                binding.tvItemAmount.text = "-${dec.format(expense.amount)}원"
                binding.tvItemAmount.setTextColor(ContextCompat.getColor(context, R.color.expense))
            }

            binding.tvItemDateWeather.text = "${expense.date} | ${expense.weather}"

            val emojis = mapOf("식비" to "🍴", "교통비" to "🚌", "쇼핑" to "🛍️", "문화" to "🎬", "투자" to "📈", "수입" to "💰", "기타" to "🏷️")
            val emoji = emojis[category] ?: "🏷️"
            binding.tvItemCategory.text = "$emoji $category"

            // 🎨 차트와 동일한 색상 지정
            val catColorRes = when (category) {
                "식비" -> R.color.cat_food
                "교통비" -> R.color.cat_transport
                "쇼핑" -> R.color.cat_shopping
                "문화" -> R.color.cat_culture
                "투자" -> R.color.cat_investment
                "수입" -> R.color.income
                else -> R.color.cat_etc
            }
            
            val backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.bg_category_tag)?.mutate()
            backgroundDrawable?.setTint(ContextCompat.getColor(context, catColorRes))
            binding.tvItemCategory.background = backgroundDrawable

            binding.btnItemEdit.setOnClickListener { onEditClick(expense) }
            binding.btnItemDelete.setOnClickListener { onDeleteClick(expense) }

            // 🎯 기록 시간 표시 (yyyy-MM-dd HH:mm -> HH:mm)
            val time = try {
                if (expense.date.length >= 16) {
                    expense.date.substring(11, 16)
                } else {
                    val parts = expense.date.split(" ")
                    if (parts.size >= 2) parts[1] else "--:--"
                }
            } catch (e: Exception) {
                "--:--"
            }
            binding.tvItemRecordedTime.text = time
        }
    }

    class ExpenseDiffCallback : DiffUtil.ItemCallback<ExpenseEntity>() {
        override fun areItemsTheSame(oldItem: ExpenseEntity, newItem: ExpenseEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ExpenseEntity, newItem: ExpenseEntity) = oldItem == newItem
    }
}
