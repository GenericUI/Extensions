package com.nexustools.genericui.ext.engine.animation

/**
 * Defines how an animation behaves once its elapsed time reaches or exceeds its duration.
 */
enum class AnimationWrapMode {
    /** Plays once from 0.0 to 1.0 and stops upon completion. */
    ONCE,
    /** Repeats seamlessly from 0.0 to 1.0 indefinitely. */
    LOOP,
    /** Alternates forward (0.0 -> 1.0) and backward (1.0 -> 0.0) indefinitely. */
    PING_PONG,
    /** Plays from 0.0 to 1.0 and holds at 1.0 without removing the clip until stopped. */
    CLAMP_FOREVER
}
