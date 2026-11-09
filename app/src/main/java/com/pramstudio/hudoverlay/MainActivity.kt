package com.pramstudio.hudoverlay

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
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

    companion object {
        private const val PICK_IMAGE = 101
        private const val PICK_VIDEO = 102
        private const val PICK_AUDIO = 103
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
        showEditor(false)
    }

    private fun showEditor(landscape: Boolean) {
        editingLandscape = landscape
        val profile = HudProfiles.load(this, landscape)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20, 24, 20, 24)
        }
        val scroll = ScrollView(this).apply { addView(root) }
        root.addView(TextView(this).apply {
            text = "HUD Overlay Editor"
            textSize = 25f
            setPadding(0, 0, 0, 16)
        })

        val modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        modes.addView(Button(this).apply {
            text = if (!landscape) "✓ Portrait" else "Portrait"
            setOnClickListener { saveEditorFields(); showEditor(false) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        modes.addView(Button(this).apply {
            text = if (landscape) "✓ Landscape" else "Landscape"
            setOnClickListener { saveEditorFields(); showEditor(true) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(modes)
        root.addView(TextView(this).apply {
            text = if (landscape) "Layout horizontal 16:9" else "Layout vertikal 9:16"
            setPadding(0, 12, 0, 8)
        })

        labelInput = EditText(this).apply {
            hint = "Nama / teks HUD"
            setSingleLine(true)
            setText(profile.label)
        }
        root.addView(labelInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        section(root, "Posisi dan tampilan")
        seek(root, "Posisi horizontal", profile.xPercent) { v -> updateProfile { it.copy(xPercent = v) } }
        seek(root, "Posisi vertikal", profile.yPercent) { v -> updateProfile { it.copy(yPercent = v) } }
        root.addView(TextView(this).apply { text = "Transparansi" })
        opacitySeek = SeekBar(this).apply {
            max = 90
            progress = ((profile.opacity - 0.1f) * 100).toInt().coerceIn(0, 90)
        }
        root.addView(opacitySeek)

        section(root, "Media overlay")
        root.addView(Button(this).apply {
            text = if (profile.imageUri == null) "Tambah gambar / GIF" else "Ganti gambar / GIF"
            setOnClickListener { pick("image/*", PICK_IMAGE, false) }
        })
        root.addView(TextView(this).apply { text = if (profile.imageUri == null) "Belum ada gambar" else "Gambar tersimpan" })
        root.addView(Button(this).apply {
            text = if (profile.videoUri == null) "Tambah video" else "Ganti video"
            setOnClickListener { pick("video/*", PICK_VIDEO, false) }
        })
        root.addView(TextView(this).apply { text = if (profile.videoUri == null) "Belum ada video" else "Video tersimpan (loop)" })
        root.addView(Button(this).apply {
            text = "Atur Web Overlay"
            setOnClickListener { editWeb() }
        })
        root.addView(TextView(this).apply { text = profile.webUrl ?: "Belum ada URL web" })
        root.addView(Button(this).apply {
            text = "Pilih playlist MP3"
            setOnClickListener { pick("audio/mpeg", PICK_AUDIO, true) }
        })
        root.addView(TextView(this).apply { text = "Jumlah backsound: ${profile.audioUris.size} file" })
        repeatCheck = CheckBox(this).apply {
            text = "Ulangi playlist otomatis"
            isChecked = profile.repeatAudio
        }
        root.addView(repeatCheck)

        root.addView(Button(this).apply {
            text = "Simpan layout"
            setOnClickListener {
                saveEditorFields()
                status.text = "Layout tersimpan."
            }
        })
        root.addView(Button(this).apply {
            text = "START OVERLAY"
            setOnClickListener {
                saveEditorFields()
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    status.text = "Izinkan tampil di atas aplikasi lain, lalu tekan START lagi."
                } else {
                    val serviceIntent = Intent(this@MainActivity, OverlayService::class.java)
                        .setAction(OverlayService.ACTION_START)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
                    status.text = "Overlay berjalan. Kontrol tersedia di notification bar."
                }
            }
        })
        status = TextView(this).apply {
            text = "Portrait dan Landscape menyimpan konfigurasi masing-masing."
            setPadding(0, 12, 0, 12)
        }
        root.addView(status)
        setContentView(scroll)
    }

    private fun section(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 18f
            setPadding(0, 18, 0, 8)
        })
    }

    private fun seek(root: LinearLayout, label: String, initial: Float, changed: (Float) -> Unit) {
        root.addView(TextView(this).apply { text = label })
        root.addView(SeekBar(this).apply {
            max = 100
            progress = (initial * 100).toInt()
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
        HudProfiles.save(this, editingLandscape, change(HudProfiles.load(this, editingLandscape)))
    }

    private fun saveEditorFields() {
        val old = HudProfiles.load(this, editingLandscape)
        HudProfiles.save(this, editingLandscape, old.copy(
            label = labelInput.text.toString().ifBlank { "HUD Overlay" },
            opacity = 0.1f + opacitySeek.progress / 100f,
            repeatAudio = repeatCheck.isChecked
        ))
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
