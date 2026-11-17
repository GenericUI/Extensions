package com.nexustools.genericui.ext.engine.animation

import com.nexustools.genericui.core.FramePresentTarget
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VBlankAnimationAndCurvesTest {

    private class FakeFramePresentTarget : FramePresentTarget {
        override var presentedFrameCount: Long = 0L
        private val listeners = mutableListOf<(Long, Double) -> Unit>()

        override fun addBeforeFramePresentListener(listener: (frameIndex: Long, timestampMs: Double) -> Unit) {
            listeners.add(listener)
        }

        override fun removeBeforeFramePresentListener(listener: (frameIndex: Long, timestampMs: Double) -> Unit) {
            listeners.remove(listener)
        }

        fun fireVBlank(frameIndex: Long, timestampMs: Double) {
            presentedFrameCount = frameIndex
            for (listener in listeners.toList()) {
                listener(frameIndex, timestampMs)
            }
        }
    }

    @Test
    fun all31EasingFunctionsMatchExactEndpointsAndFiniteMidpoints() {
        assertEquals(31, EasingFunction.entries.size)
        for (easing in EasingFunction.entries) {
            assertEquals(0f, easing.sample(0f), 1e-5f, "Endpoint 0f failed for $easing")
            assertEquals(1f, easing.sample(1f), 1e-5f, "Endpoint 1f failed for $easing")
            val mid = easing.sample(0.5f)
            assertTrue(mid.isFinite(), "Midpoint must be finite for $easing")
            assertTrue(mid in -0.5f..1.5f, "Unexpected midpoint $mid for $easing")
        }
    }

    @Test
    fun cubicBezierSpringWaveAndHermiteCurvesEvaluateAccurately() {
        val bezier = CubicBezierCurve(0.42f, 0f, 0.58f, 1f)
        assertEquals(0f, bezier.sample(0f), 1e-4f)
        assertEquals(0.5f, bezier.sample(0.5f), 1e-2f)
        assertEquals(1f, bezier.sample(1f), 1e-4f)

        val spring = SpringDamperCurve(stiffness = 220f, dampingRatio = 0.65f, settlingDurationSeconds = 1.0f)
        assertEquals(0f, spring.sample(0f), 1e-4f)
        assertEquals(1f, spring.sample(1f), 1e-4f)
        // Underdamped spring overshoots > 1.0 before settling
        val peak = (1..20).maxOf { spring.sample(it / 20f) }
        assertTrue(peak > 1.0f, "Expected underdamped spring overshoot > 1f, got $peak")

        val wave = WaveMathCurve(frequencyHz = 1f, amplitude = 2f, shape = WaveShape.SINE)
        assertEquals(0f, wave.sample(0f), 1e-4f)
        assertEquals(2f, wave.sample(0.25f), 1e-3f)
        assertEquals(-2f, wave.sample(0.75f), 1e-3f)

        val spline = HermiteSplineCurve(
            listOf(
                HermiteKeyframe(0f, 10f, outTangent = 0f),
                HermiteKeyframe(0.5f, 50f, inTangent = 0f, outTangent = 0f),
                HermiteKeyframe(1f, 20f, inTangent = 0f)
            )
        )
        assertEquals(10f, spline.sample(0f), 1e-4f)
        assertEquals(50f, spline.sample(0.5f), 1e-4f)
        assertEquals(20f, spline.sample(1f), 1e-4f)
    }

    @Test
    fun vblankDriverAndLambdaClipDeliverZeroDriftMonotonicProgressOnFramePresentTarget() {
        val target = FakeFramePresentTarget()
        val scheduler = AnimationScheduler()
        val driver = VBlankAnimationDriver(scheduler)
        driver.bindToFramePresentTarget(target)

        val recorded = mutableListOf<Float>()
        var completed = false
        scheduler.playLambda(
            durationSeconds = 1.0f,
            curve = EasingFunction.LINEAR,
            wrapMode = AnimationWrapMode.ONCE,
            onComplete = { completed = true }
        ) { progress ->
            recorded.add(progress)
        }

        assertEquals(1, scheduler.activeClipCount)

        target.fireVBlank(1L, 1000.0)
        target.fireVBlank(2L, 1250.0)
        target.fireVBlank(3L, 1500.0)
        target.fireVBlank(4L, 1750.0)
        target.fireVBlank(5L, 2000.0)

        assertEquals(5, recorded.size)
        assertEquals(0.0f, recorded[0], 1e-5f)
        assertEquals(0.25f, recorded[1], 1e-5f)
        assertEquals(0.50f, recorded[2], 1e-5f)
        assertEquals(0.75f, recorded[3], 1e-5f)
        assertEquals(1.0f, recorded[4], 1e-5f)
        assertTrue(completed)
        assertEquals(0, scheduler.activeClipCount)

        for (i in 1 until recorded.size) {
            assertTrue(recorded[i] > recorded[i - 1], "Progress must increase strictly monotonically")
        }
        driver.unbind()
    }

    @Test
    fun wrapModesPingPongLoopAndSequentialTimelineWorkCorrectly() {
        val scheduler = AnimationScheduler()
        val pingPongValues = mutableListOf<Float>()
        val clip = scheduler.playLambda(
            durationSeconds = 1.0f,
            curve = EasingFunction.LINEAR,
            wrapMode = AnimationWrapMode.PING_PONG
        ) { p ->
            pingPongValues.add(p)
        }

        scheduler.evaluateAtVBlank(1L, 0.0)
        scheduler.evaluateAtVBlank(2L, 500.0)
        scheduler.evaluateAtVBlank(3L, 1500.0)
        scheduler.evaluateAtVBlank(4L, 2000.0)
        assertEquals(0f, pingPongValues[0], 1e-4f)
        assertEquals(0.5f, pingPongValues[1], 1e-4f)
        assertEquals(0.5f, pingPongValues[2], 1e-4f)
        assertTrue(abs(pingPongValues[3] - 0f) < 1e-4f)

        scheduler.stop(clip)

        var firstVal = 0f
        var secondVal = 0f
        val group = AnimationTimelineGroup(
            mode = TimelinePlaybackMode.SEQUENTIAL,
            children = listOf(
                LambdaAnimationClip(0.5f) { firstVal = it },
                LambdaAnimationClip(0.5f) { secondVal = it }
            )
        )
        scheduler.play(group)
        scheduler.evaluateAtVBlank(5L, 0.0)
        scheduler.evaluateAtVBlank(6L, 250.0)
        assertEquals(0.5f, firstVal, 1e-4f)
        assertEquals(0f, secondVal, 1e-4f)

        scheduler.evaluateAtVBlank(7L, 750.0)
        assertEquals(1f, firstVal, 1e-4f)
        assertEquals(0.5f, secondVal, 1e-4f)
    }
}
