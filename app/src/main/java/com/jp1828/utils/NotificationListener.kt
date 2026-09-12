package com.jp1828.utils

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationListener : NotificationListenerService() {

    companion object {
        var instance: NotificationListener? = null
        private const val TAG = "NotificationListener"
        private val IGNORED_PACKAGES = setOf(
            "com.jp1828.utils", "android", "com.android.systemui",
            "com.android.launcher3", "com.google.android.gms"
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
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName in IGNORED_PACKAGES) return
        if (sbn.isOngoing) return
        val notification = sbn.notification ?: return
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        if (title.isBlank() && text.isBlank()) return
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, PackageManager.GET_META_DATA)
            ).toString()
        } catch (e: Exception) { sbn.packageName }
        val actions = mutableListOf<NotificationAction>()
        notification.actions?.forEachIndexed { index, action ->
            val label = action.title?.toString() ?: "Action $index"
            val remoteInputs = action.remoteInputs
            val type = if (remoteInputs != null && remoteInputs.isNotEmpty()) "reply" else "action"
            actions.add(NotificationAction(
                index = index, label = label, type = type,
                pendingIntent = action.actionIntent,
                remoteInputs = remoteInputs,
                remoteInputResultKey = remoteInputs?.firstOrNull()?.resultKey
            ))
        }
        val data = NotificationData(
            key = sbn.key, packageName = sbn.packageName, appName = appName,
            title = title, text = text, subText = subText,
            timestamp = sbn.postTime, actions = actions
        )
        Log.d(TAG, "Posted: [$appName] $title")
        TcpServer.instance?.storeNotification(data)
        TcpServer.instance?.broadcast(data.toJson())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in IGNORED_PACKAGES) return
        TcpServer.instance?.removeNotification(sbn.key)
        TcpServer.instance?.broadcast("""{"type":"notification_removed","key":"${sbn.key}"}""".replace("\\", "\\\\"))
    }
}
