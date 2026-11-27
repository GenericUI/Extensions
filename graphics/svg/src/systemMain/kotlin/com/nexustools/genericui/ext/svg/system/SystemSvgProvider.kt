package com.nexustools.genericui.ext.svg.system

import com.nexustools.genericui.ext.svg.SvgExtension

/**
 * Installs the pure-Kotlin software-rasterized [SystemSvgNativeImpl] provider into [SvgExtension].
 */
object SystemSvgProvider {
    fun install() {
        SvgExtension.registerProvider { svg, factory ->
            SystemSvgNativeImpl(svg, factory)
        }
    }
}
