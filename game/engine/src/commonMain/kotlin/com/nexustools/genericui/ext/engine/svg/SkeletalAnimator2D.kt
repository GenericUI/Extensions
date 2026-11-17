package com.nexustools.genericui.ext.engine.svg

import com.nexustools.genericui.ext.engine.animation.AnimationClip
import com.nexustools.genericui.ext.engine.animation.AnimationScheduler
import com.nexustools.genericui.ext.engine.animation.AnimationWrapMode
import com.nexustools.genericui.ext.engine.animation.LambdaAnimationClip
import kotlin.math.floor

/**
 * Evaluates [SkeletalAnimationClip2D] clips onto [skeleton] with cross-fade blending, VBlank
 * synchronization via [AnimationScheduler], and post-FK [TwoBoneIkSolver2D] IK overrides.
 */
class SkeletalAnimator2D(
    val skeleton: Skeleton2D
) {
    var activeClip: SkeletalAnimationClip2D? = null
        private set

    private var previousClip: SkeletalAnimationClip2D? = null
    private var activeElapsedSeconds: Float = 0f
    private var previousElapsedSeconds: Float = 0f
    private var crossFadeDurationSeconds: Float = 0f
    private var crossFadeElapsedSeconds: Float = 0f

    private val ikTargets = LinkedHashMap<String, IkTarget2D>()
    private var activeScheduledClip: AnimationClip? = null
    private var boundScheduler: AnimationScheduler? = null

    fun setIkTarget(
        upperBoneName: String,
        lowerBoneName: String,
        targetX: Float,
        targetY: Float,
        bendDirection: Float = 1f,
        weight: Float = 1f
    ): IkTarget2D {
        val key = "$upperBoneName->$lowerBoneName"
        val target = IkTarget2D(upperBoneName, lowerBoneName, targetX, targetY, bendDirection, weight)
        ikTargets[key] = target
        return target
    }

    fun clearIkTarget(upperBoneName: String, lowerBoneName: String) {
        ikTargets.remove("$upperBoneName->$lowerBoneName")
    }

    fun clearAllIkTargets() {
        ikTargets.clear()
    }

    fun play(
        clip: SkeletalAnimationClip2D,
        crossFadeSeconds: Float = 0f,
        scheduler: AnimationScheduler? = null
    ) {
        if (crossFadeSeconds > 0f && activeClip != null) {
            previousClip = activeClip
            previousElapsedSeconds = activeElapsedSeconds
            crossFadeDurationSeconds = crossFadeSeconds
            crossFadeElapsedSeconds = 0f
        } else {
            previousClip = null
            crossFadeDurationSeconds = 0f
            crossFadeElapsedSeconds = 0f
        }
        activeClip = clip
        activeElapsedSeconds = 0f

        if (scheduler != null) {
            attachToScheduler(scheduler, clip)
        }
    }

    /**
     * Binds playback onto [scheduler] so every VBlank render callback evaluates the skeletal pose
     * automatically at exact `0.0f..1.0f` progress without drift.
     */
    fun attachToScheduler(scheduler: AnimationScheduler, clip: SkeletalAnimationClip2D? = activeClip) {
        activeScheduledClip?.let { boundScheduler?.stop(it) }
        val targetClip = clip ?: return
        boundScheduler = scheduler
        val scheduled = LambdaAnimationClip(
            durationSeconds = targetClip.durationSeconds,
            wrapMode = targetClip.wrapMode
        ) { progress ->
            evaluateAtProgress(progress)
        }
        activeScheduledClip = scheduled
        scheduler.play(scheduled)
    }

    /**
     * Evaluates the active skeletal clip directly at normalized [progress] (`0f..1f`) plus IK.
     */
    fun evaluateAtProgress(progress: Float) {
        val clip = activeClip
        if (clip != null) {
            val p = progress.coerceIn(0f, 1f)
            for (track in clip.tracks) {
                val bone = skeleton.getBone(track.boneName) ?: continue
                bone.localAngleRad = track.sampleAngleRad(p)
            }
        }
        skeleton.updateWorldTransforms()
        applyIkTargets()
    }

    /**
     * Advances animation elapsed time by [deltaSeconds], handles cross-fade blending, updates FK
     * world transforms, and applies post-FK IK overrides.
     */
    fun stepTime(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceAtLeast(0f)
        val current = activeClip
        if (current != null) {
            activeElapsedSeconds += dt
            val currProg = resolveProgress(current, activeElapsedSeconds)
            val prev = previousClip
            if (prev != null && crossFadeDurationSeconds > 0f) {
                previousElapsedSeconds += dt
                crossFadeElapsedSeconds += dt
                val blend = (crossFadeElapsedSeconds / crossFadeDurationSeconds).coerceIn(0f, 1f)
                val prevProg = resolveProgress(prev, previousElapsedSeconds)
                val prevMap = HashMap<String, Float>()
                for (t in prev.tracks) {
                    prevMap[t.boneName] = t.sampleAngleRad(prevProg)
                }
                for (t in current.tracks) {
                    val bone = skeleton.getBone(t.boneName) ?: continue
                    val targetAngle = t.sampleAngleRad(currProg)
                    val prevAngle = prevMap[t.boneName] ?: bone.localAngleRad
                    bone.localAngleRad = prevAngle + (targetAngle - prevAngle) * blend
                }
                if (blend >= 1f) {
                    previousClip = null
                    crossFadeDurationSeconds = 0f
                }
            } else {
                for (t in current.tracks) {
                    val bone = skeleton.getBone(t.boneName) ?: continue
                    bone.localAngleRad = t.sampleAngleRad(currProg)
                }
            }
        }
        skeleton.updateWorldTransforms()
        applyIkTargets()
    }

    private fun applyIkTargets() {
        if (ikTargets.isEmpty()) return
        for (target in ikTargets.values) {
            TwoBoneIkSolver2D.applyToBones(
                skeleton = skeleton,
                upperBoneName = target.upperBoneName,
                lowerBoneName = target.lowerBoneName,
                targetX = target.targetX,
                targetY = target.targetY,
                bendDirection = target.bendDirection,
                weight = target.weight
            )
        }
    }

    private fun resolveProgress(clip: SkeletalAnimationClip2D, elapsedSec: Float): Float {
        val dur = clip.durationSeconds.coerceAtLeast(0.001f)
        val cycles = elapsedSec / dur
        return when (clip.wrapMode) {
            AnimationWrapMode.ONCE, AnimationWrapMode.CLAMP_FOREVER -> cycles.coerceIn(0f, 1f)
            AnimationWrapMode.LOOP -> (cycles - floor(cycles)).coerceIn(0f, 1f)
            AnimationWrapMode.PING_PONG -> {
                val cIdx = floor(cycles).toLong()
                val frac = (cycles - cIdx).coerceIn(0f, 1f)
                if ((cIdx and 1L) == 1L) 1f - frac else frac
            }
        }
    }
}
