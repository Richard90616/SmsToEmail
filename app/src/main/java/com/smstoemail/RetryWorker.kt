package com.smstoemail

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RetryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            doWorkInner()
        } catch (t: Throwable) {
            Log.e(TAG, "outer crash: ${t.javaClass.simpleName}: ${t.message}", t)
            try { SmsLogStore.add(SmsLogStore.Level.FAIL, "?", "RetryWorker crash: ${t.message}") } catch (_: Throwable) {}
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    private suspend fun doWorkInner(): Result {
        val cfg = try { ConfigManager(applicationContext) } catch (t: Throwable) {
            Log.e(TAG, "ConfigManager failed", t); return Result.failure()
        }
        if (!cfg.enabled || !cfg.isConfigured()) {
            return Result.failure()
        }
        return try {
            val sender = inputData.getString(KEY_SENDER) ?: "unknown"
            val subject = inputData.getString(KEY_SUBJECT) ?: "[SMS]"
            val body = inputData.getString(KEY_BODY) ?: ""
            Log.w(TAG, "retry attempt #$runAttemptCount for $sender")
            val r = EmailService.sendAsync(
                cfg.smtpHost, cfg.smtpPort, cfg.smtpSsl,
                cfg.mailFrom, cfg.mailPassword, cfg.mailTo,
                subject, body
            )
            r.fold(
                onSuccess = {
                    SmsLogStore.add(SmsLogStore.Level.SUCCESS, sender, "retry succeed after $runAttemptCount attempt(s)")
                    Result.success()
                },
                onFailure = { e ->
                    val msg = e.message ?: "unknown"
                    if (runAttemptCount >= 3) {
                        SmsLogStore.add(SmsLogStore.Level.FAIL, sender, "retry failed 3 times: $msg")
                        Notifier.notifyFailure(applicationContext, sender, msg)
                        Result.failure()
                    } else {
                        SmsLogStore.add(SmsLogStore.Level.RETRY, sender, "retry #${runAttemptCount + 1}: $msg")
                        Result.retry()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "RetryWorker exception", e)
            if (runAttemptCount >= 3) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val TAG = "RetryWorker"
        private const val UNIQUE = "sms_retry"
        private const val KEY_SENDER = "sender"
        private const val KEY_SUBJECT = "subject"
        private const val KEY_BODY = "body"

        fun enqueue(ctx: Context, sender: String, subject: String, body: String) {
            val data = Data.Builder()
                .putString(KEY_SENDER, sender)
                .putString(KEY_SUBJECT, subject)
                .putString(KEY_BODY, body)
                .build()
            val req = OneTimeWorkRequestBuilder<RetryWorker>()
                .setInputData(data)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            try {
                WorkManager.getInstance(ctx)
                    .enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
                SmsLogStore.add(SmsLogStore.Level.RETRY, sender, "queued WorkManager (will retry)")
            } catch (t: Throwable) {
                Log.e(TAG, "enqueue failed", t)
            }
        }

        fun enqueueFromJson(ctx: Context, json: String) {
            val o = JSONObject(json)
            enqueue(ctx, o.optString("sender"), o.optString("subject"), o.optString("body"))
        }
    }
}
