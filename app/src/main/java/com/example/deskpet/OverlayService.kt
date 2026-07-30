package com.example.deskpet

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "pet_overlay_channel"
        private const val HOLIDAY_CHANNEL_ID = "pet_holiday_channel"
        private const val NOTIF_WHISPER = 1001
        private const val NOTIF_HOLIDAY = 1002
        private const val PET_SIZE_DP = 180
        private const val PET_HEIGHT_DP = 240
        private const val PREFS_NAME = "crab_prefs"
        private const val KEY_BIRTHDAY_MONTH = "birthday_month"
        private const val KEY_BIRTHDAY_DAY = "birthday_day"

        const val ACTION_SET_PROP_XMAS = "com.example.deskpet.SET_XMAS"
        const val ACTION_SET_PROP_GAMING = "com.example.deskpet.SET_GAMING"
        const val ACTION_SET_PROP_CURIOUS = "com.example.deskpet.SET_CURIOUS"
        const val ACTION_CLEAR_PROP = "com.example.deskpet.CLEAR_PROP"
        const val ACTION_RANDOM_PROP = "com.example.deskpet.RANDOM_PROP"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null
    private lateinit var sensorManager: SensorManager
    private var lastShakeTime = 0L
    private var isInjectingBirthday = false

    private val propReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val js = when (intent.action) {
                ACTION_SET_PROP_XMAS -> "window.petEngine?.setProp('xmas')"
                ACTION_SET_PROP_GAMING -> "window.petEngine?.setProp('gaming')"
                ACTION_SET_PROP_CURIOUS -> "window.petEngine?.setProp('curious')"
                ACTION_CLEAR_PROP -> "window.petEngine?.clearProps()"
                ACTION_RANDOM_PROP -> "window.petEngine?.randomProp()"
                else -> null
            }
            if (js != null) {
                Handler(Looper.getMainLooper()).post {
                    overlayView?.evaluateJavascript(js, null)
                }
            }
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val magnitude = Math.sqrt((x*x + y*y + z*z).toDouble()).toFloat()
                if (magnitude > 15f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTime > 1000) {
                        lastShakeTime = now
                        overlayView?.evaluateJavascript(
                            "window.petEngine?.randomProp()", null
                        )
                        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else { v.vibrate(80) }
                    }
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        registerPropReceiver()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.registerListener(
            sensorListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_GAME
        )
        startForeground(NOTIF_WHISPER, buildWhisperNotification())
        setupOverlay()
        scheduleWhisperUpdate()
        checkHolidayAndBirthday()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(
            dpToPx(PET_SIZE_DP), dpToPx(PET_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50; y = 300
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!isInjectingBirthday) {
                        isInjectingBirthday = true
                        injectBirthdayData()
                    }
                }
            }
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }
        windowManager?.addView(overlayView, params)
    }

    private fun injectBirthdayData() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bm = prefs.getInt(KEY_BIRTHDAY_MONTH, -1)
        val bd = prefs.getInt(KEY_BIRTHDAY_DAY, -1)
        if (bm > 0 && bd > 0) {
            val js = "window.petBirthday = {month: $bm, day: $bd};" +
                    "if(window.petEngine&&window.location.reload){}"
            overlayView?.evaluateJavascript(js, null)
        }
    }

    private fun checkHolidayAndBirthday() {
        val cal = java.util.Calendar.getInstance()
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val bm = prefs.getInt(KEY_BIRTHDAY_MONTH, -1)
        val bd = prefs.getInt(KEY_BIRTHDAY_DAY, -1)

        if (bm > 0 && bm == month && bd == day) {
            sendHolidayNotif("🎂 生日快乐！", "壳壳蟹送来生日祝福～今天的你超棒的！")
            return
        }

        val holidayEntry = when {
            month == 1 && day == 1 -> "🎆 元旦快乐！新年新气象～"
            month == 2 && day == 14 -> "💝 情人节快乐！爱你哟～"
            month == 5 && day == 1 -> "🌸 劳动节快乐！今天好好休息吧～"
            month == 10 && day == 1 -> "🎑 国庆节快乐！祝你假期愉快～"
            month == 12 && day == 25 -> "🎄 圣诞节快乐！Merry Christmas～"
            else -> null
        }
        if (holidayEntry != null) {
            val title = when {
                month == 1 && day == 1 -> "新年好！"
                month == 12 && day == 25 -> "Merry Christmas！"
                else -> "节日快乐！"
            }
            sendHolidayNotif(title, holidayEntry)
        }
    }

    private fun sendHolidayNotif(title: String, body: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, HOLIDAY_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSilent(false)
            .build()
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_HOLIDAY, notif)
    }

    private fun scheduleWhisperUpdate() {
        Handler(Looper.getMainLooper()).post(object : Runnable {
            override fun run() {
                val notif = buildWhisperNotification()
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIF_WHISPER, notif)
                Handler(Looper.getMainLooper()).postDelayed(this, 3600000L)
            }
        })
    }

    // ========== 手势 ==========
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
                    if (!hasMoved) {
                        val now = System.currentTimeMillis()
                        if (now - touchStartTime > 600) {
                            callJS("onLongPress")
                        } else if (now - lastTapTime < 300) {
                            callJS("onDoubleTap")
                        } else {
                            lastTapTime = now
                            if (now - firstTapTime > 2000) {
                                tapCount = 1; firstTapTime = now
                            } else {
                                tapCount++; firstTapTime = now
                            }
                            when (tapCount) {
                                1 -> callJS("onTap")
                                3 -> callJS("onTripleTap")
                                5 -> callJS("onRapidTap")
                                8 -> callJS("onFuriousTap")
                                else -> callJS("onTap")
                            }
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun callJS(method: String) {
        overlayView?.evaluateJavascript(
            "window.petEngine?.$method()", null
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

        val launchIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val piXmas = makePropPI(1, ACTION_SET_PROP_XMAS)
        val piGaming = makePropPI(2, ACTION_SET_PROP_GAMING)
        val piCurious = makePropPI(3, ACTION_SET_PROP_CURIOUS)
        val piClear = makePropPI(4, ACTION_CLEAR_PROP)
        val piRandom = makePropPI(5, ACTION_RANDOM_PROP)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🐾 壳壳蟹")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "🎄圣诞", piXmas)
            .addAction(0, "🎮游戏", piGaming)
            .addAction(0, "🔍探索", piCurious)
            .addAction(0, "🎲随机", piRandom)
            .addAction(0, "✕清除", piClear)
            .build()
    }

    private fun makePropPI(requestCode: Int, action: String): PendingIntent {
        return PendingIntent.getBroadcast(
            this, requestCode,
            Intent(action).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun registerPropReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_SET_PROP_XMAS)
            addAction(ACTION_SET_PROP_GAMING)
            addAction(ACTION_SET_PROP_CURIOUS)
            addAction(ACTION_CLEAR_PROP)
            addAction(ACTION_RANDOM_PROP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(propReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(propReceiver, filter)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val whisper = NotificationChannel(CHANNEL_ID, "桌宠碎碎念", NotificationManager.IMPORTANCE_LOW)
            whisper.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(whisper)

            val holiday = NotificationChannel(HOLIDAY_CHANNEL_ID, "节日祝福", NotificationManager.IMPORTANCE_DEFAULT)
            holiday.setShowBadge(true)
            getSystemService(NotificationManager::class.java).createNotificationChannel(holiday)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        try { unregisterReceiver(propReceiver) } catch (_: Exception) {}
        try { sensorManager.unregisterListener(sensorListener) } catch (_: Exception) {}
        super.onDestroy()
    }
}