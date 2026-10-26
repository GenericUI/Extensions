package com.nexustools.genericui.ext.overlay

/**
 * Functional factory interface that creates an [OverlayElement] for a given [OverlayContext].
 */
fun interface OverlayElementFactory {
    fun create(context: OverlayContext): OverlayElement
}
