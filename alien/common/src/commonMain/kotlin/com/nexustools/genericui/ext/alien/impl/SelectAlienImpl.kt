package com.nexustools.genericui.ext.alien.impl

import com.nexustools.genericui.ext.alien.AlienBootstrapPainter
import com.nexustools.genericui.ext.alien.AlienRenderSession
import com.nexustools.genericui.ext.alien.AlienWidgetNode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl

/**
 * Self-rendered Bootstrap 5.3 `.form-select` control (`SelectAlienImpl`) implementing [SelectNativeImpl].
 */
class SelectAlienImpl(
    session: AlienRenderSession? = null
) : AlienImpl(
    componentClassPrefix = "form-select",
    tagName = "select",
    session = session
), SelectNativeImpl<Any?> {

    private var optionsList: List<Pair<String, String>> = emptyList()
    private var selectedVal: String? = null
    private var onSelectionChangedListener: ((String) -> Unit)? = null

    init {
        node.focusRingEnabled = true
        node.customMeasure = { availW, _, style ->
            val w = if (availW > 0) availW.coerceAtLeast(140) else 180
            val h = style.fontSizePx.toInt() + style.padding.top + style.padding.bottom + 10
            Size(w, h)
        }
        node.customPaint = { ctx, bounds, style ->
            val currentLabel = optionsList.firstOrNull { it.first == selectedVal }?.second
                ?: optionsList.firstOrNull()?.second
                ?: ""
            val inner = Rect(
                x = bounds.x + style.padding.left,
                y = bounds.y + style.padding.top,
                width = (bounds.width - style.padding.left - style.padding.right - 18).coerceAtLeast(0),
                height = (bounds.height - style.padding.top - style.padding.bottom).coerceAtLeast(0)
            )
            AlienBootstrapPainter.paintText(
                ctx = ctx,
                text = currentLabel,
                contentBounds = inner,
                style = style,
                verticalCenter = true,
                maxLines = 1
            )
        }
        node.onClick = {
            if (optionsList.isNotEmpty() && node.enabled) {
                val curIdx = optionsList.indexOfFirst { it.first == selectedVal }
                val nextIdx = (curIdx + 1) % optionsList.size
                val nextVal = optionsList[nextIdx].first
                selectedVal = nextVal
                onSelectionChangedListener?.invoke(nextVal)
                node.invalidateBounds()
            }
        }
    }

    override fun setOptions(options: List<Pair<String, String>>) {
        optionsList = options.toList()
        if (selectedVal == null && optionsList.isNotEmpty()) {
            selectedVal = optionsList.first().first
        }
        node.invalidateBounds()
    }

    override fun setSelectedValue(value: String?) {
        if (selectedVal != value) {
            selectedVal = value
            node.invalidateBounds()
        }
    }

    override fun getSelectedValue(): String? = selectedVal

    override fun setOnSelectionChanged(listener: ((String) -> Unit)?) {
        onSelectionChangedListener = listener
    }
}
