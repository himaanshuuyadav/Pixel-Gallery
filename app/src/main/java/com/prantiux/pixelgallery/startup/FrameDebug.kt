package com.prantiux.pixelgallery.startup

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Printer

object FrameDebug {
    const val TAG = "FrameDebug"
    private const val MAIN_THREAD_BLOCK_THRESHOLD_MS = 16L

    private var installed = false
    private var dispatchStartMs = 0L
    private val currentSection = ThreadLocal<String?>()

    @Synchronized
    fun installMainThreadBlockDetector() {
        if (installed) return
        installed = true

        Looper.getMainLooper().setMessageLogging(Printer { message ->
            if (message.startsWith(">>>>> Dispatching")) {
                dispatchStartMs = SystemClock.elapsedRealtime()
            } else if (message.startsWith("<<<<< Finished")) {
                val durationMs = SystemClock.elapsedRealtime() - dispatchStartMs
                if (durationMs > MAIN_THREAD_BLOCK_THRESHOLD_MS) {
                    val section = currentSection.get() ?: "main-thread dispatch"
                    Log.d(TAG, "MainThreadBlock: ${durationMs}ms in $section")
                }
            }
        })
    }

    fun <T> trace(section: String, tag: String = TAG, block: () -> T): T {
        val isMainThread = Looper.myLooper() == Looper.getMainLooper()
        val previousSection = if (isMainThread) currentSection.get() else null
        if (isMainThread) {
            currentSection.set(section)
        }

        val startMs = SystemClock.elapsedRealtime()
        try {
            return block()
        } finally {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.d(tag, "Trace: $section ${durationMs}ms")
            if (isMainThread) {
                currentSection.set(previousSection)
            }
        }
    }
}
