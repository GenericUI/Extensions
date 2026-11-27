package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.form.ColorPicker
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.style.color.RgbaColor

internal class ColorSchemaFieldController(
    private val colorSpec: ColorFieldSpec,
    initialLabel: String,
    compactInline: Boolean
) : SchemaBaseFieldController(colorSpec, initialLabel, compactInline) {

    private var currentColor: RgbaColor = RgbaColor.fromHex(colorSpec.defaultValue)
    override var savedValue: Any? = exportValue(currentColor)

    private val colorPicker: ColorPicker
    private val hexField: TextField
    private val chipButtons = mutableListOf<Button>()

    init {
        colorPicker = ColorPicker(initialColor = currentColor).apply {
            onColorChanged = { chosen ->
                if (!suppressUiSync) {
                    applyColor(chosen, syncPicker = false, syncHex = true)
                }
            }
        }
        hexField = TextField(
            value = currentColor.toHex(includeAlpha = colorSpec.allowAlpha && currentColor.a < 255),
            placeholder = colorSpec.placeholder ?: "#RRGGBB"
        ).apply {
            onValueChanged = { text ->
                if (!suppressUiSync) {
                    val parsed = RgbaColor.fromCss(text.trim())
                    if (parsed != null) {
                        applyColor(parsed, syncPicker = true, syncHex = false)
                    }
                }
            }
        }
        val row = HStack(SpacingScale.S2).apply {
            alignItems = AlignItems.CENTER
            addChild(colorPicker)
            addChild(hexField, weight = 1f)
        }
        val body: AbstractComponent = if (colorSpec.quickChips.isNotEmpty()) {
            val col = VStack(SpacingScale.S1)
            col.addChild(row)
            val chipsRow = HStack(SpacingScale.S1).apply {
                flexWrap = FlexWrap.WRAP
            }
            for (hex in colorSpec.quickChips) {
                val btn = Button(hex, type = BootstrapType.SECONDARY, size = BootstrapSize.SM, outline = true).apply {
                    onClick = {
                        applyColor(RgbaColor.fromHex(hex), syncPicker = true, syncHex = true)
                    }
                }
                chipButtons.add(btn)
                chipsRow.addChild(btn)
            }
            col.addChild(chipsRow)
            col
        } else {
            row
        }
        bindLabelTarget(hexField)
        mountStandardBody(body, compactInline, hexField, colorPicker)
    }

    private fun normalizeColor(c: RgbaColor): RgbaColor =
        if (colorSpec.allowAlpha) c else c.copy(a = 255)

    private fun exportValue(c: RgbaColor): Any {
        val norm = normalizeColor(c)
        return if (colorSpec.returnRgbaColorValue) norm else norm.toHex(includeAlpha = colorSpec.allowAlpha && norm.a < 255)
    }

    private fun applyColor(raw: RgbaColor, syncPicker: Boolean, syncHex: Boolean) {
        val norm = normalizeColor(raw)
        val changed = currentColor != norm
        currentColor = norm
        suppressUiSync = true
        try {
            if (syncPicker) {
                colorPicker.selectedColor = norm
            }
            if (syncHex) {
                hexField.value = norm.toHex(includeAlpha = colorSpec.allowAlpha && norm.a < 255)
            }
        } finally {
            suppressUiSync = false
        }
        if (changed) notifyValueChanged(exportValue(norm))
    }

    override var value: Any?
        get() = exportValue(currentColor)
        set(newVal) {
            val parsed = when (newVal) {
                is RgbaColor -> newVal
                is String -> RgbaColor.fromCss(newVal.trim()) ?: RgbaColor.fromHex(colorSpec.defaultValue)
                null -> RgbaColor.fromHex(colorSpec.defaultValue)
                else -> RgbaColor.fromCss(newVal.toString()) ?: RgbaColor.fromHex(colorSpec.defaultValue)
            }
            applyColor(parsed, syncPicker = true, syncHex = true)
        }

    override val stringValue: String
        get() = currentColor.toHex(includeAlpha = colorSpec.allowAlpha && currentColor.a < 255)

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        colorPicker.isEnabled = enabled
        hexField.isEnabled = enabled
        for (btn in chipButtons) btn.isEnabled = enabled
    }
}
