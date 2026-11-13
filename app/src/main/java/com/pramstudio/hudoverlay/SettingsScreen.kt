package com.pramstudio.hudoverlay

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val appColors = listOf(
    "Orange" to 0xFFFF9800.toInt(),
    "Blue" to 0xFF2196F3.toInt(),
    "Purple" to 0xFF9C27B0.toInt(),
    "Green" to 0xFF4CAF50.toInt(),
    "Red" to 0xFFE53935.toInt(),
    "Pink" to 0xFFE91E63.toInt(),
    "Cyan" to 0xFF00BCD4.toInt()
)

/** Jalankan export/import dan ubah kegagalan (termasuk kehabisan memori) jadi pesan untuk pengguna. */
private fun runArchive(block: () -> Unit): String? = try {
    block()
    null
} catch (e: OutOfMemoryError) {
    "memori perangkat tidak cukup untuk file sebesar ini"
} catch (e: Exception) {
    e.message ?: "kesalahan tidak diketahui"
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appContext = remember { context.applicationContext }
    val prefs = remember { HudStore.prefs(context) }
    var selectedColor by remember { mutableStateOf(prefs.getInt("app_color", 0xFFFF9800.toInt())) }
    var menu by remember { mutableStateOf(false) }
    // Tetap hidup walau pengguna pindah tab saat export/import berjalan, supaya hasilnya tetap dilaporkan.
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) scope.launch {
            val error = withContext(Dispatchers.IO) {
                runArchive { ConfigArchive.export(appContext, uri) }
            }
            Toast.makeText(
                appContext,
                if (error == null) "Config berhasil diekspor" else "Export gagal: $error",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) scope.launch {
            val error = withContext(Dispatchers.IO) {
                val before = HudStore.load(appContext)
                val err = runArchive { ConfigArchive.import(appContext, uri) }
                if (err == null) {
                    // Gambar dari konfigurasi lama yang tidak dipakai lagi dihapus supaya tidak menumpuk.
                    val after = HudStore.load(appContext)
                    (before.portrait + before.landscape)
                        .filter { it.type == ItemType.IMAGE }
                        .forEach { Media.deleteIfUnused(it.path, after) }
                }
                err
            }
            Toast.makeText(
                appContext,
                if (error == null) "Config berhasil diimpor" else "Import gagal: $error",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Column(
        modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Color.White).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Ui.Orange)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text("OA", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
            }
            Text("Overlay aja", color = Ui.Ink, fontWeight = FontWeight.Bold, fontSize = 21.sp)
            Text("By PanjeGogo", color = Ui.Muted, fontSize = 13.sp)
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("App Color", color = Ui.Ink, fontWeight = FontWeight.Bold)
            Box {
                OutlinedButton(onClick = { menu = true }) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color(selectedColor)).padding(7.dp))
                    Text(appColors.firstOrNull { it.second == selectedColor }?.first ?: "Custom")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    appColors.forEach { (name, color) ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color(color)).padding(9.dp))
                                    Text(name)
                                }
                            },
                            onClick = {
                                selectedColor = color
                                prefs.edit().putInt("app_color", color).apply()
                                Ui.setAccent(color)
                                menu = false
                            }
                        )
                    }
                }
            }
            Text("Warna default aplikasi adalah Orange.", color = Ui.Muted, fontSize = 12.sp)
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Color.White).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Konfigurasi Overlay", color = Ui.Ink, fontWeight = FontWeight.Bold)
            Text("Ekspor atau impor layout Portrait dan Landscape beserta gambar/GIF dalam format project khusus Overlay aja.", color = Ui.Muted, fontSize = 12.sp)
            OutlinedButton(
                onClick = { exportPicker.launch("OverlayAja-Config.panje") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Export Config (.panje)") }
            OutlinedButton(
                onClick = { importPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Import Config (.panje)") }
            Text("File .panje terenkripsi dan hanya dapat dibaca melalui fitur Import Overlay aja. Import mengganti konfigurasi yang tersimpan.", color = Color(0xFFB45309), fontSize = 11.sp)
            Text("Hati-hati: URL Web Overlay (sering berisi token alert) ikut tersimpan di file ini dan kuncinya tertanam di aplikasi, jadi jangan bagikan file .panje ke orang yang tidak dipercaya.", color = Color(0xFFB45309), fontSize = 11.sp)
        }
    }
}
