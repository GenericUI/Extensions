package com.nexustools.genericui.ext.alien

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl

/**
 * Self-rendering scrollable [AlienFragment] (`AlienScrollFragment`) that combines the
 * `:extensions:alien` hardware-accelerated `Canvas2D` viewport with horizontal/vertical
 * scroll state (`scrollX`, `scrollY`), virtual content sizing, and scroll event callbacks.
 *
 * On `:web`, acts as a zero-overhead pass-through to the native DOM scrollable fragment container.
 */
abstract class AlienScrollFragment : AlienFragment() {

    var scrollX: Int = 0
        private set

    var scrollY: Int = 0
        private set

    var contentWidth: Int = 0
        private set

    var contentHeight: Int = 0
        private set

    private var onScrollHandler: ((Int, Int) -> Unit)? = null

    init {
        htmlTag = "genericui:alien-scroll-fragment"
        addClass("overflow-auto")
        addClass("scroll-fragment")
        onWheel = { wheel ->
            scrollTo(scrollX + wheel.deltaX.toInt(), scrollY + wheel.deltaY.toInt())
        }
    }

    fun setContentSize(width: Int, height: Int) {
        contentWidth = width.coerceAtLeast(0)
        contentHeight = height.coerceAtLeast(0)
        setAttribute("data-content-width", contentWidth.toString())
        setAttribute("data-content-height", contentHeight.toString())
        repaintAlien()
    }

    fun scrollTo(x: Int, y: Int) {
        val maxScrollX = if (contentWidth > 0 && (widthPx ?: 0) > 0) {
            (contentWidth - (widthPx ?: 0)).coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }
        val maxScrollY = if (contentHeight > 0 && (heightPx ?: 0) > 0) {
            (contentHeight - (heightPx ?: 0)).coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }
        scrollX = x.coerceIn(0, maxScrollX)
        scrollY = y.coerceIn(0, maxScrollY)
        setAttribute("data-scroll-x", scrollX.toString())
        setAttribute("data-scroll-y", scrollY.toString())
        (nativeImpl as? ScrollViewNativeImpl<*>)?.scrollTo(scrollX, scrollY)
        renderSession?.let { session ->
            session.rootNode.scrollOffsetX = scrollX
            session.rootNode.scrollOffsetY = scrollY
            session.rootNode.markLayoutDirty()
            session.requestLayoutAndRepaint()
        }
        onScrollHandler?.invoke(scrollX, scrollY)
        repaintAlien()
    }

    fun scrollToBottom() {
        val targetY = (contentHeight - (heightPx ?: 600)).coerceAtLeast(0)
        scrollTo(scrollX, targetY)
    }

    fun onScroll(handler: (Int, Int) -> Unit) {
        onScrollHandler = handler
    }

    override fun createNativeImpl(factory: NativeFactory): ContainerNativeImpl<*> {
        if (AlienExtension.platformBridge.isWebPassThrough) {
            return (factory.createScrollView() as? ContainerNativeImpl<*>) ?: super.createNativeImpl(factory)
        }
        return super.createNativeImpl(factory).also {
            renderSession?.let { session ->
                session.rootNode.scrollOffsetX = scrollX
                session.rootNode.scrollOffsetY = scrollY
            }
        }
    }
}
