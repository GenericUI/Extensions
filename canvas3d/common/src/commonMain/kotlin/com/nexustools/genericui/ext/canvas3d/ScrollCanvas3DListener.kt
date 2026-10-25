package com.nexustools.genericui.ext.canvas3d

/**
 * Listener invoked whenever a [ScrollCanvas3D], [ScrollGLCanvas], or [ScrollVKCanvas]'s
 * simulated scroll offset or viewport/content dimensions change, allowing the application
 * to re-render the viewport-sized 3D canvas for the visible region
 * `(scrollX, scrollY, viewportWidth, viewportHeight)`.
 */
fun interface ScrollCanvas3DListener {
    fun onScrollChanged(
        scrollX: Int,
        scrollY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        contentWidth: Int,
        contentHeight: Int
    )
}
