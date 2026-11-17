package com.takaroute.smscompanion

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** HTTPS upload with exponential WorkManager retry. Never prints credentials or SMS. */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object { const val PAYLOAD_KEY = "payload_json" }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = SettingsStore(applicationContext)
        if (!settings.enabled) return@withContext Result.success()
        val payload = inputData.getString(PAYLOAD_KEY) ?: return@withContext Result.failure()
        val token = settings.getToken() ?: return@withContext Result.failure()
        val origin = settings.apiBase
        if (!origin.startsWith("https://", ignoreCase = true)) return@withContext Result.failure()
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("$origin/v1/sms-events").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12000
                readTimeout = 12000
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $token")
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            return@withContext when {
                status in 200..299 || status == 409 -> Result.success()
                status == 408 || status == 429 || status >= 500 -> Result.retry()
                else -> Result.failure() // Invalid config/token: don't retry forever.
            }
        } catch (_: Exception) {
            Result.retry()
        } finally {
            conn?.disconnect()
        }
    }
}
