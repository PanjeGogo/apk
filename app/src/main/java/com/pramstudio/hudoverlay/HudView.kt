package com.pramstudio.hudoverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.net.http.SslError
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Batas aman ukuran satu elemen (px) supaya nilai ekstrem tidak merusak render. */
private const val MAX_VIEW_PX = 16384

/** Lebar kanvas virtual halaman Web Overlay dalam px CSS, seperti browser source OBS. */
const val WEB_VIRTUAL_WIDTH_CSS = 1280

/**
 * Skrip pemeriksaan isi halaman: ukuran viewport yang dilihat halaman, jumlah elemen,
 * potongan teks, dan versi Chrome/WebView. Hasilnya dicatat ke WebDiag.
 */
private const val PROBE_SCRIPT =
    """(function(){try{var b=document.body;return innerWidth+'x'+innerHeight+' dpr='+devicePixelRatio+' elemen='+document.getElementsByTagName('*').length+' html='+document.documentElement.outerHTML.length+' '+(navigator.userAgent.match(/Chrome\/[\d.]+/)||[''])[0]+' teks='+(b?b.innerText:'').replace(/\s+/g,' ').slice(0,100)}catch(e){return 'error '+e}})()"""

/**
 * Ukuran WebView di kanvas virtual (px perangkat) dan skala untuk mengecilkannya ke lebar tampil.
 * Halaman selalu merasa berada di layar selebar 1280 px CSS; rasio menentukan tingginya.
 */
class WebCanvas(val widthPx: Int, val heightPx: Int, val scale: Float)

fun webCanvasFor(density: Float, ratio: Float, targetWidthPx: Float): WebCanvas {
    val w = (WEB_VIRTUAL_WIDTH_CSS * density).roundToInt().coerceIn(1, MAX_VIEW_PX)
    val h = (w / ratio).roundToInt().coerceIn(1, MAX_VIEW_PX)
    return WebCanvas(w, h, targetWidthPx.coerceAtLeast(1f) / w)
}

/** Kecilkan view dari pojok kiri atas, supaya posisi kiri atas elemen tetap di x,y. */
fun applyWebScale(v: View, scale: Float) {
    v.pivotX = 0f
    v.pivotY = 0f
    if (v.scaleX != scale) v.scaleX = scale
    if (v.scaleY != scale) v.scaleY = scale
}

/** Alamat web yang valid (http/https), atau null kalau URL tidak bisa dipakai. */
fun normalizeWebUrl(value: String): String? {
    val raw = value.trim()
    if (raw.isEmpty()) return null
    val address = if (raw.startsWith("https://", true) || raw.startsWith("http://", true)) raw else "https://$raw"
    val uri = Uri.parse(address)
    return address.takeIf { uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() }
}

/** Rasio lebar/tinggi dari teks seperti "16:9". */
fun aspectRatioOf(value: String): Float {
    val parts = value.split(":")
    val w = parts.getOrNull(0)?.toFloatOrNull() ?: 16f
    val h = parts.getOrNull(1)?.toFloatOrNull() ?: 9f
    val ratio = w / h
    return if (ratio.isFinite() && ratio > 0f) ratio else 16f / 9f
}

/** Periksa isi halaman dan catat hasilnya. Aman dipanggil walau WebView sudah dihancurkan. */
private fun probeWebView(view: WebView, host: String) {
    try {
        view.evaluateJavascript(PROBE_SCRIPT) { result ->
            WebDiag.add("Isi halaman ($host): $result")
        }
    } catch (e: Exception) {
        // WebView sudah dihancurkan
    }
}

fun createOverlayWebView(context: Context, address: String): WebView {
    val host = Uri.parse(address).host ?: "?"
    val web = WebView(context)
    web.apply {
        setBackgroundColor(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        // Suara alert (notifikasi donasi, TTS) harus bisa diputar otomatis: overlay ini tidak pernah
        // disentuh, jadi tanpa ini WebView memblokir semua audio.
        settings.mediaPlaybackRequiresUserGesture = false
        // Buang penanda WebView dari user agent supaya widget memperlakukannya seperti Chrome biasa.
        settings.userAgentString = settings.userAgentString
            .replace("; wv", "")
            .replace("Version/4.0 ", "")
    }

    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            WebDiag.add("Selesai dimuat: $host")
            // Periksa isi halaman setelah skrip widget sempat berjalan, dan sekali lagi agak lama kemudian.
            view?.postDelayed({ probeWebView(view, host) }, 4000)
            view?.postDelayed({ probeWebView(view, host) }, 15000)
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            val main = request?.isForMainFrame == true
            val target = if (main) host else (request?.url?.host ?: "?")
            val label = if (main) "GAGAL memuat halaman" else "Gagal sub-resource"
            WebDiag.add("$label $target: ${error?.description} (${error?.errorCode})")
        }

        override fun onReceivedHttpError(
            view: WebView?,
            request: WebResourceRequest?,
            errorResponse: WebResourceResponse?
        ) {
            val main = request?.isForMainFrame == true
            val path = ((request?.url?.host ?: "?") + (request?.url?.path ?: "")).take(90)
            WebDiag.add("HTTP ${errorResponse?.statusCode} ${if (main) "(halaman)" else "(sub-resource)"} $path")
        }

        override fun onReceivedSslError(
            view: WebView?,
            handler: SslErrorHandler?,
            error: SslError?
        ) {
            WebDiag.add("Error SSL di $host (kode ${error?.primaryError})")
            handler?.cancel()
        }

        override fun onRenderProcessGone(
            view: WebView?,
            detail: RenderProcessGoneDetail?
        ): Boolean {
            WebDiag.add("Proses render WebView mati (crash=${detail?.didCrash()}) untuk $host")
            return true
        }
    }

    web.webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            if (consoleMessage != null &&
                consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR
            ) {
                val source = consoleMessage.sourceId()?.substringAfterLast('/') ?: "?"
                WebDiag.add("JS error: ${consoleMessage.message()} ($source:${consoleMessage.lineNumber()})")
            }
            return true
        }
    }

    val version = WebView.getCurrentWebViewPackage()?.versionName ?: "?"
    WebDiag.add("Muat $host (Android System WebView $version)")
    web.loadUrl(address)
    return web
}

/** Lepas WebView dari parent dulu baru di-destroy, supaya tidak ada peringatan "destroy() while attached". */
fun destroyOverlayWebView(v: WebView) {
    (v.parent as? ViewGroup)?.removeView(v)
    v.stopLoading()
    v.loadUrl("about:blank")
    v.removeAllViews()
    v.destroy()
}

/**
 * Menggambar elemen overlay (teks dan gambar/GIF, serta Web Overlay yang tidak touchable)
 * dengan posisi pecahan layar. Web Overlay yang touchable memakai jendela kecil sendiri
 * (lihat OverlayService) supaya sentuhan di luar area web tetap tembus ke game.
 * Halaman web dirender di kanvas virtual 1280 px CSS lalu diskalakan ke ukuran yang diatur.
 * View dipakai ulang antar update supaya animasi GIF tidak restart tiap kali editor menyimpan.
 */
class HudView(context: Context) : FrameLayout(context) {

    private class Cached(val sig: String, val view: View)

    private class Entry(val item: HudItem, val view: View)

    private val cache = HashMap<Long, Cached>()
    private var entries: List<Entry> = emptyList()
    private var lastW = 0
    private var lastH = 0

    /** Dipanggil di thread UI setiap kali ukuran layar overlay (px) berubah. */
    var onSized: ((Int, Int) -> Unit)? = null

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
            return createOverlayWebView(context, address)
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

    private fun style(v: View, item: HudItem) {
        if (v is TextView) {
            v.text = item.text
            v.setTextColor(item.color)

            // Bobot huruf asli (API 28+), bukan sekadar normal/bold.
            var weight = when (item.fontWeight) {
                "MEDIUM" -> 500
                "BOLD" -> 700
                "EXTRA_BOLD" -> 800
                else -> 400
            }
            if (item.fontFamily.endsWith("_BOLD")) weight = max(weight, 700)
            val family = when (item.fontFamily) {
                "SERIF", "SERIF_BOLD" -> "serif"
                "MONOSPACE" -> "monospace"
                "CASUAL" -> "casual"
                "CURSIVE" -> "cursive"
                "FANTASY" -> "fantasy"
                else -> "sans-serif"
            }
            v.typeface = Typeface.create(Typeface.create(family, Typeface.NORMAL), weight, item.italic)
            v.paint.isFakeBoldText = false
            v.paintFlags = if (item.underline) {
                v.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            } else {
                v.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }
            // Alignment berlaku untuk teks multi-baris (lebar view mengikuti baris terpanjang).
            v.gravity = when (item.textAlignment) {
                "CENTER" -> Gravity.CENTER_HORIZONTAL
                "RIGHT" -> Gravity.RIGHT
                else -> Gravity.LEFT
            }
            // Radius 0 membuat Android menghapus shadow; shadow tanpa blur dibuat dengan radius sangat kecil.
            val shadowRadius = when {
                item.shadowBlur > 0f -> item.shadowBlur
                item.shadowDx != 0f || item.shadowDy != 0f -> 0.01f
                else -> 0f
            }
            v.setShadowLayer(shadowRadius, item.shadowDx, item.shadowDy, item.shadowColor)
            if (v is StyledOverlayTextView) {
                v.strokeColor = item.outlineColor
                v.strokeWidth = item.outlineWidth
                // Padding secukupnya supaya outline dan shadow tidak terpotong batas view.
                val shadowExtent = if (shadowRadius > 0f) {
                    shadowRadius + max(abs(item.shadowDx), abs(item.shadowDy))
                } else {
                    0f
                }
                val pad = ceil(max(item.outlineWidth, shadowExtent)).toInt().coerceIn(0, 96)
                if (v.paddingLeft != pad) v.setPadding(pad, pad, pad, pad)
            }
            v.invalidate()
        }
        // Alpha hanya diterapkan ke teks/gambar. Alpha pada WebView akan
        // ikut memudarkan seluruh halaman (termasuk teks alert), bukan
        // membuat area di luar konten halaman menjadi transparan.
        v.alpha = if (v is WebView) 1f else item.alpha
    }

    private fun release(v: View) {
        if (v is WebView) {
            destroyOverlayWebView(v)
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
                // Halaman dirender 1280 px CSS lebar (tinggi mengikuti rasio) lalu diskalakan
                // ke lebar tampil, jadi layout halaman sama seperti di browser source OBS.
                val canvas = webCanvasFor(
                    resources.displayMetrics.density,
                    aspectRatioOf(e.item.webAspectRatio),
                    e.item.size * minSide
                )
                v.measure(
                    MeasureSpec.makeMeasureSpec(canvas.widthPx, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(canvas.heightPx, MeasureSpec.EXACTLY)
                )
                applyWebScale(v, canvas.scale)
            } else if (v is ImageView) {
                val d = v.drawable
                val iw = d?.intrinsicWidth ?: 0
                val ih = d?.intrinsicHeight ?: 0
                val tw = (e.item.size * minSide).roundToInt().coerceIn(1, MAX_VIEW_PX)
                val th = if (iw > 0 && ih > 0) {
                    (tw.toFloat() * ih / iw).roundToInt().coerceIn(1, MAX_VIEW_PX)
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
            // Padding teks (untuk outline/shadow) digeser balik supaya posisi huruf tetap di x,y.
            val pad = (e.view as? TextView)?.paddingLeft ?: 0
            val l = (e.item.x * w).roundToInt() - pad
            val t = (e.item.y * h).roundToInt() - pad
            e.view.layout(l, t, l + e.view.measuredWidth, t + e.view.measuredHeight)
        }
        if (w > 0 && h > 0 && (w != lastW || h != lastH)) {
            lastW = w
            lastH = h
            post { onSized?.invoke(w, h) }
        }
    }
}

/**
 * TextView dengan garis luar (outline). Garis digambar langsung lewat Layout dengan
 * mengubah warna Paint, tanpa memanggil setTextColor() di onDraw (yang dulu memicu
 * invalidate tanpa henti dan menguras baterai).
 */
private class StyledOverlayTextView(context: Context) : TextView(context) {
    var strokeColor: Int = Color.BLACK
    var strokeWidth: Float = 0f

    override fun onDraw(canvas: Canvas) {
        val textLayout = getLayout()
        if (strokeWidth > 0f && textLayout != null) {
            val p = paint
            val oldColor = p.color
            val oldStyle = p.style
            val oldWidth = p.strokeWidth
            val oldJoin = p.strokeJoin
            p.color = strokeColor
            p.style = Paint.Style.STROKE
            p.strokeJoin = Paint.Join.ROUND
            p.strokeWidth = strokeWidth * 2f
            canvas.save()
            canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
            textLayout.draw(canvas)
            canvas.restore()
            p.style = oldStyle
            p.strokeWidth = oldWidth
            p.strokeJoin = oldJoin
            p.color = oldColor
        }
        super.onDraw(canvas)
    }
}
