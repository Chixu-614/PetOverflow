package com.example.deskpet

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat

/**
 * 悬浮窗宠物主服务
 * 这个Service会创建一个透明WebView悬浮窗在所有应用之上
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        private const val CHANNEL_ID = "pet_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PET_SIZE_DP = 180
        private const val PET_HEIGHT_DP = 240

        /** 定时更新通知碎碎念 */
        private val whisperMessages = listOf(
            "盯——",
            "你在干嘛呀",
            "戳我干嘛！",
            "ε(┤┬﹏┬├)з",
            "该喝水了！",
            "还不睡吗？",
            "早安呀~",
            "午安~在忙什么？"
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildWhisperNotification())
        setupOverlay()

        // 每小时更新一次通知碎碎念
        Handler(Looper.getMainLooper()).post(object : Runnable {
            override fun run() {
                val notification = buildWhisperNotification()
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
                Handler(Looper.getMainLooper()).postDelayed(this, 3600000L)
            }
        })
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            dpToPx(PET_SIZE_DP),
            dpToPx(PET_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = WebViewClient()
            // 从assets加载宠物HTML
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }

        windowManager?.addView(overlayView, params)
    }

    // ========== 手势状态机 ==========
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false
    private var tapCount = 0
    private var firstTapTime = 0L

    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        hasMoved = true
                        params?.x = initialX + dx
                        params?.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime

                    if (!hasMoved) {
                        val now = System.currentTimeMillis()

                        if (elapsed > 600) {
                            onLongPress()
                        } else if (now - lastTapTime < 300) {
                            onDoubleTap()
                        } else {
                            lastTapTime = now

                            // 连击计数
                            if (now - firstTapTime > 2000) {
                                tapCount = 1
                                firstTapTime = now
                            } else {
                                tapCount++
                                firstTapTime = now
                            }

                            when (tapCount) {
                                1 -> onTap()
                                3 -> onTripleTap()
                                5 -> onRapidTap()
                                8 -> onFuriousTap()
                                else -> onTap()
                            }
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun onTap() {
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onTap()", null
        )
    }

    private fun onDoubleTap() {
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onDoubleTap()", null
        )
    }

    private fun onTripleTap() {
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onTripleTap()", null
        )
    }

    private fun onRapidTap() {
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onRapidTap()", null
        )
    }

    private fun onFuriousTap() {
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onFuriousTap()", null
        )
    }

    private fun onLongPress() {
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onLongPress()", null
        )
    }

    // ========== 通知 ==========
    private fun buildWhisperNotification(): Notification {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val text = when {
            hour < 6 -> "凌晨了还不睡吗…(｡•́︿•̀｡)"
            hour < 9 -> "早安呀~今天也要开心！"
            hour < 12 -> "上午好~在忙什么呀"
            hour < 14 -> "午安~该吃饭啦！"
            hour < 18 -> "下午好~盯——"
            hour < 21 -> "晚上了呢，陪我玩！"
            hour < 23 -> "还不睡吗？ε(┤┬﹏┬├)з"
            else -> "深夜了…该休息了哦"
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🐾 池续")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "桌宠",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // ========== 工具 ==========
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}
