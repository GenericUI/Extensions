package com.nexustools.genericui.ext.canvas2d.alien

import com.nexustools.genericui.component.media.Canvas2D
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.alien.AlienExtensionImplProvider
import com.nexustools.genericui.ext.alien.AlienRenderSession

/**
 * Registers [Canvas2DAlienImpl] with `:extensions:ui:alien` so [Canvas2D] components mounted
 * inside an `AlienContainer`, `AlienFragment`, or `AlienFactory` render directly into the
 * Alien scene graph without creating OS underlay holes.
 */
object Canvas2DAlienExtension {
    private var registered: Boolean = false

    private val provider = AlienExtensionImplProvider { component: AbstractComponent, session: AlienRenderSession?, _: NativeFactory ->
        when {
            component !is Canvas2D -> null
            session != null && component === session.canvas2D -> null
            component.getAttribute("alienCanvas2D") == "true" -> null
            else -> Canvas2DAlienImpl(
                canvasComponent = component,
                initialWidth = (component.widthPx ?: 800).coerceAtLeast(1),
                initialHeight = (component.heightPx ?: 600).coerceAtLeast(1),
                doubleBuffer = component.doubleBuffer,
                initialAntialias = component.antialias,
                session = session
            )
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
