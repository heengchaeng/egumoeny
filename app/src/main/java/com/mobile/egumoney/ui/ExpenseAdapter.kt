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

            binding.tvItemTitle.text = expense.title
            binding.tvItemAmount.text = "${dec.format(expense.amount)}원"
            binding.tvItemDateWeather.text = "${expense.date} | ${expense.weather}"

            // 🎯 리스트의 카테고리를 레포지토리와 동일한 기준으로 표시
            val category = expense.category.trim()
            binding.tvItemCategory.text = category

            // 🎨 차트와 동일한 색상 지정
            val catColorRes = when (category) {
                "식비" -> R.color.cat_food
                "교통비" -> R.color.cat_transport
                "쇼핑" -> R.color.cat_shopping
                "문화" -> R.color.cat_culture
                "투자" -> R.color.cat_investment
                else -> R.color.cat_etc
            }
            binding.tvItemCategory.setBackgroundColor(ContextCompat.getColor(context, catColorRes))

            binding.btnItemEdit.setOnClickListener { onEditClick(expense) }
            binding.btnItemDelete.setOnClickListener { onDeleteClick(expense) }
        }
    }

    class ExpenseDiffCallback : DiffUtil.ItemCallback<ExpenseEntity>() {
        override fun areItemsTheSame(oldItem: ExpenseEntity, newItem: ExpenseEntity) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ExpenseEntity, newItem: ExpenseEntity) = oldItem == newItem
    }
}
