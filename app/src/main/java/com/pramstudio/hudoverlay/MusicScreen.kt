package com.pramstudio.hudoverlay

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MusicScreen(
    canDraw: Boolean,
    onNeedPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val music by MusicBus.state.collectAsState()
    var tracks by remember { mutableStateOf(MusicStore.tracks(context)) }
    var loop by remember { mutableStateOf(MusicStore.repeat(context)) }
    var volume by remember { mutableStateOf(MusicStore.volume(context)) }

    fun save(list: List<Track>) {
        tracks = list
        MusicStore.saveTracks(context, list)
    }

    fun control(action: String, index: Int = 0) {
        if (tracks.isEmpty()) return
        if (!canDraw) {
            onNeedPermission()
        } else {
            OverlayService.sendMusic(context, action, index)
        }
    }

    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val added = uris.map { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // izin permanen tidak tersedia untuk file ini
                }
                Track(uri.toString(), Media.displayName(context, uri))
            }
            save(tracks + added.filter { t -> tracks.none { it.uri == t.uri } })
        }
    }

    val currentName = tracks.getOrNull(music.index)?.name
    val subtitle = if (tracks.isEmpty()) {
        "Tambahkan lagu untuk mulai"
    } else {
        "Lagu ${music.index + 1} dari ${tracks.size}" + (if (loop) " - auto repeat" else "")
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Ui.Orange, Ui.OrangeDark)))
                    .padding(20.dp)
            ) {
                Text("Sedang diputar", color = Color(0xCCFFFFFF), fontSize = 12.sp)
                Text(
                    currentName ?: "Belum ada lagu",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(subtitle, color = Color(0xCCFFFFFF), fontSize = 13.sp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphButton(Glyph.PREV, Color(0x33FFFFFF), Color.White, 48.dp) {
                        control(OverlayService.ACTION_MUSIC_PREV)
                    }
                    Spacer(Modifier.width(16.dp))
                    GlyphButton(
                        if (music.playing) Glyph.PAUSE else Glyph.PLAY,
                        Color.White,
                        Ui.OrangeDark,
                        64.dp
                    ) {
                        control(OverlayService.ACTION_MUSIC_TOGGLE)
                    }
                    Spacer(Modifier.width(16.dp))
                    GlyphButton(Glyph.NEXT, Color(0x33FFFFFF), Color.White, 48.dp) {
                        control(OverlayService.ACTION_MUSIC_NEXT)
                    }
                }
            }
        }

        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Auto repeat")
                        Text(
                            "Ulangi playlist dari awal setelah lagu terakhir",
                            color = Ui.Muted,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = loop,
                        onCheckedChange = {
                            loop = it
                            MusicStore.setRepeat(context, it)
                        }
                    )
                }
                SectionLabel("Volume musik")
                StepSlider(
                    value = volume,
                    onChange = {
                        volume = it
                        MusicStore.setVolume(context, it)
                    },
                    range = 0f..1f
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    SectionLabel("Playlist (${tracks.size})")
                }
                if (tracks.isNotEmpty()) {
                    SoftChip("Kosongkan", onClick = { save(emptyList()) }, danger = true)
                }
                Pill("+ Tambah lagu", true, { pickAudio.launch(arrayOf("audio/*")) })
            }
        }

        if (tracks.isEmpty()) {
            item {
                Text(
                    "Playlist masih kosong. Ketuk Tambah lagu untuk memilih file audio dari HP.",
                    color = Ui.Muted,
                    fontSize = 13.sp
                )
            }
        }

        itemsIndexed(tracks) { i, t ->
            val active = i == music.index && music.playing
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) Ui.OrangeSoft else Color.White)
                    .clickable { control(OverlayService.ACTION_MUSIC_PLAY_INDEX, i) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${i + 1}", color = Ui.Muted, modifier = Modifier.width(28.dp))
                Text(
                    t.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Ui.Ink,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    "X",
                    color = Ui.Muted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { save(tracks.filterIndexed { idx, _ -> idx != i }) }
                        .padding(8.dp)
                )
            }
        }

        item {
            Text(
                "Memutar musik juga menyalakan overlay. Tombol Stop di atas menghentikan keduanya.",
                color = Ui.Muted,
                fontSize = 12.sp
            )
        }
    }
}
