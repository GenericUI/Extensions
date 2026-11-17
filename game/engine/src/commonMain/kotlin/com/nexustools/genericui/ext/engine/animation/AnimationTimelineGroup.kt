package com.nexustools.genericui.ext.engine.animation

/**
 * Composite [AnimationClip] that runs child clips either sequentially or in parallel.
 */
class AnimationTimelineGroup(
    val mode: TimelinePlaybackMode = TimelinePlaybackMode.SEQUENTIAL,
    val children: List<AnimationClip>,
    curve: AnimationCurve = EasingFunction.LINEAR,
    wrapMode: AnimationWrapMode = AnimationWrapMode.ONCE,
    onComplete: (() -> Unit)? = null
) : AnimationClip(
    durationSeconds = computeTotalDuration(mode, children),
    curve = curve,
    wrapMode = wrapMode,
    onComplete = onComplete
) {
    override fun applyShapedProgress(shapedProgress: Float, rawProgress: Float) {
        if (children.isEmpty()) return
        val totalDurationMs = durationSeconds.toDouble() * 1000.0
        val virtualElapsedMs = shapedProgress.toDouble() * totalDurationMs

        when (mode) {
            TimelinePlaybackMode.PARALLEL -> {
                for (child in children) {
                    child.resetPlayback(newStartTimestampMs = 0.0)
                    child.evaluateAtVBlank(virtualElapsedMs)
                }
            }
            TimelinePlaybackMode.SEQUENTIAL -> {
                var cursorMs = 0.0
                for (child in children) {
                    val childDurMs = child.durationSeconds.toDouble() * 1000.0
                    val childEndMs = cursorMs + childDurMs
                    if (virtualElapsedMs >= cursorMs) {
                        val localMs = (virtualElapsedMs - cursorMs).coerceAtMost(childDurMs)
                        child.resetPlayback(newStartTimestampMs = 0.0)
                        child.evaluateAtVBlank(localMs)
                    } else if (child.lastRawProgress > 0f && virtualElapsedMs < cursorMs) {
                        child.resetPlayback(newStartTimestampMs = 0.0)
                        child.evaluateAtVBlank(0.0)
                    }
                    cursorMs = childEndMs
                }
            }
        }
    }

    companion object {
        private fun computeTotalDuration(
            mode: TimelinePlaybackMode,
            children: List<AnimationClip>
        ): Float {
            if (children.isEmpty()) return 0.0001f
            return when (mode) {
                TimelinePlaybackMode.SEQUENTIAL -> children.sumOf { it.durationSeconds.toDouble() }.toFloat().coerceAtLeast(0.0001f)
                TimelinePlaybackMode.PARALLEL -> (children.maxOfOrNull { it.durationSeconds } ?: 0.0001f).coerceAtLeast(0.0001f)
            }
        }
    }
}
