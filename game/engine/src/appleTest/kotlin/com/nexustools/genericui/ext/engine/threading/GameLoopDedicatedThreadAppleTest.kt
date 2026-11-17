package com.nexustools.genericui.ext.engine.threading

import com.nexustools.genericui.ext.engine.scene.EntityBehavior
import com.nexustools.genericui.ext.engine.scene.GameScene
import platform.Foundation.NSThread
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameLoopDedicatedThreadAppleTest {

    @Test
    fun asyncTickRunsOnDedicatedGcdGameLogicQueue() {
        val scene = GameScene("AppleThreadTest")
        var onDedicated = false
        var wasMainThread = true

        scene.createEntity("Runner").addModule(object : EntityBehavior() {
            override fun onTick(deltaSeconds: Float) {
                onDedicated = PlatformGameLogicThread.isOnDedicatedThread()
                wasMainThread = NSThread.isMainThread
            }
        })

        val scheduler = GameLoopScheduler(scene = scene, targetTicksPerSecond = 60)
        val sem = dispatch_semaphore_create(0)
        scheduler.stepAsyncTickOnLogicThread(1f / 60f) {
            sem?.let { dispatch_semaphore_signal(it) }
        }
        sem?.let { dispatch_semaphore_wait(it, DISPATCH_TIME_FOREVER) }

        assertTrue(onDedicated)
        assertFalse(wasMainThread)
        assertTrue(scheduler.lastTickExecutedOnDedicatedThread)
    }
}
