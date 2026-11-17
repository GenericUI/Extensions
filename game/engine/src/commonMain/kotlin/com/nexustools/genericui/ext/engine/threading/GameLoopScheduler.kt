package com.nexustools.genericui.ext.engine.threading

import com.nexustools.genericui.ext.engine.scene.FixedStepAccumulator
import com.nexustools.genericui.ext.engine.scene.GameScene
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * Drives [scene] simulation ticks (`onFixedStep` via [FixedStepAccumulator] + `onTick` + `onLateTick`)
 * on the dedicated Game Logic Tick Thread ([PlatformGameLogicThread]), decoupled from UI Thread,
 * VBlank Render-Thread animations, and GPU presentation.
 */
class GameLoopScheduler(
    val scene: GameScene,
    targetTicksPerSecond: Int = 60,
    fixedStepSeconds: Float = 1f / 60f,
    maxDeltaSeconds: Float = 0.1f
) {
    var targetTicksPerSecond: Int = targetTicksPerSecond.coerceIn(1, 240)
        set(value) {
            field = value.coerceIn(1, 240)
        }

    val fixedAccumulator: FixedStepAccumulator = FixedStepAccumulator(
        fixedStepSeconds = fixedStepSeconds,
        maxDeltaSeconds = maxDeltaSeconds
    )

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var isPaused: Boolean = false
        private set

    @Volatile
    var totalLogicTicks: Long = 0L
        private set

    val totalFixedSteps: Long
        get() = scene.clock.fixedStepCount

    @Volatile
    var lastTickThreadName: String = ""
        private set

    @Volatile
    var lastTickExecutedOnDedicatedThread: Boolean = false
        private set

    @Volatile
    private var sessionToken: Long = 0L

    private var lastTickMark: TimeSource.Monotonic.ValueTimeMark? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        isPaused = false
        val token = ++sessionToken
        lastTickMark = TimeSource.Monotonic.markNow()
        PlatformGameLogicThread.dispatch {
            runLoopStep(token)
        }
    }

    fun stop() {
        isRunning = false
        sessionToken++
    }

    fun pause() {
        isPaused = true
        scene.clock.paused = true
    }

    fun resume() {
        isPaused = false
        scene.clock.paused = false
        lastTickMark = TimeSource.Monotonic.markNow()
    }

    /**
     * Executes a single synchronous deterministic tick on the current thread (ideal for unit tests).
     */
    fun stepManualTick(deltaSeconds: Float) {
        val clamped = deltaSeconds.coerceIn(0f, fixedAccumulator.maxDeltaSeconds)
        lastTickExecutedOnDedicatedThread = PlatformGameLogicThread.isOnDedicatedThread()
        lastTickThreadName = PlatformGameLogicThread.threadName
        scene.stepLogicTick(clamped, fixedAccumulator)
        totalLogicTicks++
    }

    /**
     * Schedules a single tick with explicit [deltaSeconds] onto the dedicated Game Logic Tick Thread.
     */
    fun stepAsyncTickOnLogicThread(
        deltaSeconds: Float,
        onCompleted: (() -> Unit)? = null
    ) {
        PlatformGameLogicThread.dispatch {
            stepManualTick(deltaSeconds)
            onCompleted?.invoke()
        }
    }

    private fun runLoopStep(expectedToken: Long) {
        if (!isRunning || sessionToken != expectedToken) return

        val now = TimeSource.Monotonic.markNow()
        val prev = lastTickMark
        lastTickMark = now
        val rawDt = if (prev != null) {
            (now - prev).inWholeMicroseconds / 1_000_000f
        } else {
            1f / targetTicksPerSecond.toFloat()
        }

        if (!isPaused) {
            lastTickExecutedOnDedicatedThread = PlatformGameLogicThread.isOnDedicatedThread()
            lastTickThreadName = PlatformGameLogicThread.threadName
            scene.stepLogicTick(rawDt.coerceIn(0f, fixedAccumulator.maxDeltaSeconds), fixedAccumulator)
            totalLogicTicks++
        }

        if (isRunning && sessionToken == expectedToken) {
            val intervalMs = (1000L / targetTicksPerSecond.toLong()).coerceAtLeast(1L)
            PlatformGameLogicThread.dispatchDelayed(intervalMs) {
                runLoopStep(expectedToken)
            }
        }
    }
}
