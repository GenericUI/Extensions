package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.lazy.LazyGrid
import com.nexustools.genericui.component.lazy.LazyList
import com.nexustools.genericui.component.lazy.TreeView
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.ui.Orientation

/**
 * In-place virtualized [LazyList] builder.
 */
inline fun AbstractContainer.lazyList(
    orientation: Orientation = Orientation.VERTICAL,
    estimatedItemExtentPx: Float = 56f,
    overscanItems: Int = 3,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker LazyList).() -> Unit = {}
): LazyList = mountDslComponent(
    this,
    LazyList(
        orientation = orientation,
        estimatedItemExtentPx = estimatedItemExtentPx,
        overscanItems = overscanItems
    ),
    classes,
    id,
    block
)

/**
 * In-place virtualized [LazyGrid] builder.
 */
inline fun AbstractContainer.lazyGrid(
    columns: Int = 3,
    estimatedRowHeightPx: Float = 180f,
    overscanRows: Int = 2,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker LazyGrid).() -> Unit = {}
): LazyGrid = mountDslComponent(
    this,
    LazyGrid(
        columns = columns,
        estimatedRowHeightPx = estimatedRowHeightPx,
        overscanRows = overscanRows
    ),
    classes,
    id,
    block
)

/**
 * In-place virtualized [TreeView] builder.
 */
inline fun <T> AbstractContainer.treeView(
    estimatedRowHeightPx: Float = 32f,
    overscanRows: Int = 4,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker TreeView<T>).() -> Unit = {}
): TreeView<T> = mountDslComponent(
    this,
    TreeView<T>(
        estimatedRowHeightPx = estimatedRowHeightPx,
        overscanRows = overscanRows
    ),
    classes,
    id,
    block
)
