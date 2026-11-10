package com.pramstudio.hudoverlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt

private val palette =
    listOf(0xFFFFFFFF, 0xFFFFEB3B, 0xFF4FC3F7, 0xFFFF5252, 0xFF69F0AE).map { it.toInt() }

@Composable
fun EditorScreen(
    initial: HudLayout,
    onSave: (HudLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    var layout by remember { mutableStateOf(initial) }
    var profile by remember { mutableStateOf(Profile.PORTRAIT) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf(false) }

    // Simpan dengan jeda singkat supaya tidak menulis tiap frame saat menggeser.
    LaunchedEffect(layout) {
        delay(300)
        onSave(layout)
    }

    fun update(id: Long, f: (HudItem) -> HudItem) {
        layout = layout.withItems(
            profile,
            layout.items(profile).map { if (it.id == id) f(it) else it }
        )
    }

    fun move(id: Long, delta: Int) {
        val list = layout.items(profile).toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        layout = layout.withItems(profile, list)
    }

    fun add() {
        val item = HudItem(
            id = System.nanoTime(),
            text = "Teks baru",
            x = 0.1f,
            y = 0.1f,
            size = 0.06f,
            alpha = 1f,
            color = 0xFFFFFFFF.toInt()
        )
        layout = layout.withItems(profile, layout.items(profile) + item)
        selectedId = item.id
    }

    fun remove(id: Long) {
        layout = layout.withItems(profile, layout.items(profile).filter { it.id != id })
        selectedId = null
    }

    val items = layout.items(profile)
    val selected = items.firstOrNull { it.id == selectedId }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileButton("Portrait", profile == Profile.PORTRAIT) {
                profile = Profile.PORTRAIT
                selectedId = null
            }
            ProfileButton("Landscape", profile == Profile.LANDSCAPE) {
                profile = Profile.LANDSCAPE
                selectedId = null
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { add() }) { Text("+ Teks") }
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val ratio = if (profile == Profile.PORTRAIT) 9f / 16f else 16f / 9f
            val canvasW = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
            val canvasH = canvasW / ratio

            BoxWithConstraints(
                Modifier
                    .size(canvasW, canvasH)
                    .background(Color(0xFF263238))
                    .pointerInput(Unit) { detectTapGestures { selectedId = null } }
            ) {
                val wPx = constraints.maxWidth.toFloat()
                val hPx = constraints.maxHeight.toFloat()
                items.forEach { item ->
                    key(item.id) {
                        EditorItem(
                            item = item,
                            wPx = wPx,
                            hPx = hPx,
                            selected = item.id == selectedId,
                            onSelect = { selectedId = item.id },
                            onDrag = { dx, dy ->
                                update(item.id) {
                                    it.copy(
                                        x = (it.x + dx).coerceIn(0f, 0.95f),
                                        y = (it.y + dy).coerceIn(0f, 0.95f)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        if (selected == null) {
            Text(
                "Seret teks untuk memindah, ketuk untuk memilih.",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text("Ukuran", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = selected.size,
                onValueChange = { v -> update(selected.id) { it.copy(size = v) } },
                valueRange = 0.02f..0.15f
            )
            Text("Opacity", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = selected.alpha,
                onValueChange = { v -> update(selected.id) { it.copy(alpha = v) } },
                valueRange = 0.1f..1f
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                palette.forEach { c ->
                    Spacer(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(c))
                            .then(
                                if (c == selected.color) Modifier.border(2.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .clickable { update(selected.id) { it.copy(color = c) } }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { editing = true }) { Text("Edit") }
                OutlinedButton(onClick = { move(selected.id, 1) }) { Text("Naik") }
                OutlinedButton(onClick = { move(selected.id, -1) }) { Text("Turun") }
                OutlinedButton(onClick = { remove(selected.id) }) { Text("Hapus") }
            }
        }
    }

    if (editing && selected != null) {
        var text by remember(selected.id) { mutableStateOf(selected.text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit teks") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }) },
            confirmButton = {
                TextButton(onClick = {
                    update(selected.id) { it.copy(text = text) }
                    editing = false
                }) { Text("Simpan") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ProfileButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun EditorItem(
    item: HudItem,
    wPx: Float,
    hPx: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    val density = LocalDensity.current
    val minSide = min(wPx, hPx)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnDrag by rememberUpdatedState(onDrag)

    Text(
        text = item.text,
        color = Color(item.color).copy(alpha = item.alpha),
        fontSize = with(density) { (item.size * minSide).toSp() },
        softWrap = false,
        modifier = Modifier
            .offset { IntOffset((item.x * wPx).roundToInt(), (item.y * hPx).roundToInt()) }
            .then(if (selected) Modifier.border(1.dp, Color(0xFF4FC3F7)) else Modifier)
            .pointerInput(item.id, wPx, hPx) {
                detectDragGestures(
                    onDragStart = { currentOnSelect() },
                    onDrag = { change, amount ->
                        change.consume()
                        currentOnDrag(amount.x / wPx, amount.y / hPx)
                    }
                )
            }
            .pointerInput(item.id) {
                detectTapGestures(onTap = { currentOnSelect() })
            }
    )
}
