package com.nexustools.genericui.ext.overlay.dsl

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.OverlayActions
import com.nexustools.genericui.ext.overlay.OverlayHostBox
import com.nexustools.genericui.ext.overlay.OverlayState

/**
 * Top-level `kotlinx.html`-style entry point that creates and configures a pure,
 * background-free [OverlayHostBox] without any default preset overlay or scrim.
 */
fun buildOverlayHostBox(
    content: AbstractComponent? = null,
    initialOverlay: AbstractOverlay? = null,
    state: OverlayState = OverlayState(),
    actions: OverlayActions = OverlayActions(),
    themeColorHex: String? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker OverlayHostBox.() -> Unit)? = null
): OverlayHostBox {
    val box = OverlayHostBox(content = content, initialOverlay = initialOverlay)
    box.state = state
    box.actions = actions
    if (themeColorHex != null) {
        box.themeColorHex = themeColorHex
    }
    if (!classes.isNullOrBlank()) {
        box.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        box.id = id
    }
    if (block != null) {
        box.block()
    }
    return box
}

/**
 * In-place [OverlayHostBox] builder inside any [AbstractContainer].
 */
fun AbstractContainer.overlayHostBox(
    content: AbstractComponent? = null,
    initialOverlay: AbstractOverlay? = null,
    state: OverlayState = OverlayState(),
    actions: OverlayActions = OverlayActions(),
    themeColorHex: String? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker OverlayHostBox.() -> Unit)? = null
): OverlayHostBox {
    val box = buildOverlayHostBox(
        content = content,
        initialOverlay = initialOverlay,
        state = state,
        actions = actions,
        themeColorHex = themeColorHex,
        id = id,
        classes = classes,
        block = block
    )
    addChild(box)
    return box
}
