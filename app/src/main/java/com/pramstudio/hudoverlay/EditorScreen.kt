package com.pramstudio.hudoverlay

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private val palette = listOf(
    0xFFFFFFFF, 0xFFCFCFCF, 0xFF8C8C8C, 0xFF000000, 0xFF8D5524, 0xFF8B0000,
    0xFFE53935, 0xFFFF5722, 0xFFFF9800, 0xFFFFEB3B, 0xFFCDDC39, 0xFF4CAF50,
    0xFF00BCD4, 0xFF2196F3, 0xFF3F51B5, 0xFF9C27B0, 0xFFE91E63, 0xFFF8BBD0
).map { it.toInt() }

@Composable
fun EditorScreen(
    profile: Profile,
    onProfileChange: (Profile) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var layout by remember { mutableStateOf(HudStore.load(context)) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf(false) }
    var replaceId by remember { mutableStateOf<Long?>(null) }

    // Simpan dengan jeda singkat supaya tidak menulis tiap frame saat menggeser,
    // dan simpan sekali lagi saat layar ditutup (misalnya pindah tab).
    val latestLayout by rememberUpdatedState(layout)
    LaunchedEffect(layout) {
        delay(300)
        HudStore.save(context, layout)
    }
    DisposableEffect(Unit) {
        onDispose { HudStore.save(context, latestLayout) }
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

    fun addText() {
        val n = layout.items(profile).size
        val item = HudItem(
            id = System.nanoTime(),
            type = ItemType.TEXT,
            text = "Teks baru",
            x = 0.08f + 0.03f * (n % 5),
            y = 0.08f + 0.05f * (n % 5),
            size = 0.06f,
            alpha = 1f,
            color = 0xFFFFFFFF.toInt()
        )
        layout = layout.withItems(profile, layout.items(profile) + item)
        selectedId = item.id
    }

    fun addImage(path: String) {
        val n = layout.items(profile).size
        val item = HudItem(
            id = System.nanoTime(),
            type = ItemType.IMAGE,
            path = path,
            x = 0.1f + 0.03f * (n % 5),
            y = 0.2f + 0.05f * (n % 5),
            size = 0.3f,
            alpha = 1f,
            color = 0xFFFFFFFF.toInt()
        )
        layout = layout.withItems(profile, layout.items(profile) + item)
        selectedId = item.id
    }

    fun replaceImage(id: Long, path: String) {
        val old = layout.items(profile).firstOrNull { it.id == id }
        update(id) { it.copy(path = path) }
        if (old != null) Media.deleteIfUnused(old.path, layout)
    }

    fun remove(id: Long) {
        val old = layout.items(profile).firstOrNull { it.id == id }
        layout = layout.withItems(profile, layout.items(profile).filter { it.id != id })
        selectedId = null
        if (old != null && old.type == ItemType.IMAGE) Media.deleteIfUnused(old.path, layout)
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val target = replaceId
            replaceId = null
            scope.launch {
                val path = withContext(Dispatchers.IO) { Media.importImage(context, uri) }
                if (path != null) {
                    if (target == null) addImage(path) else replaceImage(target, path)
                }
            }
        }
    }

    val items = layout.items(profile)
    val selected = items.firstOrNull { it.id == selectedId }

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Pill("Portrait", profile == Profile.PORTRAIT, {
                selectedId = null
                onProfileChange(Profile.PORTRAIT)
            })
            Pill("Landscape", profile == Profile.LANDSCAPE, {
                selectedId = null
                onProfileChange(Profile.LANDSCAPE)
            })
            Spacer(Modifier.weight(1f))
            Text("${items.size} elemen", color = Ui.Muted, fontSize = 12.sp)
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (maxWidth > 40.dp && maxHeight > 40.dp) {
                val ratio = if (profile == Profile.PORTRAIT) 9f / 16f else 16f / 9f
                val canvasW = if (maxWidth / maxHeight > ratio) maxHeight * ratio else maxWidth
                val canvasH = canvasW / ratio

                BoxWithConstraints(
                    Modifier
                        .size(canvasW, canvasH)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF455A64), Color(0xFF1C262B))
                            )
                        )
                        .pointerInput(Unit) { detectTapGestures { selectedId = null } }
                ) {
                    val wPx = constraints.maxWidth.toFloat()
                    val hPx = constraints.maxHeight.toFloat()
                    items.forEach { item ->
                        key(item.id) {
                            CanvasItem(
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
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color.White,
            shadowElevation = 12.dp
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("+ Teks", true, { addText() }, Modifier.weight(1f))
                    SoftChip(
                        "+ Gambar / GIF",
                        onClick = {
                            replaceId = null
                            pickImage.launch(arrayOf("image/*"))
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (selected == null) {
                    Text(
                        "Ketuk elemen di preview untuk memilih, seret untuk memindah.",
                        color = Ui.Muted,
                        fontSize = 13.sp
                    )
                } else {
                    val isText = selected.type == ItemType.TEXT

                    if (isText) {
                        SectionLabel("Teks")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                selected.text,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Ui.Ink
                            )
                            SoftChip("Edit", onClick = { editing = true })
                        }
                        SectionLabel("Warna")
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            palette.forEach { c ->
                                val sel = c == selected.color
                                Box(
                                    Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .border(
                                            if (sel) 3.dp else 1.dp,
                                            if (sel) Ui.Orange else Ui.Line,
                                            CircleShape
                                        )
                                        .clickable { update(selected.id) { it.copy(color = c) } }
                                )
                            }
                        }
                    } else {
                        SectionLabel("Gambar / GIF")
                        SoftChip(
                            "Ganti gambar",
                            onClick = {
                                replaceId = selected.id
                                pickImage.launch(arrayOf("image/*"))
                            }
                        )
                    }

                    SectionLabel("Ukuran")
                    StepSlider(
                        value = selected.size,
                        onChange = { v -> update(selected.id) { it.copy(size = v) } },
                        range = if (isText) 0.02f..0.15f else 0.08f..0.9f
                    )
                    SectionLabel("Opacity")
                    StepSlider(
                        value = selected.alpha,
                        onChange = { v -> update(selected.id) { it.copy(alpha = v) } },
                        range = 0.1f..1f
                    )
                    SectionLabel("Layer")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftChip("Naik", onClick = { move(selected.id, 1) })
                        SoftChip("Turun", onClick = { move(selected.id, -1) })
                        SoftChip("Hapus", onClick = { remove(selected.id) }, danger = true)
                    }
                }
            }
        }
    }

    if (editing && selected != null && selected.type == ItemType.TEXT) {
        var text by remember(selected.id) { mutableStateOf(selected.text) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit teks") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true
                )
            },
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
private fun CanvasItem(
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

    val gestures = Modifier
        .offset { IntOffset((item.x * wPx).roundToInt(), (item.y * hPx).roundToInt()) }
        .then(if (selected) Modifier.border(1.5.dp, Ui.Orange) else Modifier)
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

    if (item.type == ItemType.TEXT) {
        Text(
            text = item.text,
            color = Color(item.color).copy(alpha = item.alpha),
            fontSize = with(density) { (item.size * minSide).toSp() },
            softWrap = false,
            modifier = gestures
        )
    } else {
        val widthDp = with(density) { (item.size * minSide).toDp() }
        val preview by produceState<ImageBitmap?>(null, item.path) {
            value = withContext(Dispatchers.IO) {
                Media.decodePreview(item.path)?.asImageBitmap()
            }
        }
        val bmp = preview
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                alpha = item.alpha,
                modifier = Modifier.width(widthDp).then(gestures)
            )
        } else {
            Box(
                Modifier
                    .size(widthDp)
                    .background(Color(0x33FFFFFF))
                    .then(gestures)
            )
        }
    }
}
