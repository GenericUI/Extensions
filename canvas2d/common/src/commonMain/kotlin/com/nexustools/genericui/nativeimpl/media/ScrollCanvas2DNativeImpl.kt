package com.nexustools.genericui.nativeimpl.media

/**
 * Native implementation contract for [com.nexustools.genericui.ext.canvas2d.ScrollCanvas2D].
 *
 * - On **Web** (`:extensions:canvas2d:web`), implemented by `WebScrollCanvas2DImpl` which renders
 *   a `<genericui:scrollcanvas2d>` element with `position: relative` and `overflow: auto`, an inner
 *   `<div>` sized to `(contentWidth, contentHeight)`, and an HTML `<canvas>` overtop the `<div>`
 *   with `position: absolute; left: 0; top: 0; width: 0; height: 0;`.
 * - On **System targets** (`:desktop`, `:mobile`, `:swing`), `ScrollCanvas2D` composes the
 *   viewport-sized `Canvas2D` with right and bottom scrollbar components (`RangeSlider`).
 */
interface ScrollCanvas2DNativeImpl<out W> : Canvas2DNativeImpl<W> {
    val isWebScrollContainer: Boolean
    fun setScrollableContentSize(contentWidth: Int, contentHeight: Int)
    fun setViewportSize(viewportWidth: Int, viewportHeight: Int)
    fun setScrollPosition(scrollX: Int, scrollY: Int)
    fun getScrollX(): Int
    fun getScrollY(): Int
    fun setOnScrollListener(listener: ((scrollX: Int, scrollY: Int) -> Unit)?)
}
