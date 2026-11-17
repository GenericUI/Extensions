package com.nexustools.genericui.ext.engine.threading

import com.nexustools.genericui.ext.engine.scene.EntityBehavior
import com.nexustools.genericui.ext.engine.scene.GameScene
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class GameLoopDedicatedThreadJvmTest {

    @Test
    fun asyncTickRunsOnDedicatedDaemonGameLogicThread() {
        val callerThread = Thread.currentThread()
        val scene = GameScene("JvmThreadTest")
        var tickThread: Thread? = null
        var onDedicated = false

        scene.createEntity("Runner").addModule(object : EntityBehavior() {
            override fun onTick(deltaSeconds: Float) {
                tickThread = Thread.currentThread()
                onDedicated = PlatformGameLogicThread.isOnDedicatedThread()
            }
        })

        val scheduler = GameLoopScheduler(scene = scene, targetTicksPerSecond = 60)
        val latch = CountDownLatch(1)
        scheduler.stepAsyncTickOnLogicThread(1f / 60f) {
            latch.countDown()
        }

        assertTrue(latch.await(3, TimeUnit.SECONDS), "Dedicated GameLogic thread tick timed out")
        assertTrue(onDedicated, "Tick must report PlatformGameLogicThread.isOnDedicatedThread() == true")
        assertNotSame(callerThread, tickThread)
        assertTrue(tickThread!!.isDaemon)
        assertTrue(scheduler.lastTickExecutedOnDedicatedThread)
    }
}
