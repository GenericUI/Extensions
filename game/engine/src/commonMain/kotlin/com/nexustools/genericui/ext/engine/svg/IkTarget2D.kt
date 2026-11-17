package com.nexustools.genericui.ext.engine.svg

/**
 * Post-FK 2-bone inverse kinematics constraint evaluated on [SkeletalAnimator2D] after pose tracks.
 */
data class IkTarget2D(
    val upperBoneName: String,
    val lowerBoneName: String,
    var targetX: Float,
    var targetY: Float,
    var bendDirection: Float = 1f,
    var weight: Float = 1f
)
