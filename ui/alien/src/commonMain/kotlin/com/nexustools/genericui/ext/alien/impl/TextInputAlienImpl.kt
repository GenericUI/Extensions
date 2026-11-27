package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienResolvedVisualStyle
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Self-rendered Bootstrap 5.3 `.form-control` text field / textarea (`TextInputAlienImpl`)
 * implementing [TextInputNativeImpl].
 */
open class TextInputAlienImpl(
    private var multiline: Boolean = false,
    componentClassPrefix: String = "form-control",
    tagName: String = if (multiline) "textarea" else "input",
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = componentClassPrefix,
    tagName = tagName,
    session = session
), TextInputNativeImpl<Any?> {

    private var textValue: String = ""
    private var placeholderValue: String = ""
    private var labelValue: String? = null
    private var readOnlyState: Boolean = false
    private var passwordModeState: Boolean = false
    private var onValueChangedListener: ((String) -> Unit)? = null
    private var onSubmitListener: ((String) -> Unit)? = null
    private var externalKeyDownListener: ((KeyEvent) -> Unit)? = null

    init {
        node.focusRingEnabled = true
        node.attributes["value"] = ""
        node.customMeasure = { availW, _, style ->
            val minW = 180
            val w = if (availW > 0) availW.coerceAtLeast(minW) else 220
            val h = if (multiline) {
                val rows = node.attributes["rows"]?.toIntOrNull()?.coerceAtLeast(1) ?: 4
                (style.fontSizePx * rows).toInt() + style.padding.top + style.padding.bottom
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
                style.copy(textColor = resolvePlaceholderColor(style))
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
            val lbl = labelValue?.trim()?.takeIf { it.isNotEmpty() }
            if (lbl != null && bounds.width >= 32) {
                val capFontPx = (style.fontSizePx * 0.78f).coerceIn(10f, 13f)
                ctx.save()
                ctx.setGlobalAlpha(style.opacity.coerceIn(0f, 1f))
                ctx.setFont(style.fontFamily, capFontPx, 500, false)
                val maxCapW = (bounds.width - 20f).coerceAtLeast(12f)
                val capW = ctx.measureTextWidth(lbl).coerceAtMost(maxCapW)
                val capX = bounds.x + 10f
                val bw = style.borderWidthPx.coerceAtLeast(1f)
                val maskBg = if (style.backgroundColor.a > 0) style.backgroundColor else RgbaColor(255, 255, 255, 255)
                ctx.setFillColor(maskBg)
                ctx.fillRect(capX - 3f, bounds.y - bw - 1f, capW + 6f, bw * 2f + 2f)
                val capColor = if (style.focusRingColor != null) style.borderColor else style.textColor.withAlpha(0.78f)
                ctx.setFillColor(capColor)
                ctx.fillText(lbl, capX, bounds.y + capFontPx * 0.35f)
                ctx.restore()
            }
        }
        node.onKeyDown = { keyEv ->
            externalKeyDownListener?.invoke(keyEv)
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

    protected open fun resolvePlaceholderColor(style: AlienResolvedVisualStyle): RgbaColor =
        style.textColor.withAlpha(0.55f)

    private fun updateValueInternal(next: String) {
        if (textValue != next) {
            textValue = next
            node.attributes["value"] = next
            onValueChangedListener?.invoke(textValue)
            node.invalidateBounds()
        }
    }

    override fun setValue(value: String) {
        if (textValue != value) {
            textValue = value
            node.attributes["value"] = value
            node.invalidateBounds()
        }
    }

    override fun getValue(): String = textValue

    override fun setPlaceholder(placeholder: String) {
        if (placeholderValue != placeholder) {
            placeholderValue = placeholder
            node.attributes["placeholder"] = placeholder
            node.invalidateBounds()
        }
    }

    override fun getPlaceholder(): String = placeholderValue

    override fun setLabel(label: String?) {
        val normalized = label?.takeIf { it.isNotBlank() }
        if (labelValue != normalized) {
            labelValue = normalized
            node.markLayoutDirty()
            node.invalidateBounds()
        }
    }

    override fun getLabel(): String? = labelValue

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
            node.tagName = if (multiline) "textarea" else "input"
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

    override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {
        externalKeyDownListener = listener
    }
}
