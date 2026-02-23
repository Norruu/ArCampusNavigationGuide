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
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.campus.arnav.R
import com.campus.arnav.ui.MainActivity

class NavigationService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    private var latestInstruction = "Navigating..."
    private var latestDistance = ""

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra(EXTRA_INSTRUCTION) == true) {
            latestInstruction = intent.getStringExtra(EXTRA_INSTRUCTION) ?: latestInstruction
            latestDistance = intent.getStringExtra(EXTRA_DISTANCE) ?: latestDistance
        }

        when (intent?.action) {
            ACTION_START, ACTION_UPDATE -> {
                startOrUpdateForegroundService(latestInstruction, latestDistance)
                updateFloatingWindowText() // Updates widget if it's currently showing
            }
            ACTION_SHOW_OVERLAY -> showFloatingWindow()
            ACTION_HIDE_OVERLAY -> hideFloatingWindow()
            ACTION_STOP -> {
                hideFloatingWindow()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingWindow() {
        if (floatingView != null || !android.provider.Settings.canDrawOverlays(this)) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 1. Inflate the NEW slim floating layout
        val themeContext = android.view.ContextThemeWrapper(this, R.style.Theme_CampusARNav)
        floatingView = LayoutInflater.from(themeContext).inflate(R.layout.layout_floating_navigation, null)

        val layoutFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 2. Lock parameters (No focusable so you can click things behind it)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // 3. Stick it exactly to the top center of the screen
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 0

        // 4. Handle tapping the "X" Cancel Button
        floatingView?.findViewById<View>(R.id.btnCancelNav)?.setOnClickListener {
            // Stop the background service and remove the window
            val stopIntent = Intent(this, NavigationService::class.java)
            stopIntent.action = ACTION_STOP
            startService(stopIntent)

            // Open the app and completely reset it to cancel the navigation
            val openIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(openIntent)
        }

        // 5. Handle tapping anywhere else on the banner
        floatingView?.setOnClickListener {
            // Just return to the app smoothly (the onResume will hide the banner)
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

            if (tvInstruction != null) tvInstruction.text = latestInstruction
            if (tvDistance != null) tvDistance.text = latestDistance
        }
    }

    private fun hideFloatingWindow() {
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
    }

    // ... KEEP your existing startOrUpdateForegroundService and buildNotification functions here ...
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