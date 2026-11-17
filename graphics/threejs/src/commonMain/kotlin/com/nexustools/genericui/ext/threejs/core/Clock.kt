package com.nexustools.genericui.ext.threejs.core

import kotlin.time.TimeSource

/**
 * High-resolution animation timer (`THREE.Clock`).
 */
class Clock(
    val autoStart: Boolean = true
) {
    private val timeSource = TimeSource.Monotonic
    private var startMark = timeSource.markNow()
    private var oldMark = startMark
    var elapsedTime: Float = 0f
        private set
    var running: Boolean = false
        private set

    fun start() {
        startMark = timeSource.markNow()
        oldMark = startMark
        elapsedTime = 0f
        running = true
    }

    fun stop() {
        getElapsedTime()
        running = false
    }

    @kotlin.jvm.JvmName("computeElapsedTime")
    fun getElapsedTime(): Float {
        getDelta()
        return elapsedTime
    }

    fun getDelta(): Float {
        var diff = 0f
        if (autoStart && !running) {
            start()
            return 0f
        }
        if (running) {
            val now = timeSource.markNow()
            diff = (now - oldMark).inWholeMicroseconds / 1_000_000f
            oldMark = now
            elapsedTime += diff
        }
        return diff
    }
}
