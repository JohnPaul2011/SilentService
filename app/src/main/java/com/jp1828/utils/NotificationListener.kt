package com.jp1828.utils

import android.app.Notification
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

        // Only skip our own app and pure system UI noise
        private val IGNORED_PACKAGES = setOf(
            "com.jp1828.utils",
            "android",
            "com.android.systemui"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.d(TAG, "Notification listener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName in IGNORED_PACKAGES) return

        val notification = sbn.notification ?: return
        val extras = notification.extras

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?: ""

        // Handle MessagingStyle (WhatsApp, SMS, Telegram, etc.)
        val text = extractText(extras)

        // Skip completely empty notifications
        if (title.isBlank() && text.isBlank()) return

        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, PackageManager.GET_META_DATA)
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
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

        Log.d(TAG, "Posted: [$appName] $title: $text")
        TcpServer.instance?.storeNotification(data)
        TcpServer.instance?.broadcast(data.toJson())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in IGNORED_PACKAGES) return
        val key = sbn.key.replace("\\", "\\\\").replace("\"", "\\\"")
        TcpServer.instance?.removeNotification(sbn.key)
        TcpServer.instance?.broadcast("""{"type":"notification_removed","key":"$key"}""")
    }

    /**
     * Extract readable text from notification extras.
     * Handles:
     *  - MessagingStyle (WhatsApp, Telegram, SMS etc.) via EXTRA_MESSAGES
     *  - BigTextStyle via EXTRA_BIG_TEXT
     *  - InboxStyle via EXTRA_TEXT_LINES
     *  - Standard EXTRA_TEXT fallback
     */
    private fun extractText(extras: android.os.Bundle): String {
        // MessagingStyle: grab the latest message from EXTRA_MESSAGES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (!messages.isNullOrEmpty()) {
                val last = messages.last()
                if (last is Parcelable) {
                    // Each message is a Bundle with "text" key
                    try {
                        val msgBundle = last as? android.os.Bundle
                        val msgText = msgBundle?.getCharSequence("text")?.toString()
                        if (!msgText.isNullOrBlank()) return msgText
                    } catch (_: Exception) {}
                    // Fallback: use toString
                    val str = last.toString()
                    if (str.isNotBlank()) return str
                }
            }
        }

        // BigTextStyle
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        if (!bigText.isNullOrBlank()) return bigText

        // InboxStyle lines — join them
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!lines.isNullOrEmpty()) return lines.joinToString(" | ")

        // Standard text
        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
    }
}
