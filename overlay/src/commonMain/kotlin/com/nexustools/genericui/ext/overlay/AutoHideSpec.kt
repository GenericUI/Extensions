package com.nexustools.genericui.ext.overlay

/**
 * Configuration parameters for [AutoHideController].
 */
data class AutoHideSpec(
    val delayMs: Int = 3500,
    val fadeInMs: Int = 200,
    val fadeOutMs: Int = 200,
    val hideImmediatelyOnPointerLeave: Boolean = false,
    val instantHideOnTap: Boolean = false,
    val hideCursorWhenOverlayHidden: Boolean = false
) {
    constructor(
        timeoutMs: Int,
        fadeDurationMs: Int = 200,
        hideImmediatelyOnPointerLeave: Boolean = false,
        instantHideOnTap: Boolean = false,
        hideCursorWhenHidden: Boolean = false
    ) : this(
        delayMs = timeoutMs,
        fadeInMs = fadeDurationMs,
        fadeOutMs = fadeDurationMs,
        hideImmediatelyOnPointerLeave = hideImmediatelyOnPointerLeave,
        instantHideOnTap = instantHideOnTap,
        hideCursorWhenOverlayHidden = hideCursorWhenHidden
    )

    val timeoutMs: Int
        get() = delayMs

    val fadeDurationMs: Int
        get() = fadeInMs

    val hideCursorWhenHidden: Boolean
        get() = hideCursorWhenOverlayHidden
}
