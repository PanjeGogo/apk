package com.pramstudio.hudoverlay

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt

/** Menggambar semua elemen HUD memakai posisi pecahan (0..1) dari ukuran layar. */
class HudView(context: Context) : FrameLayout(context) {

    private var current: List<HudItem> = emptyList()

    fun setItems(items: List<HudItem>) {
        current = items
        removeAllViews()
        items.forEach { item ->
            val tv = TextView(context)
            tv.text = item.text
            tv.setTextColor(item.color)
            tv.alpha = item.alpha
            addView(
                tv,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val minSide = min(w, h).toFloat()
        for (i in 0 until childCount) {
            val tv = getChildAt(i) as TextView
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, current[i].size * minSide)
            tv.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val l = (current[i].x * w).roundToInt()
            val t = (current[i].y * h).roundToInt()
            v.layout(l, t, l + v.measuredWidth, t + v.measuredHeight)
        }
    }
}
