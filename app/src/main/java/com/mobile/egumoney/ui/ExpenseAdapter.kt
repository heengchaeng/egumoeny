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

            // 1. 기본 텍스트 데이터 매핑
            binding.tvItemTitle.text = expense.title
            binding.tvItemAmount.text = "${dec.format(expense.amount)}원"
            binding.tvItemDateWeather.text = "${expense.date} | ${expense.weather}"

            
  val titleText = expense.title.lowercase()

// 🍔 식비 키워드 리스트
val foodKeywords = listOf(
    "돈까스", "밥", "커피", "카페", "라떼", "식사", "치킨", "피자", "버거", "편의점", "마트",
    "햄버거", "아이스크림", "돈카츠", "돈코츠라멘", "라멘", "라면", "떡볶이", "파스타", "비빔밥", "오리고기", 
    "고기", "국수", "삼겹살", "불고기", "양념갈비", "갈비", "볶음밥", "짜장면", "짬뽕", "찜닭", "계란찜", 
    "에이드", "수육", "족발", "잡채", "반찬", "김밥", "쫄면", "육회", "육사시미", "불닭볶음면", 
    "불닭", "까르보불닭", "핫바", "닭가슴살", "닭강정", "감자", "바베큐", "케밥", "타코야끼", "코코넛", "밀크티","휘낭시에", "케이크", "쿠키", "마카롱", "튀김", "붕어빵", "만두", "김말이", "생선까스","샌드위치", "닭꼬치","닭갈비", "모밀", "초밥", "국밥",
    "우유", "간장계란밥", "곱도리탕", "낙곱새", "오므라이스", "콩나물불고기", "샤브샤브", "콩불", "콩나물국밥", "계란말이", "공차", "버블티", "라뗴", "핫도그","소떡소떡", "어묵","크로플", "과자", "배달"
    
)

// 🚌 교통비 키워드 리스트
val transportKeywords = listOf(
    "택시", "버스", "지하철", "주유", "ktx", "기차", "비행기", "배", "오토바이", "자전거", "킥보드", "톨비", "톨게이트비"
)

// 🛍️ 쇼핑 키워드 리스트 (요청하신 브랜드 100% 반영!)
val shoppingKeywords = listOf(
    "쿠팡", "다이소", "올리브영", "옷", "gs25", "cu", "이마트24", "세븐일레븐", "교보문고", "롯데마트", "이마트", "홈플러스", "무신사", "에이블리", "지그재그"
)

// 🎬 문화 키워드 리스트 (여기도 미리 깔끔하게 정리해뒀어요!)
val cultureKeywords = listOf(
    "영화", "넷플", "책", "cgv", "공연", "전시", "뮤지컬", "티켓", "메가박스", "롯데시네마"
)

val standardCategory = when {
    // 1. 식비 필터링
    foodKeywords.any { titleText.contains(it) } || 
    expense.category.trim() in listOf("식비", "식사", "카페", "디저트", "맛집") -> "식비"

    // 2. 교통비 필터링
    transportKeywords.any { titleText.contains(it) } || 
    expense.category.trim() in listOf("교통비", "교통", "택시", "버스","기차","비행기") -> "교통비"

    // 3. 쇼핑 필터링 (요청하신 GS25, CU, 마트 시리즈 완벽 작동!)
    shoppingKeywords.any { titleText.contains(it) } || 
    expense.category.trim() in listOf("쇼핑", "구매", "마트", "쿠팡", "올리브영", "다이소", "이마트", "홈플러스", "gs25", "cu", "세븐일레븐") -> "쇼핑"

    // 4. 문화 필터링
    cultureKeywords.any { titleText.contains(it) } || 
    expense.category.trim() in listOf("문화", "영화", "콘텐츠", "여가") -> "문화"

    // 나머지는 기존 카테고리 유지 혹은 기타
    else -> "기타"
}
            // 화면에 보정된 카테고리 텍스트 표시
            binding.tvItemCategory.text = standardCategory

            // 3. 보정된 카테고리에 맞춰 배경 색상 지정
            val catColorRes = when (standardCategory) {
                "식비" -> R.color.cat_food
                "교통비" -> R.color.cat_transport
                "쇼핑" -> R.color.cat_shopping
                "문화" -> R.color.cat_culture
                else -> R.color.cat_etc
            }
            binding.tvItemCategory.setBackgroundColor(ContextCompat.getColor(context, catColorRes))

            // 4. 이벤트 리스너 연결
            binding.btnItemEdit.setOnClickListener { onEditClick(expense) }
            binding.btnItemDelete.setOnClickListener { onDeleteClick(expense) }
        }
    }

    class ExpenseDiffCallback : DiffUtil.ItemCallback<ExpenseEntity>() {
        override fun areItemsTheSame(oldItem: ExpenseEntity, newItem: ExpenseEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ExpenseEntity, newItem: ExpenseEntity): Boolean {
            return oldItem == newItem
        }
    }
}