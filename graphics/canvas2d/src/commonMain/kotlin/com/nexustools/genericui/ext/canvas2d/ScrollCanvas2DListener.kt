package com.nexustools.genericui.ext.canvas2d

/**
 * Listener invoked whenever a [ScrollCanvas2D]'s simulated scroll offset or viewport/content
 * dimensions change, allowing the application to re-render the viewport-sized canvas for
 * the visible region `(scrollX, scrollY, viewportWidth, viewportHeight)`.
 */
fun interface ScrollCanvas2DListener {
    fun onScrollChanged(
        scrollX: Int,
        scrollY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        contentWidth: Int,
        contentHeight: Int
    )
}
