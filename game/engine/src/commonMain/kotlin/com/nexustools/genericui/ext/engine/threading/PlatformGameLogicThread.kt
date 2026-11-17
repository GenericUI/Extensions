package com.nexustools.genericui.ext.engine.threading

/**
 * Platform-native dedicated **Game Logic Tick Thread** abstraction completely separate from the UI
 * rendering thread, VBlank animation evaluation, and GPU frame presentation.
 */
expect object PlatformGameLogicThread {
    val threadName: String

    /**
     * Returns `true` when invoked on the dedicated Game Logic Tick Thread.
     */
    fun isOnDedicatedThread(): Boolean

    /**
     * Dispatches [task] for asynchronous execution on the dedicated Game Logic Tick Thread.
     */
    fun dispatch(task: () -> Unit)

    /**
     * Schedules [task] to execute on the dedicated Game Logic Tick Thread after [delayMs] milliseconds.
     */
    fun dispatchDelayed(delayMs: Long, task: () -> Unit)

    /**
     * Requests graceful shutdown of the underlying background worker/executor when no longer needed.
     */
    fun shutdown()
}
