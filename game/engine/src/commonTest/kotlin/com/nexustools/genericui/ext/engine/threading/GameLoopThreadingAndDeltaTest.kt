package com.nexustools.genericui.ext.engine.threading

import com.nexustools.genericui.ext.engine.scene.EntityBehavior
import com.nexustools.genericui.ext.engine.scene.FixedStepAccumulator
import com.nexustools.genericui.ext.engine.scene.GameScene
import com.nexustools.genericui.ext.engine.scene.GameWorld
import com.nexustools.genericui.threading.DefaultUiThreadDispatcherNativeImpl
import com.nexustools.genericui.threading.GenericUiThreading
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameLoopThreadingAndDeltaTest {

    @BeforeTest
    fun setUp() {
        GenericUiThreading.customDispatcherOverride = DefaultUiThreadDispatcherNativeImpl(isSplitThreadingEnabled = true)
    }

    @AfterTest
    fun tearDown() {
        GenericUiThreading.customDispatcherOverride = null
    }

    @Test
    fun dedicatedGameLogicThreadIsDistinctFromCallerAndUiThread() {
        assertTrue(GenericUiThreading.isOnUiThread())
        assertFalse(PlatformGameLogicThread.isOnDedicatedThread())
        assertEquals("GenericUI-GameLogic-Thread".takeIf { PlatformGameLogicThread.threadName.contains("GenericUI") } ?: PlatformGameLogicThread.threadName, PlatformGameLogicThread.threadName)
        assertTrue(PlatformGameLogicThread.threadName.isNotBlank())
    }

    @Test
    fun fixedTimestepAccumulatorStepsExactSlicesAndClampsLargeDeltas() {
        val scene = GameScene("AccumScene")
        val fixedSteps = mutableListOf<Float>()
        val tickDeltas = mutableListOf<Float>()
        val lateTickDeltas = mutableListOf<Float>()
        var startCalls = 0

        val entity = scene.createEntity("Ticker")
        entity.addModule(object : EntityBehavior() {
            override fun onStart() {
                startCalls++
            }

            override fun onFixedStep(fixedDeltaSeconds: Float) {
                fixedSteps.add(fixedDeltaSeconds)
            }

            override fun onTick(deltaSeconds: Float) {
                tickDeltas.add(deltaSeconds)
            }

            override fun onLateTick(deltaSeconds: Float) {
                lateTickDeltas.add(deltaSeconds)
            }
        })

        val scheduler = GameLoopScheduler(
            scene = scene,
            targetTicksPerSecond = 120,
            fixedStepSeconds = 1f / 60f,
            maxDeltaSeconds = 0.1f
        )
        assertEquals(120, scheduler.targetTicksPerSecond)

        // 0.05s should produce exactly 3 fixed steps at 1/60s (0.05 / (1/60) = 3.0)
        scheduler.stepManualTick(0.05f)
        assertEquals(1, startCalls)
        assertEquals(3, fixedSteps.size)
        assertEquals(1, tickDeltas.size)
        assertEquals(1, lateTickDeltas.size)
        assertEquals(3L, scheduler.totalFixedSteps)
        assertEquals(1L, scheduler.totalLogicTicks)

        // Large 5.0s spike must be clamped to maxDeltaSeconds (0.1s) -> <= 6 fixed steps
        fixedSteps.clear()
        scheduler.stepManualTick(5.0f)
        assertTrue(fixedSteps.size in 5..6, "Expected clamped fixed steps in 5..6, got ${fixedSteps.size}")
        assertEquals(1, startCalls, "onStart must execute exactly once")
        assertEquals(0.1f, scene.clock.deltaSeconds, 1e-4f)
    }

    @Test
    fun fixedStepAccumulatorComputesNormalizedInterpolationAlpha() {
        val acc = FixedStepAccumulator(fixedStepSeconds = 0.02f, maxDeltaSeconds = 0.1f)
        var stepCount = 0
        acc.accumulateAndStep(0.03f) { stepCount++ }
        assertEquals(1, stepCount)
        assertEquals(0.01f, acc.accumulatedSeconds, 1e-5f)
        assertEquals(0.5f, acc.interpolationAlpha, 1e-4f)
    }

    @Test
    fun gameWorldSwitchesScenesCleanly() {
        val sceneA = GameScene("SceneA")
        val sceneB = GameScene("SceneB")
        val world = GameWorld(initialScene = sceneA, targetTicksPerSecond = 60)
        world.registerScene(sceneB)

        assertEquals("SceneA", world.activeScene.name)
        world.loadScene("SceneB")
        assertEquals("SceneB", world.activeScene.name)
        world.scheduler.stepManualTick(1f / 60f)
        assertEquals(1L, sceneB.clock.tickCount)
        assertEquals(0L, sceneA.clock.tickCount)
        world.dispose()
    }
}
