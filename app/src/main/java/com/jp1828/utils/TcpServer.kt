package com.jp1828.utils

import android.content.Context
import android.os.Bundle
import android.util.Log
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
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<Pair<Socket, PrintWriter>>()
    private val notifications = ConcurrentHashMap<String, NotificationData>()

    fun start() {
        instance = this
        scope.launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d(TAG, "TCP server started on port $PORT")
                launch {
                    while (isActive) {
                        delay(30_000)
                        broadcast("""{"type":"ping"}""")
                    }
                }
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    Log.d(TAG, "Client connected: ${socket.inetAddress.hostAddress}")
                    launch { handleClient(socket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        val writer = PrintWriter(socket.getOutputStream(), true)
        clients.add(Pair(socket, writer))
        notifications.values.forEach { writer.println(it.toJson()) }
        try {
            val reader = BufferedReader(InputStreamReader(socket.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { processClientMessage(it) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client disconnected: ${socket.inetAddress.hostAddress}")
        } finally {
            clients.removeAll { it.first == socket }
            runCatching { socket.close() }
        }
    }

    private fun processClientMessage(json: String) {
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
                "pong" -> Log.d(TAG, "Pong received")
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

    fun storeNotification(data: NotificationData) { notifications[data.key] = data }
    fun removeNotification(key: String) { notifications.remove(key) }

    fun sendAction(key: String, actionIndex: Int, replyText: String?) {
        val data = notifications[key] ?: run { Log.w(TAG, "Notification not found: $key"); return }
        val action = data.actions.getOrNull(actionIndex) ?: run { Log.w(TAG, "Action $actionIndex not found"); return }
        try {
            if (action.type == "reply" && replyText != null && action.remoteInputs != null && action.remoteInputResultKey != null) {
                val intent = android.content.Intent()
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

    fun stop() {
        clients.forEach { runCatching { it.first.close() } }
        clients.clear()
        runCatching { serverSocket?.close() }
        instance = null
        Log.d(TAG, "TCP server stopped")
    }
}
