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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }

        fun tv(text: String, size: Float = 16f, color: Int = Color.WHITE, bold: Boolean = false) =
            TextView(this).apply {
                this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER
                if (bold) setTypeface(null, Typeface.BOLD)
                setPadding(0, 16, 0, 16)
            }

        fun btn(text: String, bg: String, onClick: () -> Unit) =
            Button(this).apply {
                this.text = text
                setBackgroundColor(Color.parseColor(bg))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 140
                ).apply { setMargins(0, 20, 0, 0) }
                setOnClickListener { onClick() }
            }

        val ip = getWifiIp()
        val listenerEnabled = isNotificationListenerEnabled()
        val listenerStatus = if (listenerEnabled) "✓ Notification Access Granted" else "✗ Notification Access Required"
        val statusColor = if (listenerEnabled) Color.parseColor("#00E676") else Color.parseColor("#FF5252")

        layout.apply {
            addView(tv("SilentService", 28f, Color.WHITE, bold = true))
            addView(tv("Notification Mirror", 14f, Color.GRAY))
            addView(tv("──────────────────────────", 12f, Color.DKGRAY))
            addView(tv("Connect your PC to:", 13f, Color.LTGRAY))
            addView(tv("$ip : ${TcpServer.PORT}", 22f, Color.parseColor("#64B5F6"), bold = true))
            addView(tv(listenerStatus, 14f, statusColor))
            if (!listenerEnabled) {
                addView(btn("Grant Notification Access", "#E53935") {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                })
            }
            addView(btn("Start Service", "#0F3460") {
                startForegroundService(Intent(this@MainActivity, MainService::class.java))
            })
        }
        setContentView(layout)
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
