package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.SelectOption
import com.nexustools.genericui.component.form.Switch
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.AlignSelf

internal class BooleanSchemaFieldController(
    private val booleanSpec: BooleanFieldSpec,
    initialLabel: String,
    compactInline: Boolean
) : SchemaBaseFieldController(booleanSpec, initialLabel, compactInline) {

    private var currentVal: Boolean = booleanSpec.defaultValue
    override var savedValue: Any? = booleanSpec.defaultValue

    private val switchWidget: Switch?
    private val checkWidget: CheckBox?
    private val selectWidget: Select?
    private val control: AbstractComponent

    init {
        when (booleanSpec.widgetHint) {
            SchemaWidgetHint.CHECKBOX -> {
                checkWidget = CheckBox(label = "", checked = currentVal).apply {
                    onCheckedChanged = { checked ->
                        if (!suppressUiSync && currentVal != checked) {
                            currentVal = checked
                            notifyValueChanged(checked)
                        }
                    }
                }
                switchWidget = null
                selectWidget = null
                control = checkWidget
            }
            SchemaWidgetHint.SELECT_ZERO_ONE -> {
                selectWidget = Select(
                    options = listOf(
                        SelectOption("0", booleanSpec.falseLabel),
                        SelectOption("1", booleanSpec.trueLabel)
                    ),
                    selectedValue = if (currentVal) "1" else "0"
                ).apply {
                    onSelectionChanged = { sel ->
                        if (!suppressUiSync) {
                            val next = SchemaValueCodec.coerceBoolean(sel, false)
                            if (currentVal != next) {
                                currentVal = next
                                notifyValueChanged(next)
                            }
                        }
                    }
                }
                switchWidget = null
                checkWidget = null
                control = selectWidget
            }
            else -> {
                switchWidget = Switch(label = "", checked = currentVal).apply {
                    onCheckedChanged = { checked ->
                        if (!suppressUiSync && currentVal != checked) {
                            currentVal = checked
                            notifyValueChanged(checked)
                        }
                    }
                }
                checkWidget = null
                selectWidget = null
                control = switchWidget
            }
        }
        if (!compactInline && (switchWidget != null || checkWidget != null)) {
            control.style.setAlignSelf(AlignSelf.START)
        }
        mountStandardBody(control, compactInline, control)
    }

    override var value: Any?
        get() = currentVal
        set(newVal) {
            val coerced = SchemaValueCodec.coerceBoolean(newVal, booleanSpec.defaultValue)
            val changed = currentVal != coerced
            currentVal = coerced
            suppressUiSync = true
            try {
                switchWidget?.isChecked = coerced
                checkWidget?.isChecked = coerced
                selectWidget?.selectedValue = if (coerced) "1" else "0"
            } finally {
                suppressUiSync = false
            }
            if (changed) notifyValueChanged(coerced)
        }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        control.isEnabled = enabled
    }
}
