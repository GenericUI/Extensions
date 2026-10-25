package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Native escape-hatch [Container] that can be placed anywhere inside an [AlienContainer] or [AlienFragment]
 * hierarchy to mix genuine OS-native `:common` components into an Alien interface.
 *
 * - On **System targets**, [UnalienContainer] unwraps any ancestor [AlienFactory] via
 *   [AlienFactory.unwrapHostFactory] so both [resolveActiveFactory] (for itself) and [resolveChildFactory]
 *   (for all of its children) return the host OS [NativeFactory].
 * - Because [UnalienContainer] creates a genuine platform `ContainerNativeImpl` instead of an `AlienImpl`,
 *   its parent [com.nexustools.genericui.ext.alien.impl.ContainerAlienImpl] automatically wraps it in an
 *   [com.nexustools.genericui.ext.alien.impl.AlienUnderlayExtensionSlotNode], attaches it to the host
 *   container **below** the `Canvas2D`, cuts a transparent hole in the `Canvas2D` (or draws higher-Z
 *   Alien overlays on top), and routes mouse/pointer passthrough events to the native controls inside it.
 * - On the **Web target**, behaves identically to a normal [Container].
 */
open class UnalienContainer(
    classPrefix: String = "container"
) : Container(classPrefix) {

    init {
        htmlTag = "genericui:unalien-container"
    }

    override fun resolveActiveFactory(): NativeFactory? {
        val raw = super.resolveActiveFactory() ?: return null
        if (AlienExtension.platformBridge.isWebPassThrough) return raw
        return AlienFactory.unwrapHostFactory(raw) ?: raw
    }

    override fun resolveChildFactory(): NativeFactory? = resolveActiveFactory()

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> {
        val hostFactory = if (AlienExtension.platformBridge.isWebPassThrough) {
            factory
        } else {
            AlienFactory.unwrapHostFactory(factory) ?: factory
        }
        return hostFactory.createContainer(componentClassPrefix, htmlTag)
    }
}
