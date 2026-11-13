package com.pramstudio.hudoverlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Warna dan komponen kecil yang dipakai di semua layar (gaya terang dengan aksen oranye). */
object Ui {
    var Orange by mutableStateOf(Color(0xFFFF9800))
        private set
    var OrangeDark by mutableStateOf(Color(0xFFF57C00))
        private set
    var OrangeSoft by mutableStateOf(Color(0xFFFFE9C7))
        private set

    fun setAccent(argb: Int) {
        Orange = Color(argb)
        OrangeDark = Color(argb).copy(red = (Color(argb).red * 0.82f).coerceIn(0f, 1f))
        OrangeSoft = Color(argb).copy(alpha = 0.20f)
    }
    val Bg = Color(0xFFF4F4F6)
    val Ink = Color(0xFF212121)
    val Muted = Color(0xFF757575)
    val Line = Color(0xFFBDBDBD)
}

@Composable
fun HudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ui.Orange,
            onPrimary = Color.White,
            primaryContainer = Ui.OrangeSoft,
            onPrimaryContainer = Ui.Ink,
            background = Ui.Bg,
            onBackground = Ui.Ink,
            surface = Color.White,
            onSurface = Ui.Ink,
            surfaceVariant = Color(0xFFEDEDED),
            onSurfaceVariant = Ui.Muted,
            outline = Ui.Line
        ),
        content = content
    )
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg = if (selected) Ui.Orange else Color(0xFFEDEDED)
    val fg = if (selected) Color.White else Ui.Ink
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun SoftChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false
) {
    val bg = if (danger) Color(0xFFFFEBEE) else Ui.OrangeSoft
    val fg = if (danger) Color(0xFFC62828) else Ui.OrangeDark
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = Ui.Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
}

@Composable
fun RoundButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Ui.OrangeSoft)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Ui.OrangeDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

/** Slider dengan tombol - dan + di kedua ujung, seperti editor DU Recorder. */
@Composable
fun StepSlider(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val step = (range.endInclusive - range.start) / 20f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RoundButton("-") { onChange((value - step).coerceIn(range.start, range.endInclusive)) }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        )
        RoundButton("+") { onChange((value + step).coerceIn(range.start, range.endInclusive)) }
    }
}

enum class Glyph { PLAY, PAUSE, NEXT, PREV }

@Composable
fun MediaGlyph(glyph: Glyph, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (glyph) {
            Glyph.PLAY -> {
                val p = Path()
                p.moveTo(w * 0.28f, h * 0.15f)
                p.lineTo(w * 0.86f, h * 0.5f)
                p.lineTo(w * 0.28f, h * 0.85f)
                p.close()
                drawPath(p, tint)
            }
            Glyph.PAUSE -> {
                drawRect(tint, Offset(w * 0.22f, h * 0.15f), Size(w * 0.19f, h * 0.7f))
                drawRect(tint, Offset(w * 0.59f, h * 0.15f), Size(w * 0.19f, h * 0.7f))
            }
            Glyph.NEXT -> {
                val p = Path()
                p.moveTo(w * 0.15f, h * 0.18f)
                p.lineTo(w * 0.68f, h * 0.5f)
                p.lineTo(w * 0.15f, h * 0.82f)
                p.close()
                drawPath(p, tint)
                drawRect(tint, Offset(w * 0.74f, h * 0.18f), Size(w * 0.12f, h * 0.64f))
            }
            Glyph.PREV -> {
                val p = Path()
                p.moveTo(w * 0.85f, h * 0.18f)
                p.lineTo(w * 0.32f, h * 0.5f)
                p.lineTo(w * 0.85f, h * 0.82f)
                p.close()
                drawPath(p, tint)
                drawRect(tint, Offset(w * 0.14f, h * 0.18f), Size(w * 0.12f, h * 0.64f))
            }
        }
    }
}

@Composable
fun GlyphButton(glyph: Glyph, bg: Color, tint: Color, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        MediaGlyph(glyph, tint, Modifier.size(size * 0.5f))
    }
}
