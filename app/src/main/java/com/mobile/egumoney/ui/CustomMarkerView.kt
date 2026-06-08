package com.mobile.egumoney.ui

import android.content.Context
import android.widget.TextView
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.mobile.egumoney.R
import java.text.DecimalFormat

class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {

    private val tvContent: TextView = findViewById(R.id.tv_marker_content)
    private val dec = DecimalFormat("#,###")

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return

        if (e is PieEntry) {
            tvContent.text = "${e.label}: ${dec.format(e.value.toInt())}원"
        } else if (e is BarEntry) {
            tvContent.text = "${dec.format(e.y.toInt())}원"
        } else {
            tvContent.text = "${dec.format(e.y.toInt())}원"
        }

        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}
