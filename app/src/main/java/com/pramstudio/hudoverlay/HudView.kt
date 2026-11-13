package com.pramstudio.hudoverlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Paint
import android.graphics.Canvas
import android.view.Gravity
import android.graphics.drawable.AnimatedImageDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Menggambar semua elemen overlay (teks dan gambar/GIF) dengan posisi pecahan layar.
 * View dipakai ulang antar update supaya animasi GIF tidak restart tiap kali editor menyimpan.
 */
class HudView(context: Context) : FrameLayout(context) {

    private class Cached(val sig: String, val view: View)

    private class Entry(val item: HudItem, val view: View)

    private val cache = HashMap<Long, Cached>()
    private var entries: List<Entry> = emptyList()

    fun setItems(items: List<HudItem>) {
        val keep = items.map { it.id }.toSet()
        val iter = cache.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (e.key !in keep) {
                release(e.value.view)
                iter.remove()
            }
        }

        removeAllViews()
        val list = ArrayList<Entry>()
        for (item in items) {
            val sig = when (item.type) {
                ItemType.IMAGE -> "I|" + item.path
                ItemType.WEB -> "W|" + item.url
                ItemType.TEXT -> "T"
            }
            val existing = cache[item.id]
            val cached: Cached
            if (existing != null && existing.sig == sig) {
                cached = existing
            } else {
                if (existing != null) release(existing.view)
                val v = createView(item)
                if (v == null) {
                    cache.remove(item.id)
                    continue
                }
                cached = Cached(sig, v)
                cache[item.id] = cached
            }
            style(cached.view, item)
            addView(
                cached.view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            list.add(Entry(item, cached.view))
        }
        entries = list
        requestLayout()
    }

    /** Hentikan animasi dan bersihkan semua elemen. */
    fun hasTouchableWeb(): Boolean = entries.any { it.view is WebView && it.item.webTouchable }

    fun clear() {
        cache.values.forEach { release(it.view) }
        cache.clear()
        entries = emptyList()
        removeAllViews()
    }

    private fun createView(item: HudItem): View? {
        if (item.type == ItemType.TEXT) return StyledOverlayTextView(context)
        if (item.type == ItemType.WEB) {
            val address = normalizeWebUrl(item.url) ?: return null
            return WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setOnTouchListener { _, _ -> false }
                loadUrl(address)
            }
        }
        val drawable = Media.decodeDrawable(item.path) ?: return null
        val iv = ImageView(context)
        iv.scaleType = ImageView.ScaleType.FIT_XY
        iv.setImageDrawable(drawable)
        if (drawable is AnimatedImageDrawable) {
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            drawable.start()
        }
        return iv
    }

    private fun normalizeWebUrl(value: String): String? {
        val raw = value.trim()
        if (raw.isEmpty()) return null
        val address = if (raw.startsWith("https://", true) || raw.startsWith("http://", true)) raw else "https://$raw"
        val uri = android.net.Uri.parse(address)
        return address.takeIf { uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() }
    }

    private fun style(v: View, item: HudItem) {
        if (v is TextView) {
            v.text = item.text
            v.setTextColor(item.color)
            val style = when (item.fontWeight) {
                "MEDIUM" -> Typeface.BOLD
                "BOLD", "EXTRA_BOLD" -> Typeface.BOLD
                else -> Typeface.NORMAL
            } or if (item.italic) Typeface.ITALIC else Typeface.NORMAL
            val family = when (item.fontFamily) {
                "SANS_SERIF" -> "sans-serif"
                "SERIF" -> "serif"
                "MONOSPACE" -> "monospace"
                "CASUAL" -> "casual"
                "CURSIVE" -> "cursive"
                "FANTASY" -> "fantasy"
                else -> "sans-serif"
            }
            v.typeface = Typeface.create(family, style)
            v.paint.isFakeBoldText = item.fontWeight == "EXTRA_BOLD"
            v.paintFlags = if (item.underline) v.paintFlags or Paint.UNDERLINE_TEXT_FLAG else v.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            v.gravity = when (item.textAlignment) {
                "CENTER" -> Gravity.CENTER_HORIZONTAL
                "RIGHT" -> Gravity.RIGHT
                else -> Gravity.LEFT
            }
            v.setShadowLayer(item.shadowBlur, item.shadowDx, item.shadowDy, item.shadowColor)
            if (v is StyledOverlayTextView) {
                v.strokeColor = item.outlineColor
                v.strokeWidth = item.outlineWidth
            }
        }
        // Alpha hanya diterapkan ke teks/gambar. Alpha pada WebView akan
        // ikut memudarkan seluruh halaman (termasuk teks alert), bukan
        // membuat area di luar konten halaman menjadi transparan.
        v.alpha = if (v is WebView) 1f else item.alpha
    }

    private fun release(v: View) {
        if (v is WebView) {
            v.stopLoading()
            v.loadUrl("about:blank")
            v.removeAllViews()
            v.destroy()
        }
        if (v is ImageView) {
            val d = v.drawable
            if (d is AnimatedImageDrawable) d.stop()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val minSide = min(w, h).toFloat()
        for (e in entries) {
            val v = e.view
            if (v is TextView) {
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, e.item.size * minSide)
                v.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
            } else if (v is WebView) {
                val tw = (e.item.size * minSide).roundToInt().coerceAtLeast(1)
                val parts = e.item.webAspectRatio.split(":")
                val ratio = (parts.getOrNull(0)?.toFloatOrNull() ?: 16f) /
                    (parts.getOrNull(1)?.toFloatOrNull() ?: 9f)
                val th = (tw / ratio).roundToInt().coerceAtLeast(1)
                v.measure(
                    MeasureSpec.makeMeasureSpec(tw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(th, MeasureSpec.EXACTLY)
                )
            } else if (v is ImageView) {
                val d = v.drawable
                val iw = d?.intrinsicWidth ?: 0
                val ih = d?.intrinsicHeight ?: 0
                val tw = (e.item.size * minSide).roundToInt().coerceAtLeast(1)
                val th = if (iw > 0 && ih > 0) {
                    (tw.toFloat() * ih / iw).roundToInt().coerceAtLeast(1)
                } else {
                    tw
                }
                v.measure(
                    MeasureSpec.makeMeasureSpec(tw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(th, MeasureSpec.EXACTLY)
                )
            }
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val w = right - left
        val h = bottom - top
        for (e in entries) {
            val l = (e.item.x * w).roundToInt()
            val t = (e.item.y * h).roundToInt()
            e.view.layout(l, t, l + e.view.measuredWidth, t + e.view.measuredHeight)
        }
    }
}


private class StyledOverlayTextView(context: Context) : TextView(context) {
    var strokeColor: Int = Color.BLACK
    var strokeWidth: Float = 0f

    override fun onDraw(canvas: Canvas) {
        if (strokeWidth > 0f) {
            val originalColor = currentTextColor
            val originalStyle = paint.style
            val originalStroke = paint.strokeWidth
            setTextColor(strokeColor)
            paint.style = Paint.Style.STROKE
            paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = strokeWidth * 2f
            super.onDraw(canvas)
            setTextColor(originalColor)
            paint.style = originalStyle
            paint.strokeWidth = originalStroke
        }
        super.onDraw(canvas)
    }
}
