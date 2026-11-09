package com.pramstudio.hudoverlay

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    private var editingLandscape = false
    private lateinit var labelInput: EditText
    private lateinit var opacitySeek: SeekBar
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        showEditor(false)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)
    }

    private fun showEditor(landscape: Boolean) {
        editingLandscape = landscape
        val profile = HudProfiles.load(this, landscape)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 32, 24, 24)
        }

        val title = TextView(this).apply {
            text = "HUD Overlay Editor"
            textSize = 25f
            setPadding(0, 0, 0, 18)
        }
        root.addView(title)

        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val portrait = Button(this).apply {
            text = if (!landscape) "✓ Portrait" else "Portrait"
            setOnClickListener { showEditor(false) }
        }
        val horizontal = Button(this).apply {
            text = if (landscape) "✓ Landscape" else "Landscape"
            setOnClickListener { showEditor(true) }
        }
        modeRow.addView(portrait, LinearLayout.LayoutParams(0, -2, 1f))
        modeRow.addView(horizontal, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(modeRow)

        root.addView(TextView(this).apply {
            text = if (landscape) "Mengedit layout horizontal (16:9)" else "Mengedit layout vertikal (9:16)"
            textSize = 14f
            setPadding(0, 18, 0, 8)
        })

        labelInput = EditText(this).apply {
            setSingleLine(true)
            hint = "Nama / teks overlay"
            setText(profile.label)
        }
        root.addView(labelInput, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        root.addView(TextView(this).apply {
            text = "Posisi horizontal"
            setPadding(0, 14, 0, 0)
        })
        addPositionControl(root, profile.xPercent, true)
        root.addView(TextView(this).apply {
            text = "Posisi vertikal"
            setPadding(0, 10, 0, 0)
        })
        addPositionControl(root, profile.yPercent, false)

        root.addView(TextView(this).apply {
            text = "Transparansi"
            setPadding(0, 12, 0, 0)
        })
        opacitySeek = SeekBar(this).apply {
            max = 90
            progress = ((profile.opacity - 0.1f) * 100).toInt().coerceIn(0, 90)
        }
        root.addView(opacitySeek)

        val save = Button(this).apply {
            text = "Simpan layout"
            setOnClickListener {
                val current = HudProfiles.load(this@MainActivity, editingLandscape)
                HudProfiles.save(this@MainActivity, editingLandscape, current.copy(
                    label = labelInput.text.toString().ifBlank { "HUD Overlay" },
                    opacity = 0.1f + opacitySeek.progress / 100f
                ))
                status.text = "Layout ${if (editingLandscape) "Landscape" else "Portrait"} tersimpan."
            }
        }
        root.addView(save)

        val start = Button(this).apply {
            text = "START OVERLAY"
            setOnClickListener {
                save.performClick()
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                    status.text = "Izinkan tampil di atas aplikasi lain, lalu tekan START lagi."
                } else {
                    val intent = Intent(this@MainActivity, OverlayService::class.java)
                        .setAction(OverlayService.ACTION_START)
                    if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                    status.text = "Overlay berjalan. Kontrol tersedia di notification bar."
                }
            }
        }
        root.addView(start)

        status = TextView(this).apply {
            text = "Pilih mode dan atur HUD sebelum memulai."
            textSize = 13f
            setPadding(0, 14, 0, 0)
        }
        root.addView(status)
        setContentView(root)
    }

    private fun addPositionControl(root: LinearLayout, initial: Float, horizontal: Boolean) {
        val seek = SeekBar(this).apply {
            max = 100
            progress = (initial * 100).toInt()
        }
        root.addView(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                val current = HudProfiles.load(this@MainActivity, editingLandscape)
                val updated = if (horizontal) current.copy(xPercent = value / 100f)
                    else current.copy(yPercent = value / 100f)
                HudProfiles.save(this@MainActivity, editingLandscape, updated)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
    }
}
