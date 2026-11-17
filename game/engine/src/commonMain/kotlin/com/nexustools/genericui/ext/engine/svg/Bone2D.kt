package com.nexustools.genericui.ext.engine.svg

import com.nexustools.genericui.ext.engine.math.AffineMatrix2D
import kotlin.math.cos
import kotlin.math.sin

/**
 * Single 2D skeletal bone with local placement relative to [parentName] (along the parent's local
 * frame) and world-space coordinates computed by [Skeleton2D.updateWorldTransforms].
 */
class Bone2D(
    val name: String,
    val lengthPx: Float,
    var localX: Float = 0f,
    var localY: Float = 0f,
    var localAngleRad: Float = 0f,
    val parentName: String? = null
) {
    var worldX: Float = 0f
        internal set

    var worldY: Float = 0f
        internal set

    var worldAngleRad: Float = 0f
        internal set

    var worldMatrix: AffineMatrix2D = AffineMatrix2D.IDENTITY
        internal set

    val tipX: Float
        get() = worldX + cos(worldAngleRad) * lengthPx

    val tipY: Float
        get() = worldY + sin(worldAngleRad) * lengthPx
}
