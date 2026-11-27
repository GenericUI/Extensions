package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.dialog.Dialogs
import com.nexustools.genericui.component.form.ComboBox
import com.nexustools.genericui.component.form.InputGroup
import com.nexustools.genericui.component.form.PasswordField
import com.nexustools.genericui.component.form.SelectOption
import com.nexustools.genericui.component.form.TextArea
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.typography.ILabelTarget
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType

internal class StringSchemaFieldController(
    private val stringSpec: StringFieldSpec,
    initialLabel: String,
    compactInline: Boolean
) : SchemaBaseFieldController(stringSpec, initialLabel, compactInline) {

    private var currentVal: String = stringSpec.defaultValue
    override var savedValue: Any? = stringSpec.defaultValue

    private val textFieldWidget: TextField?
    private val comboBoxWidget: ComboBox?
    private var browseButton: Button? = null
    private val control: AbstractComponent

    init {
        val isPassword = stringSpec.secret || stringSpec.widgetHint == SchemaWidgetHint.SECRET
        val isMultiline = stringSpec.multiline || stringSpec.widgetHint == SchemaWidgetHint.MULTILINE
        val isPath = stringSpec.widgetHint == SchemaWidgetHint.FILE_PATH || stringSpec.widgetHint == SchemaWidgetHint.FOLDER_PATH
        val hasSuggestions = stringSpec.suggestions.isNotEmpty() || stringSpec.suggestionsProvider != null

        when {
            isPassword -> {
                val pw = PasswordField(
                    value = currentVal,
                    placeholder = stringSpec.placeholder ?: ""
                ).apply {
                    onValueChanged = { handleTextInput(it, this) }
                }
                textFieldWidget = pw
                comboBoxWidget = null
                control = pw
            }
            isMultiline -> {
                val area = TextArea(
                    value = currentVal,
                    placeholder = stringSpec.placeholder ?: "",
                    rows = stringSpec.rows
                ).apply {
                    onValueChanged = { handleTextInput(it, this) }
                }
                textFieldWidget = area
                comboBoxWidget = null
                control = area
            }
            hasSuggestions -> {
                val initialOpts = buildSuggestionOptions(SchemaEvalContext())
                val combo = ComboBox(
                    options = initialOpts,
                    selectedValue = currentVal,
                    allowCustomInput = true
                ).apply {
                    onSelectionChanged = { selected ->
                        if (!suppressUiSync) {
                            val clamped = enforceMaxLength(selected)
                            if (clamped != selected) {
                                suppressUiSync = true
                                try {
                                    this.selectedValue = clamped
                                } finally {
                                    suppressUiSync = false
                                }
                            }
                            if (currentVal != clamped) {
                                currentVal = clamped
                                notifyValueChanged(clamped)
                            }
                        }
                    }
                }
                textFieldWidget = null
                comboBoxWidget = combo
                control = combo
            }
            isPath -> {
                val tf = TextField(
                    value = currentVal,
                    placeholder = stringSpec.placeholder ?: ""
                ).apply {
                    onValueChanged = { handleTextInput(it, this) }
                }
                val btn = Button("Browse...", type = BootstrapType.SECONDARY, size = BootstrapSize.SM).apply {
                    onClick = {
                        if (stringSpec.widgetHint == SchemaWidgetHint.FOLDER_PATH) {
                            Dialogs.selectFolder(
                                initialDirectory = currentVal.ifBlank { null },
                                onSelected = { chosen ->
                                    value = chosen.pathOrUri
                                }
                            )
                        } else {
                            Dialogs.openFile(
                                initialDirectory = currentVal.ifBlank { null },
                                filters = stringSpec.fileFilters,
                                onSelected = { chosen ->
                                    value = chosen.pathOrUri
                                }
                            )
                        }
                    }
                }
                val group = InputGroup().apply {
                    addChild(tf)
                    addChild(btn)
                }
                textFieldWidget = tf
                comboBoxWidget = null
                browseButton = btn
                control = group
                registerInvalidTarget(tf)
            }
            else -> {
                val tf = TextField(
                    value = currentVal,
                    placeholder = stringSpec.placeholder ?: ""
                ).apply {
                    onValueChanged = { handleTextInput(it, this) }
                }
                textFieldWidget = tf
                comboBoxWidget = null
                control = tf
            }
        }
        val primaryTarget = (textFieldWidget ?: comboBoxWidget ?: control) as? ILabelTarget
        bindLabelTarget(primaryTarget)
        mountStandardBody(control, compactInline, textFieldWidget ?: comboBoxWidget ?: control)
    }

    private fun enforceMaxLength(input: String): String {
        val cap = stringSpec.maxLength
        return if (cap != null && cap >= 0 && input.length > cap) input.substring(0, cap) else input
    }

    private fun handleTextInput(raw: String, field: TextField) {
        if (suppressUiSync) return
        val clamped = enforceMaxLength(raw)
        if (clamped != raw) {
            suppressUiSync = true
            try {
                field.value = clamped
            } finally {
                suppressUiSync = false
            }
        }
        if (currentVal != clamped) {
            currentVal = clamped
            notifyValueChanged(clamped)
        }
    }

    private fun buildSuggestionOptions(context: SchemaEvalContext): List<SelectOption> {
        val dynamic = stringSpec.suggestionsProvider?.invoke(context) ?: stringSpec.suggestions
        val out = dynamic.map { SelectOption(it, it) }.toMutableList()
        if (currentVal.isNotEmpty() && out.none { it.value == currentVal }) {
            out.add(0, SelectOption(currentVal, currentVal))
        }
        return out
    }

    override var value: Any?
        get() = currentVal
        set(newVal) {
            val next = newVal?.toString() ?: ""
            val changed = currentVal != next
            currentVal = next
            suppressUiSync = true
            try {
                textFieldWidget?.value = next
                comboBoxWidget?.selectedValue = next
            } finally {
                suppressUiSync = false
            }
            if (changed) notifyValueChanged(next)
        }

    override fun refreshOptions(context: SchemaEvalContext) {
        val combo = comboBoxWidget ?: return
        suppressUiSync = true
        try {
            combo.setOptions(buildSuggestionOptions(context))
            combo.selectedValue = currentVal
        } finally {
            suppressUiSync = false
        }
    }

    override fun collectBuiltInValidationErrors(
        current: Any?,
        context: SchemaEvalContext,
        label: String,
        errorsOut: MutableList<String>
    ) {
        stringSpec.maxLength?.let { cap ->
            SchemaValidator.MaxLength(cap).validate(current, context, label)?.let { errorsOut.add(it) }
        }
    }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        textFieldWidget?.isEnabled = enabled
        comboBoxWidget?.isEnabled = enabled
        browseButton?.isEnabled = enabled
    }

    override fun applySpecificConstraint(target: SchemaConstraintTarget, constraintValue: Any?) {
        if (target == SchemaConstraintTarget.PLACEHOLDER) {
            textFieldWidget?.placeholder = constraintValue?.toString().orEmpty()
        } else if (target == SchemaConstraintTarget.OPTIONS && comboBoxWidget != null) {
            val list = (constraintValue as? Iterable<*>)?.mapNotNull { item ->
                when (item) {
                    is SchemaOption -> SelectOption(item.value, item.label, item.disabled)
                    else -> item?.toString()?.let { SelectOption(it, it) }
                }
            }.orEmpty()
            suppressUiSync = true
            try {
                comboBoxWidget.setOptions(list)
                comboBoxWidget.selectedValue = currentVal
            } finally {
                suppressUiSync = false
            }
        }
    }
}
