package com.nexustools.genericui.ext.overlay

/**
 * Concrete, background-free auto-fading layered overlay ([FadingOverlay]) for composing
 * arbitrary full-overlay and floating [OverlayElement] layers on top of a target component.
 */
open class Overlay(
    tagName: String = "genericui:overlay",
    classPrefix: String = "gui-overlay overlay-fading-root",
    autoHideSpec: AutoHideSpec = AutoHideSpec()
) : FadingOverlay(tagName, classPrefix, autoHideSpec)
