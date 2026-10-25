package com.nexustools.genericui.nativeimpl.media

/**
 * Native implementation contract for [com.nexustools.genericui.ext.canvas3d.ScrollCanvas3D],
 * [com.nexustools.genericui.ext.canvas3d.ScrollGLCanvas], and
 * [com.nexustools.genericui.ext.canvas3d.ScrollVKCanvas].
 *
 * - On **Web** (`:extensions:canvas3d:web`), implemented by `WebScrollCanvas3DImpl` which renders
 *   a `<genericui:scrollcanvas3d>` element with `position: relative` and `overflow: auto`, an inner
 *   `<div>` sized to `(contentWidth, contentHeight)`, and an HTML `<canvas>` overtop the `<div>`
 *   with `position: absolute; left: 0; top: 0; width: 0; height: 0;`.
 * - On **System targets** (`:desktop`, `:mobile`, `:swing`), `ScrollCanvas3D` composes the
 *   viewport-sized 3D canvas (`GLCanvasExt` / `VKCanvasExt`) with right and bottom scrollbar components (`RangeSlider`).
 */
interface ScrollCanvas3DNativeImpl<out W, C> : AbstractCanvasNativeImpl<W, C> {
    val isWebScrollContainer: Boolean
    fun setScrollableContentSize(contentWidth: Int, contentHeight: Int)
    fun setViewportSize(viewportWidth: Int, viewportHeight: Int)
    fun setScrollPosition(scrollX: Int, scrollY: Int)
    fun getScrollX(): Int
    fun getScrollY(): Int
    fun setOnScrollListener(listener: ((scrollX: Int, scrollY: Int) -> Unit)?)
}
