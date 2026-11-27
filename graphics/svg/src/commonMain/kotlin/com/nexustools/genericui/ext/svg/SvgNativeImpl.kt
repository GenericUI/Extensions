package com.nexustools.genericui.ext.svg

import com.nexustools.genericui.core.NativeImpl

/**
 * Native implementation contract for [Svg] across system, web, and alien backends.
 */
interface SvgNativeImpl<out W> : NativeImpl<W> {
    fun syncSvgScene(svg: Svg)
    fun invalidateSvgScene()
    fun hitTestViewport(viewportX: Float, viewportY: Float): SvgElement? = null
}
