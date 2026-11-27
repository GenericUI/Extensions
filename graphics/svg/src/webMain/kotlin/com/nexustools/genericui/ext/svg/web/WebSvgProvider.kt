package com.nexustools.genericui.ext.svg.web

import com.nexustools.genericui.ext.svg.SvgExtension

/**
 * Installs the native DOM `<svg>` [WebSvgNativeImpl] provider into [SvgExtension].
 */
object WebSvgProvider {
    fun install() {
        SvgExtension.registerProvider { svg, _ ->
            WebSvgNativeImpl(svg)
        }
    }
}
