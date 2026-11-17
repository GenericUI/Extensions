package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.engine.scene.EntityBehavior

/**
 * Drives frame-by-frame sprite-sheet animations on a sibling [Sprite2DModule] synced to VBlank.
 */
class SpriteSheetAnimationModule(
    var frames: List<SpriteSheetFrame> = emptyList(),
    var loop: Boolean = true,
    var isPlaying: Boolean = true
) : EntityBehavior() {

    var currentFrameIndex: Int = 0
        private set

    private var elapsedInFrame: Float = 0f

    fun setGrid(
        columns: Int,
        rows: Int,
        frameWidth: Float,
        frameHeight: Float,
        frameDurationSeconds: Float = 0.1f
    ) {
        val list = mutableListOf<SpriteSheetFrame>()
        for (r in 0 until rows.coerceAtLeast(1)) {
            for (c in 0 until columns.coerceAtLeast(1)) {
                list.add(
                    SpriteSheetFrame(
                        x = c * frameWidth,
                        y = r * frameHeight,
                        width = frameWidth,
                        height = frameHeight,
                        durationSeconds = frameDurationSeconds
                    )
                )
            }
        }
        frames = list
        currentFrameIndex = 0
        elapsedInFrame = 0f
        applyCurrentFrame()
    }

    override fun onRenderVBlank(frameNumber: Long, deltaSeconds: Float, timestampMs: Double) {
        if (!isPlaying || frames.isEmpty()) return
        elapsedInFrame += deltaSeconds.coerceAtLeast(0f)
        while (elapsedInFrame >= frames[currentFrameIndex].durationSeconds.coerceAtLeast(0.001f)) {
            elapsedInFrame -= frames[currentFrameIndex].durationSeconds.coerceAtLeast(0.001f)
            if (currentFrameIndex + 1 < frames.size) {
                currentFrameIndex++
            } else if (loop) {
                currentFrameIndex = 0
            } else {
                isPlaying = false
                break
            }
        }
        applyCurrentFrame()
    }

    fun applyCurrentFrame() {
        val frame = frames.getOrNull(currentFrameIndex) ?: return
        entity?.getModule<Sprite2DModule>()?.setSourceRect(frame.x, frame.y, frame.width, frame.height)
    }
}
