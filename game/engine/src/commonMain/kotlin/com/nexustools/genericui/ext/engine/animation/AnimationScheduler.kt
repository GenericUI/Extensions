package com.nexustools.genericui.ext.engine.animation

/**
 * Manages active [AnimationClip] playback and evaluates all active clips directly inside
 * each VBlank render frame via [evaluateAtVBlank].
 */
class AnimationScheduler {
    private val clips = mutableListOf<AnimationClip>()

    val activeClipCount: Int
        get() = clips.size

    var lastEvaluatedFrameIndex: Long = -1L
        private set

    var lastEvaluatedTimestampMs: Double = 0.0
        private set

    fun play(clip: AnimationClip, startTimestampMs: Double? = null): AnimationClip {
        clip.resetPlayback(startTimestampMs)
        if (clip !in clips) {
            clips.add(clip)
        }
        return clip
    }

    fun playLambda(
        durationSeconds: Float,
        curve: AnimationCurve = EasingFunction.LINEAR,
        wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
        onComplete: (() -> Unit)? = null,
        onProgress: (progress: Float) -> Unit
    ): LambdaAnimationClip {
        val clip = LambdaAnimationClip(
            durationSeconds = durationSeconds,
            curve = curve,
            wrapMode = wrapMode,
            onComplete = onComplete,
            onProgress = onProgress
        )
        play(clip)
        return clip
    }

    fun pause(clip: AnimationClip) {
        clip.pause(if (lastEvaluatedFrameIndex >= 0L) lastEvaluatedTimestampMs else null)
    }

    fun resume(clip: AnimationClip) {
        clip.resume(if (lastEvaluatedFrameIndex >= 0L) lastEvaluatedTimestampMs else null)
    }

    fun stop(clip: AnimationClip) {
        clips.remove(clip)
    }

    fun stopAll() {
        clips.clear()
    }

    /**
     * Evaluates every active clip using the hardware VBlank [vblankTimestampMs].
     * Finished `ONCE` clips are automatically pruned after invoking their completion callback.
     */
    fun evaluateAtVBlank(frameIndex: Long, vblankTimestampMs: Double) {
        lastEvaluatedFrameIndex = frameIndex
        lastEvaluatedTimestampMs = vblankTimestampMs
        if (clips.isEmpty()) return

        val snapshot = clips.toList()
        for (clip in snapshot) {
            if (!clip.isFinished) {
                clip.evaluateAtVBlank(vblankTimestampMs)
            }
            if (clip.isFinished && clip.wrapMode == AnimationWrapMode.ONCE) {
                clips.remove(clip)
            }
        }
    }
}
