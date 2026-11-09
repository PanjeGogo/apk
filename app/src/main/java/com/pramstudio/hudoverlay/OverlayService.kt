package com.pramstudio.hudoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView

class OverlayService : Service() {
    companion object {
        const val ACTION_START = "com.pramstudio.hudoverlay.START"
        const val ACTION_PAUSE = "com.pramstudio.hudoverlay.PAUSE"
        const val ACTION_RESUME = "com.pramstudio.hudoverlay.RESUME"
        const val ACTION_STOP = "com.pramstudio.hudoverlay.STOP"
        private const val CHANNEL_ID = "hud_overlay_running"
        private const val NOTIFICATION_ID = 4101
    }

    private lateinit var windowManager: WindowManager
    private var hudView: FrameLayout? = null
    private var videoView: VideoView? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playerPrepared = false
    private var paused = false
    private var videoPosition = 0
    private var audioIndex = 0
    private var activeProfileLandscape: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseOverlay()
            ACTION_RESUME -> resumeOverlay()
            ACTION_STOP -> {
                stopOverlay()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(false))
                paused = false
                showOverlay()
                startAudioIfNeeded()
            }
        }
        updateNotification()
        return START_STICKY
    }

    private fun showOverlay() {
        if (paused) return
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (hudView != null && activeProfileLandscape == landscape) {
            hudView?.visibility = View.VISIBLE
            return
        }
        removeOverlay()
        val profile = HudProfiles.load(this, landscape)
        activeProfileLandscape = landscape
        val frame = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            alpha = profile.opacity
        }

        frame.addView(TextView(this).apply {
            text = profile.label
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xAA202020.toInt())
            setPadding(12, 8, 12, 8)
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))

        profile.imageUri?.let { raw ->
            try {
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                val uri = Uri.parse(raw)
                if (Build.VERSION.SDK_INT >= 28) {
                    val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(contentResolver, uri))
                    image.setImageDrawable(drawable)
                    if (drawable is android.graphics.drawable.AnimatedImageDrawable) drawable.start()
                } else {
                    contentResolver.openInputStream(uri)?.use { image.setImageBitmap(BitmapFactory.decodeStream(it)) }
                }
                frame.addView(image, FrameLayout.LayoutParams(220, 140, Gravity.TOP or Gravity.END))
            } catch (_: Exception) { }
        }

        profile.videoUri?.let { raw ->
            try {
                val video = VideoView(this)
                videoView = video
                video.setVideoURI(Uri.parse(raw))
                video.setOnPreparedListener { player ->
                    player.isLooping = true
                    if (!paused) {
                        if (videoPosition > 0) video.seekTo(videoPosition)
                        video.start()
                    }
                }
                frame.addView(video, FrameLayout.LayoutParams(240, 135, Gravity.BOTTOM or Gravity.END))
            } catch (_: Exception) { }
        }

        profile.webUrl?.takeIf { it.isNotBlank() }?.let { url ->
            try {
                val web = WebView(this)
                web.settings.javaScriptEnabled = true
                web.settings.domStorageEnabled = true
                web.webViewClient = WebViewClient()
                web.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                web.loadUrl(url)
                frame.addView(web, FrameLayout.LayoutParams(260, 160, Gravity.BOTTOM or Gravity.START))
            } catch (_: Exception) { }
        }

        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels * profile.xPercent).toInt()
            y = (metrics.heightPixels * profile.yPercent).toInt()
        }
        try {
            windowManager.addView(frame, params)
            hudView = frame
        } catch (_: Exception) {
            destroyWebViews(frame)
            hudView = null
        }
    }

    private fun destroyWebViews(frame: FrameLayout) {
        for (i in 0 until frame.childCount) {
            val child = frame.getChildAt(i)
            if (child is WebView) {
                try { child.stopLoading(); child.destroy() } catch (_: Exception) { }
            }
        }
    }

    private fun pauseOverlay() {
        paused = true
        hudView?.visibility = View.GONE
        videoView?.let {
            if (it.isPlaying) {
                videoPosition = it.currentPosition
                it.pause()
            }
        }
        if (playerPrepared) mediaPlayer?.let { if (it.isPlaying) it.pause() }
    }

    private fun resumeOverlay() {
        paused = false
        showOverlay()
        videoView?.let {
            if (videoPosition > 0) it.seekTo(videoPosition)
            it.start()
        }
        if (playerPrepared) mediaPlayer?.let { if (!it.isPlaying) it.start() }
        startAudioIfNeeded()
    }

    private fun removeOverlay() {
        videoView?.let {
            try {
                if (it.isPlaying) videoPosition = it.currentPosition
                it.stopPlayback()
            } catch (_: Exception) { }
        }
        videoView = null
        hudView?.let {
            destroyWebViews(it)
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        hudView = null
    }

    private fun startAudioIfNeeded() {
        if (paused || mediaPlayer != null) return
        val playlist = HudProfiles.load(this).audioUris
        if (playlist.isEmpty()) return
        if (audioIndex >= playlist.size) audioIndex = 0
        try {
            val player = MediaPlayer()
            mediaPlayer = player
            playerPrepared = false
            player.setDataSource(this, Uri.parse(playlist[audioIndex]))
            player.setOnPreparedListener {
                playerPrepared = true
                if (!paused) it.start()
            }
            player.setOnCompletionListener {
                val latest = HudProfiles.load(this)
                if (audioIndex + 1 < latest.audioUris.size) {
                    audioIndex++
                    releasePlayer()
                    startAudioIfNeeded()
                } else if (latest.repeatAudio && latest.audioUris.isNotEmpty() && !paused) {
                    audioIndex = 0
                    releasePlayer()
                    startAudioIfNeeded()
                } else {
                    releasePlayer()
                }
            }
            player.setOnErrorListener { _, _, _ ->
                releasePlayer()
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        try { mediaPlayer?.release() } catch (_: Exception) { }
        mediaPlayer = null
        playerPrepared = false
    }

    private fun stopOverlay() {
        removeOverlay()
        releasePlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "HUD Overlay", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Kontrol overlay saat bermain game" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, OverlayService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
    }

    private fun buildNotification(isPaused: Boolean): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("HUD Overlay")
            .setContentText(if (isPaused) "Overlay dijeda" else "Overlay sedang berjalan")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", actionIntent(ACTION_PAUSE, 1))
            .addAction(android.R.drawable.ic_media_play, "Resume", actionIntent(ACTION_RESUME, 2))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", actionIntent(ACTION_STOP, 3))
        return builder.build()
    }

    private fun updateNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(paused))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        removeOverlay()
        if (!paused) showOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        releasePlayer()
        super.onDestroy()
    }
}
