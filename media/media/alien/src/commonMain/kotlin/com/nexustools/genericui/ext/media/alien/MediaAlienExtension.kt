package com.nexustools.genericui.ext.media.alien

import com.nexustools.genericui.component.media.VideoPlayer
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ext.alien.AlienExtension
import com.nexustools.genericui.ext.alien.AlienExtensionImplProvider
import com.nexustools.genericui.ext.alien.AlienRenderSession

/**
 * Registers [VideoPlayerAlienImpl] with `:extensions:ui:alien` so [VideoPlayer] components
 * mounted inside an `AlienContainer`, `AlienFragment`, or `AlienFactory` render directly into
 * Alien's offscreen/composited frame pipeline without creating OS HWND underlay holes.
 */
object MediaAlienExtension {
    private var registered: Boolean = false

    private val provider = AlienExtensionImplProvider { component: AbstractComponent, session: AlienRenderSession?, _: NativeFactory ->
        when (component) {
            is VideoPlayer -> VideoPlayerAlienImpl(
                videoPlayerComponent = component,
                session = session
            )
            else -> null
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
