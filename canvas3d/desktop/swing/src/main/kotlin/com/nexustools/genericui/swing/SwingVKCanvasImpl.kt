package com.nexustools.genericui.swing

import com.nexustools.genericui.component.media.VKContext
import com.nexustools.genericui.nativeimpl.media.VKCanvasNativeImpl
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Java Swing Vulkan (`LWJGL` `VK10` runtime-detected via [SwingVKContextImpl]) implementation of [VKCanvasNativeImpl].
 */
class SwingVKCanvasImpl : SwingAbstractCanvasImpl<VKContext>(JPanel()), VKCanvasNativeImpl<JComponent> {
    private val vkContext = SwingVKContextImpl(800, 600)
    private var createdListener: ((VKContext, Int, Int) -> Unit)? = null
    private var resizedListener: ((VKContext, Int, Int) -> Unit)? = null

    override fun getRenderingContext(): VKContext = vkContext

    override fun setOnSurfaceCreated(listener: ((VKContext, Int, Int) -> Unit)?) {
        createdListener = listener
        listener?.invoke(vkContext, getSurfaceWidth(), getSurfaceHeight())
    }

    override fun setOnSurfaceResized(listener: ((VKContext, Int, Int) -> Unit)?) {
        resizedListener = listener
    }
}
