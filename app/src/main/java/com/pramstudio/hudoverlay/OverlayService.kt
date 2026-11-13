package com.pramstudio.hudoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class OverlayService : Service() {

    companion object {
        const val ACTION_PAUSE = "com.pramstudio.hudoverlay.PAUSE"
        const val ACTION_RESUME = "com.pramstudio.hudoverlay.RESUME"
        const val ACTION_STOP = "com.pramstudio.hudoverlay.STOP"
        const val ACTION_MUSIC_TOGGLE = "com.pramstudio.hudoverlay.MUSIC_TOGGLE"
        const val ACTION_MUSIC_NEXT = "com.pramstudio.hudoverlay.MUSIC_NEXT"
        const val ACTION_MUSIC_PREV = "com.pramstudio.hudoverlay.MUSIC_PREV"
        const val ACTION_MUSIC_PLAY_INDEX = "com.pramstudio.hudoverlay.MUSIC_PLAY_INDEX"
        private const val EXTRA_INDEX = "index"
        private const val CHANNEL_ID = "hud_overlay"
        private const val NOTIF_ID = 1

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }

        /** Kontrol musik. Kalau service belum jalan, service (dan overlay) ikut dinyalakan. */
        fun sendMusic(ctx: Context, action: String, index: Int = 0) {
            val intent = Intent(ctx, OverlayService::class.java)
                .setAction(action)
                .putExtra(EXTRA_INDEX, index)
            ContextCompat.startForegroundService(ctx, intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var music: MusicPlayer
    private var hud: HudView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var paused = false

    // Editor dan layar musik menyimpan ke prefs -> service langsung ikut berubah.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            HudStore.KEY_LAYOUT -> refresh()
            MusicStore.KEY_TRACKS -> music.onTracksChanged()
            MusicStore.KEY_VOLUME -> music.applyVolume()
            else -> {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        music = MusicPlayer(this) { updateNotification() }
        startForegroundCompat()
        addOverlay()
        HudStore.prefs(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> setPaused(true)
            ACTION_RESUME -> setPaused(false)
            ACTION_STOP -> stopSelf()
            ACTION_MUSIC_TOGGLE -> music.toggle()
            ACTION_MUSIC_NEXT -> music.next()
            ACTION_MUSIC_PREV -> music.prev()
            ACTION_MUSIC_PLAY_INDEX ->
                music.playIndex(intent?.getIntExtra(EXTRA_INDEX, 0) ?: 0)
            else -> {}
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refresh(newConfig.orientation)
    }

    override fun onDestroy() {
        HudStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        music.release()
        hud?.let {
            it.clear()
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // view sudah tidak terpasang
            }
        }
        hud = null
        overlayParams = null
        _running.value = false
        super.onDestroy()
    }

    private fun addOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val view = HudView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        // Android 12+: sentuhan hanya tembus ke app lain kalau opacity jendela <= 0.8.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.alpha = 0.8f
        }
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        try {
            windowManager.addView(view, params)
            hud = view
            overlayParams = params
            view.visibility = if (paused) View.GONE else View.VISIBLE
            refresh()
            _running.value = true
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun refresh(orientation: Int = resources.configuration.orientation) {
        val profile =
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) Profile.LANDSCAPE
            else Profile.PORTRAIT
        hud?.setItems(HudStore.load(this).items(profile))
        overlayParams?.let { params ->
            val touchable = hud?.hasTouchableWeb() == true
            val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            params.flags = base or if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            try { hud?.let { windowManager.updateViewLayout(it, params) } } catch (_: Exception) { }
        }
    }

    private fun setPaused(value: Boolean) {
        paused = value
        hud?.visibility = if (paused) View.GONE else View.VISIBLE
        updateNotification()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "HUD Overlay", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun startForegroundCompat() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun serviceAction(label: String, act: String): NotificationCompat.Action {
        val intent = Intent(this, OverlayService::class.java).setAction(act)
        val pi = PendingIntent.getService(
            this,
            act.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Action.Builder(0, label, pi).build()
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val musicPlaying = ::music.isInitialized && music.isPlaying
        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("HUD Overlay")
            .setContentText(
                (if (paused) "Overlay dijeda" else "Overlay aktif") +
                    (if (musicPlaying) " - musik diputar" else "")
            )
            .setContentIntent(open)
            .setOngoing(true)
        if (paused) {
            b.addAction(serviceAction("Lanjut", ACTION_RESUME))
        } else {
            b.addAction(serviceAction("Jeda", ACTION_PAUSE))
        }
        b.addAction(
            serviceAction(if (musicPlaying) "Jeda musik" else "Putar musik", ACTION_MUSIC_TOGGLE)
        )
        b.addAction(serviceAction("Stop", ACTION_STOP))
        return b.build()
    }
}
