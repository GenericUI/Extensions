package com.nexustools.genericui.ext.engine2d

/**
 * Single sprite-sheet region frame within a [SpriteSheetAnimationModule].
 */
data class SpriteSheetFrame(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val durationSeconds: Float = 0.1f
)
