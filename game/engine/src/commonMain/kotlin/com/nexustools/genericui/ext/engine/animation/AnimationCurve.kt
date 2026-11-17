package com.nexustools.genericui.ext.engine.animation

/**
 * Maps normalized raw timeline progress (`0.0f..1.0f`) to a shaped output value.
 */
fun interface AnimationCurve {
    fun sample(rawProgress: Float): Float
}
