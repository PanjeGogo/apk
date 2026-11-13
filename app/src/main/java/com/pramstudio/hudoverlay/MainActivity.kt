package com.pramstudio.hudoverlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private var canDraw by mutableStateOf(false)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Overlay tetap jalan walau izin notifikasi ditolak.
            OverlayService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.OrangeDark.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        setContent {
            HudTheme {
                val running by OverlayService.running.collectAsState()
                var tab by remember { mutableStateOf(0) }
                androidx.compose.runtime.LaunchedEffect(Unit) { Ui.setAccent(HudStore.prefs(this@MainActivity).getInt("app_color", 0xFFFF9800.toInt())) }
                var profile by remember { mutableStateOf(Profile.PORTRAIT) }
                var showInfo by remember { mutableStateOf(false) }
                var showPatchNotes by remember { mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val prefs = HudStore.prefs(this@MainActivity)
                    if (prefs.getString("shown_patch_version", "") != "2.2.0") {
                        prefs.edit().putString("shown_patch_version", "2.2.0").apply()
                        showPatchNotes = true
                    }
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .background(Ui.Bg)
                ) {
                    TopBar(running) {
                        if (running) {
                            OverlayService.stop(this@MainActivity)
                        } else if (!canDraw) {
                            showInfo = true
                        } else {
                            requestStart()
                        }
                    }
                    TabBar(tab) { tab = it }
                    if (!canDraw) {
                        PermissionBanner { showInfo = true }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (tab == 0) {
                            EditorScreen(
                                profile = profile,
                                onProfileChange = { profile = it },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (tab == 1) {
                            MusicScreen(
                                canDraw = canDraw,
                                onNeedPermission = { showInfo = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            SettingsScreen(modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                if (showPatchNotes) {
                    AlertDialog(
                        onDismissRequest = { showPatchNotes = false },
                        title = {
                            Column {
                                Text("Overlay aja v2.2.0")
                                Text(
                                    buildAnnotatedString {
                                        append("By: ")
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("PanjeGogo")
                                        }
                                    },
                                    fontSize = 13.sp,
                                    color = Ui.Muted
                                )
                            }
                        },
                        text = {
                            Column(
                                Modifier
                                    .height(320.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text("VERSI 2.1.1 • TERBARU", fontWeight = FontWeight.Bold)
                                Text(
                                    "• Ukuran elemen memakai input angka dengan tombol minus dan plus\\n" +
                                        "• Pilihan berbagai jenis font untuk text overlay\\n" +
                                        "• Pemilih warna teks menggunakan kode HEX\\n" +
                                        "• Pengaturan font dan warna disimpan per elemen"
                                )
                                Text(
                                    "VERSI 2.1.0 • SEBELUMNYA",
                                    modifier = Modifier.padding(top = 16.dp),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "• Web Overlay dengan JavaScript aktif\\n" +
                                        "• Background WebView transparan untuk alert donasi\\n" +
                                        "• Sentuhan tetap diteruskan ke game\\n" +
                                        "• URL alert dapat diatur dari editor"
                                )
                                Text(
                                    "VERSI 0.4.0 • VERSI AWAL",
                                    modifier = Modifier.padding(top = 16.dp),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "• Editor overlay teks dan gambar\\n" +
                                        "• Pengaturan posisi, ukuran, opacity, warna, dan layer\\n" +
                                        "• Dukungan gambar/GIF dan pemutar musik\\n" +
                                        "• Pengaturan profil Portrait dan Landscape"
                                )
                            }
                        },
                        confirmButton = { TextButton(onClick = { showPatchNotes = false }) { Text("Mengerti") } }
                    )
                }

                if (showInfo) {
                    AlertDialog(
                        onDismissRequest = { showInfo = false },
                        title = { Text("Izin tampil di atas aplikasi lain") },
                        text = {
                            Text(
                                "Izin ini hanya dipakai untuk menampilkan teks, gambar, dan halaman web overlay " +
                                    "di atas game atau app lain. App ini tidak membaca layar, sentuhan, " +
                                    "atau isi aplikasi lain. Di halaman berikutnya Android akan " +
                                    "menampilkan peringatan umum. Itu peringatan standar untuk semua " +
                                    "app overlay."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showInfo = false
                                openOverlaySettings()
                            }) { Text("Lanjut") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showInfo = false }) { Text("Batal") }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        canDraw = Settings.canDrawOverlays(this)
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun requestStart() {
        val needsNotifPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsNotifPermission) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            OverlayService.start(this)
        }
    }
}

@Composable
private fun TopBar(running: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ui.Orange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Overlay aja", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                if (running) "Overlay aktif" else "Overlay mati",
                color = Color(0xCCFFFFFF),
                fontSize = 12.sp
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .clickable(onClick = onToggle)
                .padding(horizontal = 24.dp, vertical = 10.dp)
        ) {
            Text(
                if (running) "Stop" else "Mulai",
                color = if (running) Color(0xFFD32F2F) else Ui.OrangeDark,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("Editor", "Musik", "Pengaturan")
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        labels.forEachIndexed { i, label ->
            Column(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label,
                    color = if (i == selected) Ui.OrangeDark else Ui.Muted,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(if (i == selected) Ui.Orange else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ui.OrangeSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Izin overlay belum aktif. Ketuk untuk mengaktifkan.",
            color = Ui.OrangeDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}
