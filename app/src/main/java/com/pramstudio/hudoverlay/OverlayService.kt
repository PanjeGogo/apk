package com.pramstudio.hudoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

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
    private var hudView: View? = null
    private var paused = false

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
            }
        }
        updateNotification()
        return START_STICKY
    }

    private fun showOverlay() {
        if (paused || hudView != null) return
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val profile = HudProfiles.load(this, landscape)
        val view = TextView(this).apply {
            text = profile.label
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC202020.toInt())
            setPadding(18, 10, 18, 10)
            alpha = profile.opacity
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
            windowManager.addView(view, params)
            hudView = view
        } catch (_: Exception) {
            hudView = null
        }
    }

    private fun pauseOverlay() {
        paused = true
        removeOverlay()
    }

    private fun resumeOverlay() {
        paused = false
        showOverlay()
    }

    private fun removeOverlay() {
        hudView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        hudView = null
    }

    private fun stopOverlay() {
        removeOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "HUD Overlay", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Kontrol overlay saat bermain game" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
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
            .addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                actionIntent(ACTION_PAUSE, 1)
            )
            .addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                actionIntent(ACTION_RESUME, 2)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                actionIntent(ACTION_STOP, 3)
            )
        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(paused))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        removeOverlay()
        showOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
}
