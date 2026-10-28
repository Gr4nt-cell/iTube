package com.gr4nt.itube.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.gr4nt.itube.ITubeApp
import com.gr4nt.itube.R
import com.gr4nt.itube.ui.MainActivity
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating pop-up player: a draggable, pinch-resizable video window drawn over other apps.
 * Tap to show controls, drag to move, pinch to resize, fling to the screen edge... or tap ✕.
 */
class PopupPlayerService : Service() {

    private lateinit var wm: WindowManager
    private var root: FrameLayout? = null
    private var playerView: PlayerView? = null
    private var controls: View? = null
    private var playPause: ImageButton? = null
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val hideControls = Runnable { controls?.animate()?.alpha(0f)?.withEndAction { controls?.visibility = View.GONE }?.start() }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayIcon()
            root?.keepScreenOn = isPlaying
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            closePopup(stopPlayback = true)
            return START_NOT_STICKY
        }
        startInForeground()
        if (root == null) createWindow()
        return START_NOT_STICKY
    }

    private fun startInForeground() {
        val close = PendingIntent.getService(
            this, 1, Intent(this, PopupPlayerService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PLAYER),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, ITubeApp.CHANNEL_POPUP)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(PlaybackEngine.current.value?.title ?: "Pop-up player")
            .setContentText("Playing in a floating window")
            .setContentIntent(open)
            .addAction(R.drawable.ic_close, "Close", close)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createWindow() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        val width = (minOf(metrics.widthPixels, metrics.heightPixels) * 0.62f).roundToInt()
        val height = (width * 9f / 16f).roundToInt()

        params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels - width - dp(16)
            y = metrics.heightPixels - height - dp(160)
        }

        val radius = dp(18).toFloat()
        val frame = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(Color.BLACK)
                setStroke(dp(1), Color.argb(90, 255, 255, 255))
            }
            elevation = dp(16).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            clipToOutline = true
        }

        val pv = LayoutInflater.from(this).inflate(R.layout.player_surface, null) as PlayerView
        frame.addView(pv, FrameLayout.LayoutParams(-1, -1))
        PlaybackEngine.setSurfaceOwner(SurfaceOwner.POPUP)
        pv.player = PlaybackEngine.player
        playerView = pv

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(110, 0, 0, 0))
            visibility = View.GONE
            alpha = 0f
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(button(R.drawable.ic_prev) { PlaybackEngine.previous() })
        playPause = button(R.drawable.ic_pause) { PlaybackEngine.togglePlay() }
        row.addView(playPause, LinearLayout.LayoutParams(dp(52), dp(52)))
        row.addView(button(R.drawable.ic_next) { PlaybackEngine.next() })
        overlay.addView(row, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        overlay.addView(
            button(R.drawable.ic_close) { closePopup(stopPlayback = true) },
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END),
        )
        overlay.addView(
            button(R.drawable.ic_fullscreen) { openInApp() },
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.START),
        )
        frame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        controls = overlay

        attachGestures(frame, metrics)
        frame.keepScreenOn = PlaybackEngine.player.isPlaying
        wm.addView(frame, params)
        root = frame
        PlaybackEngine.player.addListener(listener)
        updatePlayIcon()
    }

    private fun button(icon: Int, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.argb(70, 255, 255, 255))
        }
        setPadding(dp(8), dp(8), dp(8), dp(8))
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { setMargins(dp(8), 0, dp(8), 0) }
        setOnClickListener {
            onClick()
            scheduleHide()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachGestures(frame: View, metrics: DisplayMetrics) {
        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val maxW = metrics.widthPixels
                val newW = (params.width * detector.scaleFactor).roundToInt().coerceIn(dp(180), maxW)
                params.width = newW
                params.height = (newW * 9f / 16f).roundToInt()
                wm.updateViewLayout(frame, params)
                return true
            }
        })
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        frame.setOnTouchListener { _, e ->
            scaleDetector.onTouchEvent(e)
            if (scaleDetector.isInProgress) {
                moved = true
                return@setOnTouchListener true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved && e.pointerCount == 1) {
                        params.x = (startX + dx).roundToInt()
                        params.y = (startY + dy).roundToInt()
                        wm.updateViewLayout(frame, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleControls() else snapToEdge(frame, metrics)
                }
            }
            true
        }
    }

    private fun snapToEdge(frame: View, metrics: DisplayMetrics) {
        val margin = dp(12)
        val center = params.x + params.width / 2
        params.x = if (center < metrics.widthPixels / 2) margin else metrics.widthPixels - params.width - margin
        params.y = params.y.coerceIn(dp(24), metrics.heightPixels - params.height - dp(24))
        wm.updateViewLayout(frame, params)
    }

    private fun toggleControls() {
        val c = controls ?: return
        if (c.visibility == View.VISIBLE) {
            handler.removeCallbacks(hideControls)
            hideControls.run()
        } else {
            c.visibility = View.VISIBLE
            c.animate().alpha(1f).start()
            scheduleHide()
        }
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 3000)
    }

    private fun updatePlayIcon() {
        playPause?.setImageResource(if (PlaybackEngine.player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun openInApp() {
        closePopup(stopPlayback = false)
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun closePopup(stopPlayback: Boolean) {
        handler.removeCallbacksAndMessages(null)
        PlaybackEngine.player.removeListener(listener)
        playerView?.player = null
        playerView = null
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        PlaybackEngine.setSurfaceOwner(SurfaceOwner.MAIN)
        if (stopPlayback) PlaybackEngine.player.pause()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (root != null) closePopup(stopPlayback = false)
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val NOTIFICATION_ID = 4242
        private const val ACTION_CLOSE = "com.gr4nt.itube.POPUP_CLOSE"

        fun canDrawOverlays(context: Context) = Settings.canDrawOverlays(context)

        fun overlayPermissionIntent(context: Context) = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Returns false when the overlay permission is still missing. */
        fun start(context: Context): Boolean {
            if (!canDrawOverlays(context)) return false
            context.startForegroundService(Intent(context, PopupPlayerService::class.java))
            return true
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(Intent(context, PopupPlayerService::class.java).setAction(ACTION_CLOSE))
            }
        }
    }
}
