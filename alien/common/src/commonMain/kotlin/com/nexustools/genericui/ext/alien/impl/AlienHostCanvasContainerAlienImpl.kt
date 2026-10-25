package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Root host container implementation for an [com.nexustools.genericui.ext.alien.AlienContainer] or
 * [com.nexustools.genericui.ext.alien.AlienFragment] on System targets (`:desktop`, `:mobile`, `:swing`).
 *
 * Manages the two-layer hardware stack inside the host OS container (`hostContainerImpl`):
 * 1. **Underlay Layer (Below `Canvas2D`)**: Any [AlienUnderlayExtensionSlotNode]s (`UnalienContainer`
 *    or extension components such as `GLCanvas`, `VKCanvas`, `VideoPlayer`, `WebView`) are attached
 *    to `hostContainerImpl` **before** (below) the `Canvas2D`.
 * 2. **Top `Canvas2D` Layer**: `session.canvas2D`'s native implementation is attached on top of the
 *    underlay layer, rendering all `*AlienImpl` widgets and cutting transparent holes over underlay
 *    slots with mouse/pointer passthrough.
 */
class AlienHostCanvasContainerAlienImpl(
    val alienRoot: AbstractContainer,
    val hostFactory: NativeFactory,
    val session: AlienRenderSession
) : ContainerAlienImpl(
    componentClassPrefix = alienRoot.componentClassPrefix,
    tagName = alienRoot.htmlTag,
    session = session,
    initialNode = session.rootNode
) {
    val hostContainerImpl: ContainerNativeImpl<*> =
        hostFactory.createContainer(alienRoot.componentClassPrefix, alienRoot.htmlTag)

    private var canvasNativeImpl: NativeImpl<*>? = null

    override val nativeWidget: Any?
        get() = hostContainerImpl.nativeWidget

    init {
        session.rootNode.ownerImpl = this
        session.rootNode.tagName = alienRoot.htmlTag
        alienRoot.computedStyle?.let { session.rootNode.computedStyle = it }
        hostContainerImpl.setAttribute("alienHostContainer", "true")

        // Attach the top-layer Canvas2D native widget (wired to session.canvas2D) to the host container
        val cImpl = runCatching {
            session.canvas2D.ensureNativeImpl()
        }.getOrNull()
        canvasNativeImpl = cImpl
        if (cImpl != null) {
            cImpl.setAttribute("alienCanvas2D", "true")
            hostContainerImpl.addChildNative(cImpl, -1)
        }
        session.requestLayoutAndRepaint()

        // Attach any existing or newly registered underlay slots BELOW the Canvas2D (index 0)
        for (slot in session.underlaySlots) {
            hostContainerImpl.addChildNative(slot.wrappedNativeImpl, 0)
        }
        session.onUnderlaySlotRegistered = { slot ->
            hostContainerImpl.addChildNative(slot.wrappedNativeImpl, 0)
        }
        session.onUnderlaySlotUnregistered = { slot ->
            hostContainerImpl.removeChildNative(slot.wrappedNativeImpl)
        }
    }

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        hostContainerImpl.setPreferredSize(size)
        if (size != null && size.width > 0 && size.height > 0) {
            session.resizeViewport(size.width, size.height)
        }
    }

    override fun setVisible(visible: Boolean) {
        super.setVisible(visible)
        hostContainerImpl.setVisible(visible)
    }

    override fun dispose() {
        session.onUnderlaySlotRegistered = null
        session.onUnderlaySlotUnregistered = null
        canvasNativeImpl?.dispose()
        hostContainerImpl.dispose()
        super.dispose()
    }
}
