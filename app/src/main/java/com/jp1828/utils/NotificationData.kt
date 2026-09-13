package com.jp1828.utils

import org.json.JSONArray
import org.json.JSONObject

data class NotificationAction(
    val index: Int,
    val label: String,
    val type: String,
    val pendingIntent: android.app.PendingIntent?,
    val remoteInputs: Array<android.app.RemoteInput>? = null,
    val remoteInputResultKey: String? = null
)

data class NotificationData(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val subText: String,
    val timestamp: Long,
    val actions: List<NotificationAction>
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("type", "notification")
        obj.put("key", key)
        obj.put("package", packageName)
        obj.put("app", appName)
        obj.put("title", title)
        obj.put("text", text)
        obj.put("subtext", subText)
        obj.put("timestamp", timestamp)
        val arr = JSONArray()
        actions.forEach {
            val actObj = JSONObject()
            actObj.put("index", it.index)
            actObj.put("label", it.label)
            actObj.put("type", it.type)
            arr.put(actObj)
        }
        obj.put("actions", arr)
        return obj.toString()
    }
}
