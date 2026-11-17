package com.nexustools.genericui.ext.box2d

/**
 * Normal and tangent contact impulses reported to [ContactListener2D.postSolve] (`b2ContactImpulse`).
 */
data class ContactImpulse2D(
    val normalImpulses: FloatArray = FloatArray(2),
    val tangentImpulses: FloatArray = FloatArray(2),
    var count: Int = 0
)
