package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl

/**
 * Self-rendered virtualized list (`LazyListAlienImpl`) implementing [LazyListNativeImpl].
 */
class LazyListAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "list-group",
    tagName = "div",
    session = session
), LazyListNativeImpl<Any?> {

    private var orientationValue: Orientation = Orientation.VERTICAL
    private var totalItemCount: Int = 0
    private var itemFactoryFn: ((Int) -> NativeImpl<*>)? = null
    private val materializedWindow = linkedMapOf<Int, NativeImpl<*>>()

    override fun setOrientation(orientation: Orientation) {
        orientationValue = orientation
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>) {
        totalItemCount = count.coerceAtLeast(0)
        itemFactoryFn = itemFactory
        rebuildVisibleWindow(0)
    }

    override fun notifyItemRangeChanged(startIndex: Int, count: Int) {
        rebuildVisibleWindow(startIndex.coerceAtLeast(0))
    }

    override fun scrollToIndex(index: Int) {
        val target = index.coerceIn(0, (totalItemCount - 1).coerceAtLeast(0))
        node.scrollOffsetY = target * 36
        rebuildVisibleWindow(target)
    }

    private fun rebuildVisibleWindow(firstIndex: Int) {
        clearChildrenNative()
        materializedWindow.clear()
        val factory = itemFactoryFn ?: return
        val maxWindow = 25
        val endExclusive = (firstIndex + maxWindow).coerceAtMost(totalItemCount)
        for (i in firstIndex until endExclusive) {
            val childImpl = factory(i)
            materializedWindow[i] = childImpl
            addChildNative(childImpl, -1)
        }
        node.markLayoutDirty()
        node.invalidateBounds()
    }
}
