package com.jp1828.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class TcpServer(private val context: Context) {

    companion object {
        const val PORT = 5556
        var instance: TcpServer? = null
        private const val TAG = "TcpServer"

        /** Ensure TcpServer is running — called from NotificationListener too */
        fun ensureRunning(context: Context) {
            if (instance == null) {
                Log.d(TAG, "TcpServer not running, starting via ensureRunning()")
                TcpServer(context.applicationContext).start()
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<Pair<Socket, PrintWriter>>()
    private val notifications = ConcurrentHashMap<String, NotificationData>()

    fun start() {
        if (instance != null && instance !== this) {
            Log.w(TAG, "TcpServer already running, skipping duplicate start")
            return
        }
        instance = this
        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "✓ TCP server started on port $PORT")
                // Heartbeat every 30s
                launch {
                    while (isActive) {
                        delay(30_000)
                        broadcast("""{"type":"ping"}""")
                    }
                }
                // Accept loop
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    Log.d(TAG, "Client connected: ${socket.inetAddress.hostAddress}")
                    launch { handleClient(socket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                instance = null
            }
        }
    }

    private fun handleClient(socket: Socket) {
        val writer = PrintWriter(socket.getOutputStream(), true)
        clients.add(Pair(socket, writer))

        // Trigger active notification sync from the listener
        NotificationListener.instance?.syncActiveNotifications()

        // Send status on connect so client knows listener state immediately
        sendStatus(writer)

        // Send any already-active notifications
        notifications.values.forEach { writer.println(it.toJson()) }

        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { processClientMessage(it, writer) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client disconnected: ${socket.inetAddress.hostAddress}")
        } finally {
            clients.removeAll { it.first == socket }
            runCatching { socket.close() }
        }
    }

    /** Send a status packet — lets the PC client know if the listener is active */
    fun sendStatus(writer: PrintWriter? = null) {
        val listenerActive = NotificationListener.instance != null
        val json = """{"type":"status","listener_active":$listenerActive,"active_notifications":${notifications.size},"clients":${clients.size}}"""
        if (writer != null) {
            runCatching { writer.println(json) }
        } else {
            broadcast(json)
        }
    }

    private fun processClientMessage(json: String, sender: PrintWriter? = null) {
        try {
            val obj = JSONObject(json)
            when (obj.optString("type")) {
                "action" -> {
                    val key = obj.getString("key")
                    val actionIndex = obj.getInt("action_index")
                    val replyText = obj.optString("reply_text").takeIf { it.isNotEmpty() }
                    sendAction(key, actionIndex, replyText)
                }
                "dismiss" -> {
                    val key = obj.getString("key")
                    NotificationListener.instance?.cancelNotification(key)
                    removeNotification(key)
                }
                "refresh", "list" -> {
                    NotificationListener.instance?.syncActiveNotifications()
                    sendStatus(sender)
                    notifications.values.forEach { sender?.println(it.toJson()) }
                }
                "test" -> {
                    sendTestNotification()
                }
                "status" -> sendStatus(sender)
                "pong"   -> Log.d(TAG, "Pong received from client")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message: $json", e)
        }
    }

    fun broadcast(json: String) {
        val dead = mutableListOf<Pair<Socket, PrintWriter>>()
        clients.forEach { pair ->
            try {
                pair.second.println(json)
            } catch (e: Exception) {
                dead.add(pair)
            }
        }
        clients.removeAll(dead.toSet())
    }

    fun storeNotification(data: NotificationData) {
        notifications[data.key] = data
    }

    fun removeNotification(key: String) {
        notifications.remove(key)
    }

    /** Called by NotificationListener when its connection state changes */
    fun broadcastListenerStatus(connected: Boolean) {
        broadcast("""{"type":"status","listener_active":$connected,"active_notifications":${notifications.size},"clients":${clients.size}}""")
    }

    fun sendAction(key: String, actionIndex: Int, replyText: String?) {
        val data = notifications[key] ?: run {
            Log.w(TAG, "Notification not found: $key")
            return
        }
        val action = data.actions.getOrNull(actionIndex) ?: run {
            Log.w(TAG, "Action index $actionIndex not found")
            return
        }
        try {
            if (action.type == "reply" && replyText != null &&
                action.remoteInputs != null && action.remoteInputResultKey != null
            ) {
                val intent = Intent()
                val bundle = Bundle()
                bundle.putString(action.remoteInputResultKey, replyText)
                android.app.RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
                action.pendingIntent?.send(context, 0, intent)
                Log.d(TAG, "Reply sent: $replyText")
            } else {
                action.pendingIntent?.send()
                Log.d(TAG, "Action fired: ${action.label}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending action", e)
        }
    }

    fun sendTestNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "silent_service_test_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Test Channel", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(context, channelId)
            .setContentTitle("SilentService Test")
            .setContentText("Notification mirroring is working! " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
            .setSmallIcon(R.drawable.ic_service)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(999, notif)
        Log.d(TAG, "Test notification dispatched")
    }

    fun stop() {
        clients.forEach { runCatching { it.first.close() } }
        clients.clear()
        runCatching { serverSocket?.close() }
        instance = null
        Log.d(TAG, "TCP server stopped")
    }
}
