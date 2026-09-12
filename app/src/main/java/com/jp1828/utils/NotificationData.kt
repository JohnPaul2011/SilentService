package com.jp1828.utils

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
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
        val actionsJson = actions.joinToString(",") {
            """{"index":${it.index},"label":"${esc(it.label)}","type":"${it.type}"}"""
        }
        return """{"type":"notification","key":"${esc(key)}","package":"${esc(packageName)}","app":"${esc(appName)}","title":"${esc(title)}","text":"${esc(text)}","subtext":"${esc(subText)}","timestamp":$timestamp,"actions":[$actionsJson]}"""
    }
}
