package com.campus.arnav.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.campus.arnav.R
import com.campus.arnav.ui.MainActivity

class NavigationService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    private var latestInstruction = "Navigating..."
    private var latestDistance = ""
    private var latestDirectionCode = "straight"

    companion object {
        const val CHANNEL_ID = "CampusNavigationChannel"
        const val NOTIFICATION_ID = 1

        const val ACTION_START = "ACTION_START_NAVIGATION"
        const val ACTION_UPDATE = "ACTION_UPDATE_NAVIGATION"
        const val ACTION_STOP = "ACTION_STOP_NAVIGATION"

        const val ACTION_SHOW_OVERLAY = "ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "ACTION_HIDE_OVERLAY"

        const val EXTRA_INSTRUCTION = "EXTRA_INSTRUCTION"
        const val EXTRA_DISTANCE = "EXTRA_DISTANCE"
        const val EXTRA_DIRECTION = "EXTRA_DIRECTION"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra(EXTRA_INSTRUCTION) == true) {
            latestInstruction = intent.getStringExtra(EXTRA_INSTRUCTION) ?: latestInstruction
            latestDistance = intent.getStringExtra(EXTRA_DISTANCE) ?: latestDistance
            latestDirectionCode = intent.getStringExtra(EXTRA_DIRECTION) ?: latestDirectionCode
        }

        // --- CRITICAL FIX: ALWAYS start the foreground notification immediately
        // to prevent the dreaded ForegroundServiceDidNotStartInTimeException crash. ---
        startOrUpdateForegroundService(latestInstruction, latestDistance)

        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> {
                updateFloatingWindowText()
            }
            ACTION_SHOW_OVERLAY -> {
                showFloatingWindow()
            }
            ACTION_HIDE_OVERLAY -> {
                hideFloatingWindow()
            }
            ACTION_STOP -> {
                hideFloatingWindow()
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingWindow() {
        if (floatingView != null || !android.provider.Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val themeContext = android.view.ContextThemeWrapper(this, R.style.Theme_CampusARNav)
        floatingView = LayoutInflater.from(themeContext).inflate(R.layout.layout_floating_navigation, null)

        val layoutFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 0

        floatingView?.findViewById<View>(R.id.btnCancelNav)?.setOnClickListener {
            val stopIntent = Intent(this, NavigationService::class.java)
            stopIntent.action = ACTION_STOP
            startService(stopIntent)

            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(openIntent)
        }

        floatingView?.setOnClickListener {
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(openIntent)
        }

        windowManager?.addView(floatingView, params)
        updateFloatingWindowText()
    }

    private fun updateFloatingWindowText() {
        floatingView?.let { view ->
            val tvInstruction = view.findViewById<TextView>(R.id.tvCurrentInstruction)
            val tvDistance = view.findViewById<TextView>(R.id.tvDistanceToNext)
            val ivIcon = view.findViewById<ImageView>(R.id.ivDirectionIcon)

            tvInstruction?.text = latestInstruction
            tvDistance?.text = latestDistance

            val iconRes = when (latestDirectionCode) {
                "right" -> R.drawable.ic_turn_right
                "left" -> R.drawable.ic_turn_left
                else -> R.drawable.ic_arrow_up
            }
            ivIcon?.setImageResource(iconRes)
        }
    }

    private fun hideFloatingWindow() {
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
    }

    private fun startOrUpdateForegroundService(instruction: String, distance: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Navigation Status", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        val notification = buildNotification(instruction, distance)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(instruction: String, distance: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(instruction)
            .setContentText(distance)
            .setSmallIcon(R.drawable.ic_destination)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {
        hideFloatingWindow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}