package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl

/**
 * Self-rendered virtualized grid (`LazyGridAlienImpl`) implementing [LazyGridNativeImpl].
 */
class LazyGridAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "lazy-grid",
    tagName = "div",
    session = session
), LazyGridNativeImpl<Any?> {

    private var columnsCount: Int = 3
    private var totalItemCount: Int = 0
    private var itemFactoryFn: ((Int) -> NativeImpl<*>)? = null

    init {
        node.attributes["data-columns"] = columnsCount.toString()
    }

    override fun setColumns(columns: Int) {
        columnsCount = columns.coerceAtLeast(1)
        node.attributes["data-columns"] = columnsCount.toString()
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>) {
        totalItemCount = count.coerceAtLeast(0)
        itemFactoryFn = itemFactory
        rebuildVisibleWindow(0)
    }

    override fun scrollToIndex(index: Int) {
        val target = index.coerceIn(0, (totalItemCount - 1).coerceAtLeast(0))
        rebuildVisibleWindow(target)
    }

    private fun rebuildVisibleWindow(firstIndex: Int) {
        clearChildrenNative()
        val factory = itemFactoryFn ?: return
        val maxWindow = 30
        val endExclusive = (firstIndex + maxWindow).coerceAtMost(totalItemCount)
        for (i in firstIndex until endExclusive) {
            addChildNative(factory(i), -1)
        }
        node.markLayoutDirty()
        node.invalidateBounds()
    }
}
