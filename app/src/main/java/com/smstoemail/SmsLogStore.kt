package com.smstoemail

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 短信转发日志环形缓冲 (加固版)。
 *
 * 改动要点（修闪退）：
 *  1. 文件 IO 全部移到 HandlerThread (sms-log-io) 上, 永远不在调用方线程磁盘写
 *  2. 内部对每个可抛异常的步骤做 try/catch, 任何 IO 异常都被吞掉 (日志不能拖死广播)
 *  3. Application 上下文可能未初始化, 提前返回而不是抛 NullPointerException
 */
object SmsLogStore {

    private const val TAG = "SmsLogStore"
    private const val MAX_INMEM = 100
    private const val LOG_FILE = "sms_log.txt"
    private const val MAX_FILE_LINES = 500
    private const val MAX_FILE_BYTES = 100_000L  // 100 KB 触发截断

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

    // 异步 IO 线程；线程结束后 lazy 重建
    @Volatile private var ioThread: HandlerThread? = null
    @Volatile private var ioHandler: Handler? = null
    private val ioStarted = AtomicBoolean(false)

    private fun ensureIoThread(): Handler? {
        if (ioStarted.get()) return ioHandler
        synchronized(this) {
            if (ioStarted.get()) return ioHandler
            try {
                val t = HandlerThread("sms-log-io").apply { start() }
                ioThread = t
                ioHandler = Handler(t.looper)
                ioStarted.set(true)
            } catch (t: Throwable) {
                Log.w(TAG, "HandlerThread failed: ${t.message}")
            }
        }
        return ioHandler
    }

    @Synchronized
    fun add(level: Level, sender: String, summary: String) {
        // 1) 内存 (轻量, 必定成功)
        val now = System.currentTimeMillis()
        val e = Entry(now, level, sender, summary)
        try {
            if (cache.size >= MAX_INMEM) cache.removeFirst()
            cache.addLast(e)
        } catch (_: Throwable) { }

        // 2) logcat (永不抛)
        try {
            val line = "${level.prefix.removeSurrounding("[", "]")} $sender $summary"
            when (level) {
                Level.FAIL  -> Log.e("SmsToEmail", "FAIL   $line")
                Level.RETRY -> Log.w("SmsToEmail", "RETRY  $line")
                Level.SUCCESS -> Log.i("SmsToEmail", "OK     $line")
                Level.INFO -> Log.i("SmsToEmail", "INFO   $line")
            }
        } catch (_: Throwable) { }

        // 3) 文件 IO (异步, 任何异常吞掉)
        val ctx = SmsApp.appContext
        if (ctx == null) return  // 应用未启动, 只留内存 + logcat
        val handler = ensureIoThread() ?: return
        try {
            val text = "${dateFmt.format(Date(now))} ${level.prefix} $sender $summary"
            handler.post {
                runCatching {
                    val f = File(ctx.filesDir, LOG_FILE)
                    FileWriter(f, true).use { it.appendLine(text) }
                    if (f.length() > MAX_FILE_BYTES) {
                        val lines = f.readLines()
                        if (lines.size > MAX_FILE_LINES) {
                            f.writeText(lines.takeLast(MAX_FILE_LINES).joinToString("\n") + "\n")
                        }
                    }
                }.onFailure {
                    Log.w(TAG, "async write failed: ${it.message}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "schedule write failed: ${t.message}")
        }
    }

    @Synchronized
    fun snapshot(): List<Entry> = cache.toList()

    fun format(e: Entry): String =
        "${timeFmt.format(Date(e.ts))} ${e.level.prefix} ${e.sender.take(20)} ${e.summary}"

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
