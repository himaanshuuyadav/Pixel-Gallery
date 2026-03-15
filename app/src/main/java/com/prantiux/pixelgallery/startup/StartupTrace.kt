package com.prantiux.pixelgallery.startup

import android.os.SystemClock
import android.util.Log

object StartupTrace {
    const val TAG = "StartupTrace"

    private val processStartElapsedMs = SystemClock.elapsedRealtime()
    private val processStartWallMs = System.currentTimeMillis()
    private var lastStageElapsedMs = processStartElapsedMs
    private val loggedStages = mutableSetOf<String>()

    @Synchronized
    fun markStage(stage: String, once: Boolean = false) {
        if (once && !loggedStages.add(stage)) return

        val nowElapsedMs = SystemClock.elapsedRealtime()
        val nowWallMs = System.currentTimeMillis()
        val fromProcessStartMs = nowElapsedMs - processStartElapsedMs
        val fromPreviousStageMs = nowElapsedMs - lastStageElapsedMs

        Log.d(
            TAG,
            "$stage at: $nowWallMs (process+$fromProcessStartMs ms, stage+$fromPreviousStageMs ms, processStartWall=$processStartWallMs)"
        )

        lastStageElapsedMs = nowElapsedMs
    }
}
