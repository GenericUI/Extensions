package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * Self-rendered Bootstrap 5.3 `.modal` dialog window (`DialogWindowAlienImpl`)
 * implementing [DialogWindowNativeImpl].
 */
class DialogWindowAlienImpl(
    session: AlienRenderSession? = null
) : ContainerAlienImpl(
    componentClassPrefix = "modal-content",
    tagName = "div",
    session = session
), DialogWindowNativeImpl<Any?> {

    private var titleValue: String = ""
    private var presentationStyleValue: DialogPresentationStyle = DialogPresentationStyle.MODAL_CENTERED
    private var modalState: Boolean = true
    private var fullscreenState: Boolean = false
    private var onDialogClosedListener: (() -> Unit)? = null
    var isShowing: Boolean = false
        private set

    init {
        node.visible = false
        node.customPaint = { ctx, bounds, style ->
            if (titleValue.isNotEmpty()) {
                val titleRect = Rect(bounds.x + 16, bounds.y + 8, (bounds.width - 32).coerceAtLeast(0), 36)
                AlienBootstrapPainter.paintText(
                    ctx = ctx,
                    text = titleValue,
                    contentBounds = titleRect,
                    style = style.copy(fontSizePx = 20f, fontWeight = 500),
                    verticalCenter = true,
                    maxLines = 1
                )
            }
        }
    }

    override fun setTitle(title: String) {
        titleValue = title
        node.invalidateBounds()
    }

    override fun getTitle(): String = titleValue

    override fun setPresentationStyle(style: DialogPresentationStyle) {
        presentationStyleValue = style
        node.invalidateBounds()
    }

    override fun getPresentationStyle(): DialogPresentationStyle = presentationStyleValue

    override fun setModal(modal: Boolean) {
        modalState = modal
    }

    override fun setFullscreen(fullscreen: Boolean) {
        fullscreenState = fullscreen
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun showDialog(ownerWindow: WindowNativeImpl<*>?) {
        isShowing = true
        node.visible = true
        node.markLayoutDirty()
        node.invalidateBounds()
    }

    override fun closeDialog() {
        if (isShowing) {
            isShowing = false
            node.visible = false
            onDialogClosedListener?.invoke()
            node.invalidateBounds()
        }
    }

    override fun setOnDialogClosed(listener: (() -> Unit)?) {
        onDialogClosedListener = listener
    }
}
