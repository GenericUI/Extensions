package com.nexustools.genericui.ext.engine.threading

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * [CoroutineDispatcher] and task scheduler bound directly to [PlatformGameLogicThread] so all
 * game simulation ticks and coroutines execute on the dedicated Game Logic Tick Thread.
 */
class GameLogicThreadDispatcher : CoroutineDispatcher() {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + this)

    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        !PlatformGameLogicThread.isOnDedicatedThread()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        PlatformGameLogicThread.dispatch { block.run() }
    }

    fun isOnGameLogicThread(): Boolean = PlatformGameLogicThread.isOnDedicatedThread()

    fun post(block: () -> Unit) {
        if (PlatformGameLogicThread.isOnDedicatedThread()) {
            block()
        } else {
            PlatformGameLogicThread.dispatch(block)
        }
    }

    fun postDelayed(delayMs: Long, block: () -> Unit) {
        PlatformGameLogicThread.dispatchDelayed(delayMs, block)
    }

    fun launchOnGameLogic(block: suspend CoroutineScope.() -> Unit) {
        scope.launch { block() }
    }

    companion object {
        val shared: GameLogicThreadDispatcher = GameLogicThreadDispatcher()
    }
}
