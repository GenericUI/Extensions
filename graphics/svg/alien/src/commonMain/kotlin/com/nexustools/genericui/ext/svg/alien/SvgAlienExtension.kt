package com.nexustools.genericui.ext.svg.alien

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.alien.AlienExtensionImplProvider
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.svg.Svg

/**
 * Registers [SvgAlienImpl] with `:extensions:ui:alien` so [Svg] components mounted inside an
 * `AlienContainer`, `AlienFragment`, or `AlienFactory` rasterize and paint directly onto the
 * Alien `Canvas2D` scene graph without creating OS underlay holes.
 */
object SvgAlienExtension {
    private var registered: Boolean = false

    private val provider = AlienExtensionImplProvider { component: AbstractComponent, session: AlienRenderSession?, _: NativeFactory ->
        if (component is Svg) {
            SvgAlienImpl(
                svgComponent = component,
                session = session
            )
        } else {
            null
        }
    }

    val isRegistered: Boolean
        get() = registered

    fun register() {
        if (registered) return
        AlienExtension.registerAlienExtensionImpl(provider)
        registered = true
    }

    fun unregister() {
        if (!registered) return
        AlienExtension.unregisterAlienExtensionImpl(provider)
        registered = false
    }
}
