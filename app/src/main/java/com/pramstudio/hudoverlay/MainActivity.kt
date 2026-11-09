package com.pramstudio.hudoverlay

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private var editingLandscape = false
    private lateinit var labelInput: EditText
    private lateinit var opacitySeek: SeekBar
    private lateinit var status: TextView
    private lateinit var repeatCheck: CheckBox
    private lateinit var previewText: TextView
    private lateinit var previewMeta: TextView
    private lateinit var previewFrame: FrameLayout
    private var currentProfile = HudProfile("HUD Overlay", 0.5f, 0.18f, 0.85f)

    companion object {
        private const val PICK_IMAGE = 101
        private const val PICK_VIDEO = 102
        private const val PICK_AUDIO = 103
        private const val BG = 0xFF0B1020.toInt()
        private const val CARD = 0xFF151D31.toInt()
        private const val MUTED = 0xFF9AA8C2.toInt()
        private const val ACCENT = 0xFF8B7CFF.toInt()
        private const val WHITE = 0xFFF5F7FF.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = 0
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
        showEditor(false)
    }

    private fun showEditor(landscape: Boolean) {
        editingLandscape = landscape
        currentProfile = HudProfiles.load(this, landscape)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
            setBackgroundColor(BG)
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }

        root.addView(TextView(this).apply {
            text = "HUD STUDIO"
            textSize = 12f
            letterSpacing = 0.18f
            setTextColor(ACCENT)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Customize your overlay"
            textSize = 25f
            setTextColor(WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(3))
        })
        root.addView(TextView(this).apply {
            text = "Atur tampilan live stream dalam satu tempat."
            textSize = 13f
            setTextColor(MUTED)
        })

        val modeCard = card()
        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        modes.addView(modeButton("PORTRAIT", !landscape) { saveEditorFields(); showEditor(false) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        modes.addView(modeButton("LANDSCAPE", landscape) { saveEditorFields(); showEditor(true) }, LinearLayout.LayoutParams(0, dp(44), 1f))
        modeCard.addView(modes)
        root.addView(modeCard, marginTop(18))

        val previewCard = card()
        val previewHeader = row()
        previewHeader.addView(sectionTitle("Live preview"), LinearLayout.LayoutParams(0, -2, 1f))
        previewHeader.addView(TextView(this).apply {
            text = if (landscape) "16:9" else "9:16"
            textSize = 11f
            setTextColor(ACCENT)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(0x332F80FF, 30)
        })
        previewCard.addView(previewHeader)
        previewFrame = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF172B4D.toInt(), 0xFF31245C.toInt(), 0xFF101827.toInt())).apply { cornerRadius = dp(14).toFloat() }
            clipToOutline = true
        }
        val previewHeight = if (landscape) dp(150) else dp(240)
        previewCard.addView(previewFrame, LinearLayout.LayoutParams(-1, previewHeight).apply { topMargin = dp(12) })
        val frameBorder = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), 0x667E8DFF)
                cornerRadius = dp(10).toFloat()
            }
        }
        previewFrame.addView(frameBorder, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(9), dp(9), dp(9), dp(9)) })
        previewMeta = TextView(this).apply {
            text = "LIVE HUD PREVIEW"
            textSize = 9f
            letterSpacing = 0.12f
            setTextColor(0xFFB9C5E5.toInt())
            setPadding(dp(12), dp(10), dp(12), 0)
        }
        previewFrame.addView(previewMeta)
        previewText = TextView(this).apply {
            text = currentProfile.label
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(8f, 0f, 2f, Color.BLACK)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(0x553C4770, 12)
        }
        previewFrame.addView(previewText, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        previewCard.addView(TextView(this).apply {
            text = "Preview tampilan overlay (simulasi)"
            textSize = 11f
            setTextColor(MUTED)
            setPadding(0, dp(9), 0, 0)
        })
        root.addView(previewCard, marginTop(14))

        val textCard = card()
        textCard.addView(sectionTitle("Text watermark"))
        labelInput = EditText(this).apply {
            hint = "Masukkan teks HUD"
            setSingleLine(true)
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            textSize = 15f
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = rounded(0xFF202B43.toInt(), 12)
            setText(currentProfile.label)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    previewText.text = s?.toString().orEmpty().ifBlank { "HUD Overlay" }
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        textCard.addView(labelInput, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(12) })
        root.addView(textCard, marginTop(14))

        val positionCard = card()
        positionCard.addView(sectionTitle("Position & appearance"))
        seek(positionCard, "Horizontal", currentProfile.xPercent) { v -> updateProfile { it.copy(xPercent = v) } }
        seek(positionCard, "Vertical", currentProfile.yPercent) { v -> updateProfile { it.copy(yPercent = v) } }
        positionCard.addView(TextView(this).apply {
            text = "Opacity"
            textSize = 13f
            setTextColor(MUTED)
            setPadding(0, dp(13), 0, dp(2))
        })
        opacitySeek = SeekBar(this).apply {
            max = 90
            progress = ((currentProfile.opacity - 0.1f) * 100).toInt().coerceIn(0, 90)
            progressTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            thumbTintList = android.content.res.ColorStateList.valueOf(ACCENT)
        }
        positionCard.addView(opacitySeek)
        root.addView(positionCard, marginTop(14))

        val mediaCard = card()
        mediaCard.addView(sectionTitle("Media layers"))
        mediaCard.addView(mediaButton("Image / GIF", if (currentProfile.imageUri == null) "Choose image or GIF" else "Image selected · Change", "▧") { pick("image/*", PICK_IMAGE, false) })
        mediaCard.addView(mediaButton("Video overlay", if (currentProfile.videoUri == null) "Choose video" else "Video selected · Change", "▷") { pick("video/*", PICK_VIDEO, false) })
        mediaCard.addView(mediaButton("Web overlay", currentProfile.webUrl ?: "Add a website URL", "⌘") { editWeb() })
        mediaCard.addView(mediaButton("MP3 playlist", "${currentProfile.audioUris.size} tracks · Choose files", "♫") { pick("audio/*", PICK_AUDIO, true) })
        repeatCheck = CheckBox(this).apply {
            text = "Repeat playlist automatically"
            textSize = 13f
            setTextColor(WHITE)
            buttonTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            isChecked = currentProfile.repeatAudio
        }
        mediaCard.addView(repeatCheck)
        root.addView(mediaCard, marginTop(14))

        val actions = card()
        val save = actionButton("SAVE LAYOUT", false) {
            saveEditorFields()
            status.text = "Layout berhasil disimpan."
        }
        actions.addView(save, LinearLayout.LayoutParams(-1, dp(48)))
        val start = actionButton("START OVERLAY", true) {
            saveEditorFields()
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                status.text = "Izinkan tampil di atas aplikasi lain, lalu tekan START lagi."
            } else {
                val serviceIntent = Intent(this@MainActivity, OverlayService::class.java).setAction(OverlayService.ACTION_START)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
                status.text = "Overlay berjalan. Kontrol tersedia di notification bar."
            }
        }
        actions.addView(start, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) })
        status = TextView(this).apply {
            text = "Portrait dan Landscape menyimpan konfigurasi terpisah."
            textSize = 11f
            setTextColor(MUTED)
            setPadding(0, dp(12), 0, 0)
        }
        actions.addView(status)
        root.addView(actions, marginTop(14))
        setContentView(scroll)
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = rounded(CARD, 18)
    }

    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 16f
        setTextColor(WHITE)
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun marginTop(value: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(value) }

    private fun modeButton(value: String, selected: Boolean, click: () -> Unit) = TextView(this).apply {
        text = value
        gravity = Gravity.CENTER
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (selected) Color.WHITE else MUTED)
        background = rounded(if (selected) ACCENT else 0xFF202B43.toInt(), 12)
        setOnClickListener { click() }
    }

    private fun mediaButton(title: String, subtitle: String, symbol: String, click: () -> Unit): View {
        val line = row().apply {
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener { click() }
        }
        val icon = TextView(this).apply {
            text = symbol
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(ACCENT)
            background = rounded(0x332F80FF, 12)
        }
        line.addView(icon, LinearLayout.LayoutParams(dp(42), dp(42)))
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@MainActivity).apply { text = title; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(WHITE) })
            addView(TextView(this@MainActivity).apply { text = subtitle; textSize = 11f; setTextColor(MUTED); setPadding(0, dp(3), 0, 0) })
        }
        line.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(TextView(this).apply { text = "›"; textSize = 25f; setTextColor(MUTED) })
        return line
    }

    private fun actionButton(value: String, primary: Boolean, click: () -> Unit) = TextView(this).apply {
        text = value
        gravity = Gravity.CENTER
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = rounded(if (primary) ACCENT else 0xFF27334D.toInt(), 14)
        setOnClickListener { click() }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun seek(root: LinearLayout, label: String, initial: Float, changed: (Float) -> Unit) {
        root.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(MUTED)
            setPadding(0, dp(12), 0, dp(2))
        })
        root.addView(SeekBar(this).apply {
            max = 100
            progress = (initial * 100).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            thumbTintList = android.content.res.ColorStateList.valueOf(ACCENT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) changed(value / 100f)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        })
    }

    private fun updateProfile(change: (HudProfile) -> HudProfile) {
        currentProfile = change(HudProfiles.load(this, editingLandscape))
        HudProfiles.save(this, editingLandscape, currentProfile)
    }

    private fun saveEditorFields() {
        currentProfile = HudProfiles.load(this, editingLandscape).copy(
            label = labelInput.text.toString().ifBlank { "HUD Overlay" },
            opacity = 0.1f + opacitySeek.progress / 100f,
            repeatAudio = repeatCheck.isChecked
        )
        HudProfiles.save(this, editingLandscape, currentProfile)
    }

    private fun pick(type: String, code: Int, multiple: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            setType(type)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, code)
    }

    @Deprecated("Retained for compatibility with Android document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        val flags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            data.data?.let { contentResolver.takePersistableUriPermission(it, flags) }
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) contentResolver.takePersistableUriPermission(clip.getItemAt(i).uri, flags)
            }
        } catch (_: Exception) { }

        when (requestCode) {
            PICK_IMAGE -> data.data?.toString()?.let { uri -> updateProfile { it.copy(imageUri = uri) } }
            PICK_VIDEO -> data.data?.toString()?.let { uri -> updateProfile { it.copy(videoUri = uri) } }
            PICK_AUDIO -> {
                val selected = mutableListOf<String>()
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) selected.add(clip.getItemAt(i).uri.toString())
                } ?: data.data?.let { selected.add(it.toString()) }
                if (selected.isNotEmpty()) updateProfile { it.copy(audioUris = selected) }
            }
        }
        showEditor(editingLandscape)
    }

    private fun editWeb() {
        val field = EditText(this).apply {
            hint = "https://example.com"
            setSingleLine(true)
            setText(HudProfiles.load(this@MainActivity, editingLandscape).webUrl.orEmpty())
        }
        AlertDialog.Builder(this)
            .setTitle("Web Overlay URL")
            .setView(field)
            .setPositiveButton("Simpan") { _, _ ->
                var url = field.text.toString().trim()
                if (url.isNotEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
                updateProfile { it.copy(webUrl = url.ifBlank { null }) }
                showEditor(editingLandscape)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
