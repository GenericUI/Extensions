package com.nexustools.genericui.ext.overlay.dsl

import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayerConstraint
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.overlay.AbstractOverlay
import com.nexustools.genericui.ext.overlay.AutoHideSpec
import com.nexustools.genericui.ext.overlay.Overlay
import com.nexustools.genericui.ext.overlay.OverlayActions
import com.nexustools.genericui.ext.overlay.OverlayElement
import com.nexustools.genericui.ext.overlay.OverlayHost
import com.nexustools.genericui.ext.overlay.OverlayState

/**
 * Builds a standalone pure, background-free [Overlay] container.
 */
fun buildOverlay(
    tagName: String = "genericui:overlay",
    classPrefix: String = "gui-overlay",
    autoHideSpec: AutoHideSpec = AutoHideSpec(),
    state: OverlayState = OverlayState(),
    actions: OverlayActions = OverlayActions(),
    themeColorHex: String? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker Overlay.() -> Unit)? = null
): Overlay {
    val instance = Overlay(tagName = tagName, classPrefix = classPrefix, autoHideSpec = autoHideSpec)
    instance.updateState(state)
    instance.updateActions(actions)
    if (themeColorHex != null) {
        instance.themeColorHex = themeColorHex
    }
    if (!classes.isNullOrBlank()) {
        instance.addClass(classes)
    }
    if (!id.isNullOrBlank()) {
        instance.id = id
    }
    if (block != null) {
        instance.block()
    }
    return instance
}

/**
 * Mounts a pure, background-free [Overlay] inside any [AbstractContainer].
 */
fun AbstractContainer.overlay(
    tagName: String = "genericui:overlay",
    classPrefix: String = "gui-overlay",
    autoHideSpec: AutoHideSpec = AutoHideSpec(),
    state: OverlayState = OverlayState(),
    actions: OverlayActions = OverlayActions(),
    themeColorHex: String? = null,
    id: String? = null,
    classes: String? = null,
    block: (@GenericUiDslMarker Overlay.() -> Unit)? = null
): Overlay {
    val instance = buildOverlay(
        tagName = tagName,
        classPrefix = classPrefix,
        autoHideSpec = autoHideSpec,
        state = state,
        actions = actions,
        themeColorHex = themeColorHex,
        id = id,
        classes = classes,
        block = block
    )
    addChild(instance)
    return instance
}

/**
 * Creates, configures, and attaches a pure, background-free [Overlay] to this [OverlayHost].
 */
fun OverlayHost.attachPureOverlay(
    tagName: String = "genericui:overlay",
    classPrefix: String = "gui-overlay",
    autoHideSpec: AutoHideSpec = AutoHideSpec(),
    themeColorHex: String? = null,
    block: (@GenericUiDslMarker Overlay.() -> Unit)? = null
): Overlay {
    val instance = Overlay(tagName = tagName, classPrefix = classPrefix, autoHideSpec = autoHideSpec)
    if (themeColorHex != null) {
        instance.themeColorHex = themeColorHex
    }
    attachOverlay(instance)
    if (block != null) {
        instance.block()
    }
    return instance
}

/**
 * Adds an [OverlayElement] wrapping [component] with [constraint] into this [AbstractOverlay].
 */
fun AbstractOverlay.overlayElement(
    component: AbstractComponent,
    constraint: LayerConstraint = LayerConstraint.fill(),
    elementId: String = component.id ?: "overlay-element"
): OverlayElement {
    val element = OverlayElement(
        component = component,
        constraint = constraint,
        elementId = elementId
    )
    return addElement(element, constraint)
}

/**
 * Adds [component] as a full-overlay (`100% × 100%`) layer inside this [AbstractOverlay].
 */
fun AbstractOverlay.fillLayer(
    component: AbstractComponent,
    zIndex: Int = 0,
    passThroughPointerEvents: Boolean = false
): OverlayElement =
    addElement(
        component = component,
        constraint = LayerConstraint.fill(
            zIndex = zIndex,
            passThroughPointerEvents = passThroughPointerEvents
        )
    )

/**
 * Adds [component] as a positioned floating layer inside this [AbstractOverlay].
 */
fun AbstractOverlay.floatingLayer(
    component: AbstractComponent,
    alignment: LayerAlignment = LayerAlignment.TOP_START,
    offsetXPx: Int = 0,
    offsetYPx: Int = 0,
    leftPercent: Float? = null,
    topPercent: Float? = null,
    rightPx: Int? = null,
    bottomPx: Int? = null,
    zIndex: Int = 1,
    passThroughPointerEvents: Boolean = false
): OverlayElement =
    addElement(
        component = component,
        constraint = LayerConstraint.float(
            alignment = alignment,
            offsetXPx = offsetXPx,
            offsetYPx = offsetYPx,
            leftPercent = leftPercent,
            topPercent = topPercent,
            rightPx = rightPx,
            bottomPx = bottomPx,
            zIndex = zIndex,
            passThroughPointerEvents = passThroughPointerEvents
        )
    )
