package com.pramstudio.hudoverlay

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * Pemutar playlist. Lagu diputar berurutan; kalau auto repeat aktif,
 * setelah lagu terakhir kembali ke lagu pertama. Lagu yang gagal dibuka dilewati.
 */
class MusicPlayer(private val ctx: Context, private val onChanged: () -> Unit) {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var prepared = false
    private var tracks: List<Track> = MusicStore.tracks(ctx)
    private var index = 0
    private var wantPlaying = false
    private var failures = 0

    val isPlaying: Boolean get() = wantPlaying

    fun toggle() {
        if (wantPlaying) pause() else play()
    }

    fun play() {
        if (tracks.isEmpty()) {
            publish()
            return
        }
        wantPlaying = true
        val p = player
        if (p == null) {
            startTrack(index)
        } else {
            if (prepared) {
                try {
                    p.start()
                } catch (e: IllegalStateException) {
                    startTrack(index)
                    return
                }
            }
            publish()
        }
    }

    fun pause() {
        wantPlaying = false
        val p = player
        if (p != null && prepared) {
            try {
                if (p.isPlaying) p.pause()
            } catch (e: IllegalStateException) {
                // abaikan
            }
        }
        publish()
    }

    fun next() {
        if (tracks.isEmpty()) return
        wantPlaying = true
        startTrack((index + 1) % tracks.size)
    }

    fun prev() {
        if (tracks.isEmpty()) return
        wantPlaying = true
        startTrack((index - 1 + tracks.size) % tracks.size)
    }

    fun playIndex(i: Int) {
        if (tracks.isEmpty()) return
        wantPlaying = true
        startTrack(i)
    }

    /** Dipanggil saat playlist diubah dari UI. */
    fun onTracksChanged() {
        val currentUri = tracks.getOrNull(index)?.uri
        tracks = MusicStore.tracks(ctx)
        if (tracks.isEmpty()) {
            wantPlaying = false
            releasePlayer()
            index = 0
        } else {
            val pos = tracks.indexOfFirst { it.uri == currentUri }
            if (pos >= 0) {
                index = pos
            } else if (player != null) {
                // lagu yang sedang aktif dihapus
                if (wantPlaying) {
                    startTrack(index.coerceAtMost(tracks.size - 1))
                    return
                }
                releasePlayer()
                index = index.coerceAtMost(tracks.size - 1)
            } else {
                index = index.coerceAtMost(tracks.size - 1)
            }
        }
        publish()
    }

    fun applyVolume() {
        val p = player
        if (p != null && prepared) {
            val v = MusicStore.volume(ctx)
            try {
                p.setVolume(v, v)
            } catch (e: IllegalStateException) {
                // abaikan
            }
        }
    }

    /** Hentikan semuanya tanpa memicu update notifikasi (dipakai saat service berhenti). */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        wantPlaying = false
        MusicBus.state.value = MusicState()
    }

    private fun startTrack(i: Int) {
        releasePlayer()
        if (tracks.isEmpty()) {
            wantPlaying = false
            publish()
            return
        }
        index = i.coerceIn(0, tracks.size - 1)
        val track = tracks[index]
        val mp = MediaPlayer()
        player = mp
        prepared = false
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(ctx, Uri.parse(track.uri))
            mp.setOnPreparedListener {
                if (player === mp) {
                    prepared = true
                    failures = 0
                    val v = MusicStore.volume(ctx)
                    mp.setVolume(v, v)
                    if (wantPlaying) mp.start()
                    publish()
                }
            }
            mp.setOnCompletionListener {
                handler.post { if (player === mp) advance() }
            }
            mp.setOnErrorListener { _, _, _ ->
                handler.post { if (player === mp) onError() }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            handler.post { if (player === mp) onError() }
        }
        publish()
    }

    private fun advance() {
        if (tracks.isEmpty()) {
            wantPlaying = false
            releasePlayer()
            publish()
            return
        }
        val atEnd = index + 1 >= tracks.size
        if (atEnd && !MusicStore.repeat(ctx)) {
            wantPlaying = false
            releasePlayer()
            index = 0
            publish()
            return
        }
        startTrack((index + 1) % tracks.size)
    }

    private fun onError() {
        failures++
        if (failures >= tracks.size) {
            // semua lagu gagal dibuka, berhenti supaya tidak berputar tanpa akhir
            failures = 0
            wantPlaying = false
            releasePlayer()
            publish()
            return
        }
        advance()
    }

    private fun releasePlayer() {
        val p = player
        player = null
        prepared = false
        if (p != null) {
            try {
                p.release()
            } catch (e: Exception) {
                // abaikan
            }
        }
    }

    private fun publish() {
        MusicBus.state.value = MusicState(
            playing = wantPlaying,
            index = index,
            title = tracks.getOrNull(index)?.name ?: ""
        )
        onChanged()
    }
}
