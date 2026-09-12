package com.jp1828.utils

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UpdateChecker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UpdateChecker"
        private const val OWNER = "OWNER"
        private const val REPO = "REPO"
        private const val URL = "https://api.github.com/repos/\$OWNER/\$REPO/releases/latest"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<UpdateChecker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "UpdateCheckerWork",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val client = OkHttpClient()
        val request = Request.Builder().url(URL.replace("\$OWNER", OWNER).replace("\$REPO", REPO)).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch update: \${response.code}")
                return Result.retry()
            }

            val json = JSONObject(response.body?.string() ?: "")
            val tagName = json.getString("tag_name")
            val remoteVersion = tagName.removePrefix("v")
            val localVersion = BuildConfig.VERSION_NAME

            if (remoteVersion != localVersion) {
                val assets = json.getJSONArray("assets")
                var downloadUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (downloadUrl.isNotEmpty()) {
                    downloadAndInstallApk(client, downloadUrl)
                }
            }
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return Result.retry()
        }
    }

    private fun downloadAndInstallApk(client: OkHttpClient, url: String) {
        try {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body ?: return
            
            val updateDir = File(applicationContext.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()
            val apkFile = File(updateDir, "update.apk")
            
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)
            
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            UpdateInstaller.install(applicationContext, apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK", e)
        }
    }
}
