package com.smstoemail

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsLogStore {

    private const val TAG = "SmsLogStore"
    private const val MAX_INMEM = 100
    private const val LOG_FILE = "sms_log.txt"
    private const val MAX_FILE_LINES = 500

    enum class Level(val prefix: String) {
        SUCCESS("[OK]"),
        RETRY  ("[RETRY]"),
        FAIL   ("[FAIL]"),
        INFO   ("[INFO]");
    }

    data class Entry(
        val ts: Long,
        val level: Level,
        val sender: String,
        val summary: String
    )

    private val cache = ArrayDeque<Entry>(MAX_INMEM)
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    @Synchronized
    fun add(level: Level, sender: String, summary: String) {
        val now = System.currentTimeMillis()
        val e = Entry(now, level, sender, summary)
        if (cache.size >= MAX_INMEM) cache.removeFirst()
        cache.addLast(e)
        try {
            val ctx = SmsApp.appContext ?: return
            val f = File(ctx.filesDir, LOG_FILE)
            val line = "${dateFmt.format(Date(now))} ${level.prefix} $sender $summary"
            FileWriter(f, true).use { it.appendLine(line) }
            val sz = f.length()
            if (sz > 100_000) {
                Thread {
                    try {
                        val all = f.readLines()
                        if (all.size > MAX_FILE_LINES) {
                            f.writeText(all.takeLast(MAX_FILE_LINES).joinToString("\n") + "\n")
                        }
                    } catch (_: Throwable) { }
                }.start()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "write log failed: ${t.message}")
        }
        when (level) {
            Level.FAIL  -> Log.e("SmsToEmail", "FAIL   $sender $summary")
            Level.RETRY -> Log.w("SmsToEmail", "RETRY  $sender $summary")
            Level.SUCCESS -> Log.i("SmsToEmail", "OK     $sender $summary")
            Level.INFO -> Log.i("SmsToEmail", "INFO   $sender $summary")
        }
    }

    @Synchronized
    fun snapshot(): List<Entry> = cache.toList()

    fun format(e: Entry): String =
        "${timeFmt.format(Date(e.ts))} ${e.level.prefix} ${e.sender.take(20).padEnd(20)} ${e.summary}"

    fun clear() {
        synchronized(this) {
            cache.clear()
            try {
                val ctx = SmsApp.appContext ?: return
                val f = File(ctx.filesDir, LOG_FILE)
                if (f.exists()) f.delete()
            } catch (_: Throwable) { }
        }
    }
}
