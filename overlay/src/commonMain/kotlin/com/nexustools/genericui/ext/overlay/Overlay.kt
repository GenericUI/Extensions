package com.nexustools.genericui.ext.overlay

/**
 * Concrete, background-free layered [AbstractOverlay] for composing arbitrary
 * full-overlay and floating [OverlayElement] layers on top of a target component.
 */
open class Overlay(
    tagName: String = "genericui:overlay",
    classPrefix: String = "gui-overlay",
    autoHideSpec: AutoHideSpec = AutoHideSpec()
) : AbstractOverlay(tagName, classPrefix, autoHideSpec)
