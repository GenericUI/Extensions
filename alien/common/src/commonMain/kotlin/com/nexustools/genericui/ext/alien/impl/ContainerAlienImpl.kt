package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.ext.alien.AlienExtensionLayerMode
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.style.transition.ContainerAnimationFrameState

/**
 * Self-rendered container implementation (`ContainerAlienImpl`) implementing [ContainerNativeImpl].
 *
 * - When `addChildNative(childImpl, index)` receives an [AlienImpl], it attaches `childImpl.node`
 *   directly as a child [AlienWidgetNode].
 * - When `addChildNative(childImpl, index)` receives a non-Alien [NativeImpl] (such as an
 *   `UnalienContainer` or an extension component like `GLCanvas`, `VKCanvas`, `VideoPlayer`,
 *   `AudioAnalyzer`, or `WebView`), it automatically wraps `childImpl` in an
 *   [AlienUnderlayExtensionSlotNode], registers it in [AlienRenderSession.underlaySlots] so it is
 *   attached to the host container **below** the `Canvas2D`, and cuts a transparent hole in the `Canvas2D`!
 */
open class ContainerAlienImpl(
    componentClassPrefix: String = "container",
    tagName: String = "div",
    session: AlienRenderSession? = null,
    initialNode: AlienWidgetNode? = null
) : AlienImpl(
    componentClassPrefix = componentClassPrefix,
    tagName = tagName,
    session = session,
    initialNode = initialNode
), ContainerNativeImpl<Any?> {

    private val underlayWrappers = linkedMapOf<NativeImpl<*>, AlienUnderlayExtensionSlotNode>()

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        val alienChildNode = (childImpl.nativeWidget as? AlienWidgetNode)
            ?: (childImpl as? AlienImpl)?.node?.takeIf { childImpl !is AlienHostCanvasContainerAlienImpl }
        if (alienChildNode != null) {
            node.addChild(alienChildNode, index)
        } else {
            val slot = underlayWrappers.getOrPut(childImpl) {
                AlienUnderlayExtensionSlotNode(
                    wrappedNativeImpl = childImpl,
                    layerMode = AlienExtensionLayerMode.CUT_TRANSPARENT_HOLE,
                    session = node.session
                )
            }
            node.addChild(slot.node, index)
            node.session?.registerUnderlaySlot(slot)
        }
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        val alienChildNode = (childImpl.nativeWidget as? AlienWidgetNode)
            ?: (childImpl as? AlienImpl)?.node?.takeIf { childImpl !is AlienHostCanvasContainerAlienImpl }
        if (alienChildNode != null) {
            node.removeChild(alienChildNode)
        } else {
            val slot = underlayWrappers.remove(childImpl)
            if (slot != null) {
                node.session?.unregisterUnderlaySlot(slot)
                node.removeChild(slot.node)
            }
        }
    }

    override fun clearChildrenNative() {
        for (slot in underlayWrappers.values) {
            node.session?.unregisterUnderlaySlot(slot)
        }
        underlayWrappers.clear()
        node.clearChildren()
    }

    fun getUnderlaySlotFor(childImpl: NativeImpl<*>): AlienUnderlayExtensionSlotNode? =
        underlayWrappers[childImpl]

    override fun enableHardwareLayerCache(enabled: Boolean) {
        node.hardwareLayerCacheEnabled = enabled
    }

    override fun captureOffscreenBuffer(width: Int, height: Int): ImageBuffer? {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val buf = ImageBuffer(safeW, safeH)
        node.cachedOffscreenBuffer = buf
        return buf
    }

    override fun presentAnimatedBuffer(buffer: ImageBuffer, frameState: ContainerAnimationFrameState) {
        node.cachedOffscreenBuffer = buffer
        node.animatedFrameState = frameState
        node.invalidateBounds()
    }

    override fun releaseAnimatedBuffer() {
        node.animatedFrameState = null
        node.invalidateBounds()
    }
}
