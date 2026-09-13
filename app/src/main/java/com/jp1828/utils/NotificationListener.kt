package com.jp1828.utils

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    companion object {
        var instance: NotificationListener? = null
        private const val TAG = "NotificationListener"

        // Pure system noise packages to ignore
        private val IGNORED_PACKAGES = setOf(
            "android",
            "com.android.systemui"
        )

        fun requestRebind(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    requestRebind(ComponentName(context, NotificationListener::class.java))
                    Log.d(TAG, "Requested rebind for NotificationListener")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to request rebind", e)
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "✓ Notification listener CONNECTED by system")
        TcpServer.ensureRunning(applicationContext)
        syncActiveNotifications()
        TcpServer.instance?.broadcastListenerStatus(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.w(TAG, "✗ Notification listener DISCONNECTED by system")
        TcpServer.instance?.broadcastListenerStatus(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn, broadcast = true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (shouldIgnore(sbn)) return
        Log.d(TAG, "Notification removed: ${sbn.key}")
        TcpServer.instance?.removeNotification(sbn.key)
        val keyEscaped = sbn.key.replace("\\", "\\\\").replace("\"", "\\\"")
        TcpServer.instance?.broadcast("""{"type":"notification_removed","key":"$keyEscaped"}""")
    }

    fun syncActiveNotifications() {
        try {
            val active = activeNotifications
            if (active != null) {
                Log.d(TAG, "Syncing ${active.size} active notifications from status bar")
                for (sbn in active) {
                    processNotification(sbn, broadcast = false)
                }
                // Broadcast updated status with count
                TcpServer.instance?.broadcastListenerStatus(true)
            } else {
                Log.d(TAG, "No active notifications returned by system")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing active notifications", e)
        }
    }

    private fun shouldIgnore(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName in IGNORED_PACKAGES) return true
        // Only ignore the persistent foreground service notification from our app
        if (sbn.packageName == packageName && sbn.id == MainService.NOTIFICATION_ID) return true
        return false
    }

    private fun processNotification(sbn: StatusBarNotification, broadcast: Boolean) {
        if (shouldIgnore(sbn)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras

        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            ?: ""

        var text = extractText(extras, notification)

        // If title and text are still empty, try tickerText or subText
        if (title.isBlank() && text.isBlank()) {
            val ticker = notification.tickerText?.toString()
            if (!ticker.isNullOrBlank()) {
                text = ticker
            } else {
                val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                if (!sub.isNullOrBlank()) {
                    text = sub
                } else {
                    // Truly empty notification, ignore
                    return
                }
            }
        }

        val appName = try {
            val appInfo = packageManager.getApplicationInfo(sbn.packageName, PackageManager.GET_META_DATA)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        if (title.isBlank()) {
            title = appName
        }

        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        // Extract actions
        val actions = mutableListOf<NotificationAction>()
        notification.actions?.forEachIndexed { index, action ->
            val label = action.title?.toString() ?: "Action $index"
            val remoteInputs = action.remoteInputs
            val type = if (remoteInputs != null && remoteInputs.isNotEmpty()) "reply" else "action"
            actions.add(
                NotificationAction(
                    index = index,
                    label = label,
                    type = type,
                    pendingIntent = action.actionIntent,
                    remoteInputs = remoteInputs,
                    remoteInputResultKey = remoteInputs?.firstOrNull()?.resultKey
                )
            )
        }

        val data = NotificationData(
            key = sbn.key,
            packageName = sbn.packageName,
            appName = appName,
            title = title,
            text = text,
            subText = subText,
            timestamp = sbn.postTime,
            actions = actions
        )

        Log.d(TAG, "Captured notification: [$appName] $title - $text (actions: ${actions.size})")
        TcpServer.instance?.storeNotification(data)
        if (broadcast) {
            TcpServer.instance?.broadcast(data.toJson())
        }
    }

    private fun extractText(extras: android.os.Bundle, notification: Notification): String {
        // 1. MessagingStyle: grab the latest message from EXTRA_MESSAGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (!messages.isNullOrEmpty()) {
                val last = messages.last()
                if (last is Parcelable) {
                    try {
                        val msgBundle = last as? android.os.Bundle
                        val msgText = msgBundle?.getCharSequence("text")?.toString()
                        if (!msgText.isNullOrBlank()) return msgText
                    } catch (_: Exception) {}
                    val str = last.toString()
                    if (str.isNotBlank()) return str
                }
            }
        }

        // 2. BigTextStyle
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        if (!bigText.isNullOrBlank()) return bigText

        // 3. InboxStyle lines — join them
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!lines.isNullOrEmpty()) return lines.joinToString(" | ")

        // 4. Standard text
        val standardText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (!standardText.isNullOrBlank()) return standardText

        // 5. Info / Summary text
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()
        if (!infoText.isNullOrBlank()) return infoText

        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
        if (!summaryText.isNullOrBlank()) return summaryText

        // 6. Ticker text fallback
        return notification.tickerText?.toString() ?: ""
    }
}
