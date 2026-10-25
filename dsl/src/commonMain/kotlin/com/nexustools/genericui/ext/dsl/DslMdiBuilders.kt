package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.shell.MdiContainer
import com.nexustools.genericui.component.shell.MdiWindow
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.geometry.Rect

/**
 * In-place [MdiContainer] (`genericui:mdicontainer`) builder for Multiple Document Interface workspaces.
 */
inline fun AbstractContainer.mdiContainer(
    showWindowToolbar: Boolean = true,
    showTaskbar: Boolean = true,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker MdiContainer).() -> Unit = {}
): MdiContainer = mountDslComponent(
    this,
    MdiContainer(showWindowToolbar = showWindowToolbar, showTaskbar = showTaskbar),
    classes,
    id,
    block
)

/**
 * Creates and mounts a child [MdiWindow] (`genericui:mdiwindow`) inside this [MdiContainer].
 */
inline fun MdiContainer.mdiWindow(
    windowId: String,
    title: String = "",
    category: String = "",
    iconName: String? = null,
    x: Int = 32,
    y: Int = 32,
    width: Int = 420,
    height: Int = 300,
    closable: Boolean = true,
    minimizable: Boolean = true,
    maximizable: Boolean = true,
    resizable: Boolean = true,
    classes: String? = null,
    block: (@GenericUiDslMarker MdiWindow).() -> Unit = {}
): MdiWindow {
    val win = createWindow(
        windowId = windowId,
        title = title,
        category = category,
        bounds = Rect(x = x, y = y, width = width, height = height),
        icon = iconName
    )
    win.isClosable = closable
    win.isMinimizable = minimizable
    win.isMaximizable = maximizable
    win.isResizable = resizable
    if (!classes.isNullOrBlank()) {
        win.addClass(classes)
    }
    win.block()
    return win
}

/**
 * Creates and mounts a child [MdiWindow] (`genericui:mdiwindow`) hosting a [Fragment] inside this [MdiContainer].
 */
inline fun MdiContainer.mdiFragmentWindow(
    windowId: String,
    fragment: Fragment,
    title: String = windowId,
    category: String = "",
    iconName: String? = null,
    x: Int = 32,
    y: Int = 32,
    width: Int = 420,
    height: Int = 300,
    closable: Boolean = true,
    minimizable: Boolean = true,
    maximizable: Boolean = true,
    resizable: Boolean = true,
    classes: String? = null,
    block: (@GenericUiDslMarker MdiWindow).() -> Unit = {}
): MdiWindow {
    val win = createFragmentWindow(
        windowId = windowId,
        title = title,
        fragment = fragment,
        category = category,
        bounds = Rect(x = x, y = y, width = width, height = height),
        icon = iconName
    )
    win.isClosable = closable
    win.isMinimizable = minimizable
    win.isMaximizable = maximizable
    win.isResizable = resizable
    if (!classes.isNullOrBlank()) {
        win.addClass(classes)
    }
    win.block()
    return win
}
