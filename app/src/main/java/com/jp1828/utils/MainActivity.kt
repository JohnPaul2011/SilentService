package com.jp1828.utils

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var statusTextView: TextView
    private lateinit var boundTextView: TextView
    private var grantButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request rebind if already permitted
        NotificationListener.requestRebind(this)

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(50, 40, 50, 40)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }

        fun tv(text: String, size: Float = 16f, color: Int = Color.WHITE, bold: Boolean = false) =
            TextView(this).apply {
                this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
                if (bold) setTypeface(null, Typeface.BOLD)
                setPadding(0, 12, 0, 12)
            }

        fun btn(text: String, bg: String, onClick: () -> Unit) =
            Button(this).apply {
                this.text = text
                setBackgroundColor(Color.parseColor(bg))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 130
                ).apply { setMargins(0, 16, 0, 0) }
                setOnClickListener { onClick() }
            }

        val ip = getWifiIp()

        rootLayout.apply {
            addView(tv("SilentService", 26f, Color.WHITE, bold = true))
            addView(tv("Notification Mirror", 14f, Color.GRAY))
            addView(tv("──────────────────────────", 12f, Color.DKGRAY))
            addView(tv("Connect PC to:", 13f, Color.LTGRAY))
            addView(tv("$ip : ${TcpServer.PORT}", 20f, Color.parseColor("#64B5F6"), bold = true))

            statusTextView = tv("", 14f, Color.WHITE)
            addView(statusTextView)

            boundTextView = tv("", 13f, Color.LTGRAY)
            addView(boundTextView)

            grantButton = btn("Grant Notification Access", "#E53935") {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            addView(grantButton)

            addView(btn("Start Service", "#0F3460") {
                startForegroundService(Intent(this@MainActivity, MainService::class.java))
                NotificationListener.requestRebind(this@MainActivity)
                updateStatus()
            })

            addView(btn("Send Test Notification", "#43A047") {
                TcpServer.ensureRunning(this@MainActivity)
                TcpServer.instance?.sendTestNotification()
            })
        }

        setContentView(rootLayout)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        NotificationListener.requestRebind(this)
        updateStatus()
    }

    private fun updateStatus() {
        val listenerEnabled = isNotificationListenerEnabled()
        if (listenerEnabled) {
            statusTextView.text = "✓ Notification Permission: GRANTED"
            statusTextView.setTextColor(Color.parseColor("#00E676"))
            grantButton?.visibility = Button.GONE
        } else {
            statusTextView.text = "✗ Notification Permission: REQUIRED"
            statusTextView.setTextColor(Color.parseColor("#FF5252"))
            grantButton?.visibility = Button.VISIBLE
        }

        if (NotificationListener.instance != null) {
            boundTextView.text = "✓ Service Status: ACTIVE & LISTENING"
            boundTextView.setTextColor(Color.parseColor("#00E676"))
        } else {
            boundTextView.text = "⚠️ Listener Not Bound Yet (Tap Start or toggle access)"
            boundTextView.setTextColor(Color.parseColor("#FFB74D"))
        }
    }

    private fun getWifiIp(): String = try {
        @Suppress("DEPRECATION")
        Formatter.formatIpAddress(
            (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).connectionInfo.ipAddress
        )
    } catch (e: Exception) { "?.?.?.?" }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(ComponentName(this, NotificationListener::class.java).flattenToString())
    }
}
