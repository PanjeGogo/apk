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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private var canDraw by mutableStateOf(false)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Overlay tetap jalan walau izin notifikasi ditolak.
            OverlayService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    val running by OverlayService.running.collectAsState()
                    val initial = remember { HudStore.load(this@MainActivity) }

                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!canDraw) {
                            Text("Izin \"Tampil di atas aplikasi lain\" belum aktif.")
                            Button(
                                onClick = { openOverlaySettings() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Buka pengaturan izin") }
                        } else {
                            Button(
                                onClick = {
                                    if (running) OverlayService.stop(this@MainActivity)
                                    else requestStart()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(if (running) "Stop overlay" else "Mulai overlay") }
                        }

                        EditorScreen(
                            initial = initial,
                            onSave = { HudStore.save(this@MainActivity, it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
