package com.pramstudio.hudoverlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catatan singkat kejadian Web Overlay (muat halaman, error, hasil pemeriksaan isi halaman)
 * untuk diagnosa kalau widget tidak tampil. Hanya disimpan di memori dan ditampilkan di Pengaturan.
 */
object WebDiag {
    private const val MAX_LINES = 60

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun add(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _lines.value = (_lines.value + "$time  $message").takeLast(MAX_LINES)
    }

    fun clear() {
        _lines.value = emptyList()
    }
}
