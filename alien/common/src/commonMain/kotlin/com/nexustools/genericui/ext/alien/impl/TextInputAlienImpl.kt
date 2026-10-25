package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl

/**
 * Self-rendered Bootstrap 5.3 `.form-control` text field / textarea (`TextInputAlienImpl`)
 * implementing [TextInputNativeImpl].
 */
class TextInputAlienImpl(
    private var multiline: Boolean = false,
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "form-control",
    tagName = if (multiline) "textarea" else "input",
    session = session
), TextInputNativeImpl<Any?> {

    private var textValue: String = ""
    private var placeholderValue: String = ""
    private var readOnlyState: Boolean = false
    private var passwordModeState: Boolean = false
    private var onValueChangedListener: ((String) -> Unit)? = null
    private var onSubmitListener: ((String) -> Unit)? = null

    init {
        node.focusRingEnabled = true
        node.customMeasure = { availW, _, style ->
            val minW = 180
            val w = if (availW > 0) availW.coerceAtLeast(minW) else 220
            val h = if (multiline) {
                (style.fontSizePx * 4f).toInt() + style.padding.top + style.padding.bottom
            } else {
                style.fontSizePx.toInt() + style.padding.top + style.padding.bottom + 10
            }
            Size(w, h)
        }
        node.customPaint = { ctx, bounds, style ->
            val inner = Rect(
                x = bounds.x + style.padding.left,
                y = bounds.y + style.padding.top,
                width = (bounds.width - style.padding.left - style.padding.right).coerceAtLeast(0),
                height = (bounds.height - style.padding.top - style.padding.bottom).coerceAtLeast(0)
            )
            val isShowingPlaceholder = textValue.isEmpty() && placeholderValue.isNotEmpty()
            val displayStr = when {
                isShowingPlaceholder -> placeholderValue
                passwordModeState -> "*".repeat(textValue.length)
                else -> textValue
            }
            val textStyle = if (isShowingPlaceholder) {
                style.copy(textColor = style.textColor.withAlpha(0.55f))
            } else {
                style
            }
            AlienBootstrapPainter.paintText(
                ctx = ctx,
                text = displayStr,
                contentBounds = inner,
                style = textStyle,
                verticalCenter = !multiline,
                maxLines = if (multiline) null else 1
            )
        }
        node.onKeyDown = { keyEv ->
            if (!readOnlyState && node.enabled) {
                when (keyEv.key) {
                    "Enter" -> if (!multiline) {
                        onSubmitListener?.invoke(textValue)
                    } else {
                        updateValueInternal("$textValue\n")
                    }
                    "Backspace" -> if (textValue.isNotEmpty()) {
                        updateValueInternal(textValue.dropLast(1))
                    }
                    else -> if (keyEv.key.length == 1 && !keyEv.ctrlKey && !keyEv.metaKey) {
                        updateValueInternal(textValue + keyEv.key)
                    }
                }
            }
        }
    }

    private fun updateValueInternal(next: String) {
        if (textValue != next) {
            textValue = next
            onValueChangedListener?.invoke(textValue)
            node.invalidateBounds()
        }
    }

    override fun setValue(value: String) {
        if (textValue != value) {
            textValue = value
            node.invalidateBounds()
        }
    }

    override fun getValue(): String = textValue

    override fun setPlaceholder(placeholder: String) {
        if (placeholderValue != placeholder) {
            placeholderValue = placeholder
            node.invalidateBounds()
        }
    }

    override fun getPlaceholder(): String = placeholderValue

    override fun setReadOnly(readOnly: Boolean) {
        readOnlyState = readOnly
        node.invalidateBounds()
    }

    override fun setPasswordMode(password: Boolean) {
        passwordModeState = password
        node.invalidateBounds()
    }

    override fun setMultiline(multiline: Boolean) {
        if (this.multiline != multiline) {
            this.multiline = multiline
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun setOnValueChanged(listener: ((String) -> Unit)?) {
        onValueChangedListener = listener
    }

    override fun setOnSubmit(listener: ((String) -> Unit)?) {
        onSubmitListener = listener
    }
}
