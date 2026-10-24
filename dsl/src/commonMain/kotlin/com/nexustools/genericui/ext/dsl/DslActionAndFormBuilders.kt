package com.nexustools.genericui.ext.dsl

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.ButtonGroup
import com.nexustools.genericui.component.action.DropdownButton
import com.nexustools.genericui.component.action.IconButton
import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.CompoundUnitInput
import com.nexustools.genericui.component.form.FormGroup
import com.nexustools.genericui.component.form.InputGroup
import com.nexustools.genericui.component.form.NumberField
import com.nexustools.genericui.component.form.PasswordField
import com.nexustools.genericui.component.form.RadioButton
import com.nexustools.genericui.component.form.RadioGroup
import com.nexustools.genericui.component.form.RangeSlider
import com.nexustools.genericui.component.form.SearchField
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.SelectOption
import com.nexustools.genericui.component.form.Switch
import com.nexustools.genericui.component.form.TextArea
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.ui.Orientation

/**
 * In-place `<button>` [Button] builder matching `kotlinx.html`'s `button`.
 */
inline fun AbstractContainer.button(
    text: String = "",
    type: BootstrapType = BootstrapType.PRIMARY,
    size: BootstrapSize = BootstrapSize.MD,
    outline: Boolean = false,
    classes: String? = null,
    id: String? = null,
    noinline onClick: (() -> Unit)? = null,
    block: (@GenericUiDslMarker Button).() -> Unit = {}
): Button = mountDslComponent(
    this,
    Button(text = text, type = type, size = size, outline = outline),
    classes,
    id
) {
    if (onClick != null) {
        this.onClick = onClick
    }
    block()
}

/**
 * In-place [IconButton] builder.
 */
inline fun AbstractContainer.iconButton(
    icon: String,
    tooltipText: String? = null,
    type: BootstrapType = BootstrapType.SECONDARY,
    size: BootstrapSize = BootstrapSize.SM,
    classes: String? = null,
    id: String? = null,
    noinline onClick: (() -> Unit)? = null,
    block: (@GenericUiDslMarker IconButton).() -> Unit = {}
): IconButton = mountDslComponent(
    this,
    IconButton(icon = icon, tooltipText = tooltipText, type = type, size = size),
    classes,
    id
) {
    if (onClick != null) {
        this.onClick = onClick
    }
    block()
}

/**
 * In-place [ButtonGroup] builder.
 */
inline fun AbstractContainer.buttonGroup(
    orientation: Orientation = Orientation.HORIZONTAL,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker ButtonGroup).() -> Unit = {}
): ButtonGroup = mountDslComponent(this, ButtonGroup(orientation = orientation), classes, id, block)

/**
 * In-place [DropdownButton] builder.
 */
inline fun AbstractContainer.dropdownButton(
    label: String = "",
    type: BootstrapType = BootstrapType.PRIMARY,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker DropdownButton).() -> Unit = {}
): DropdownButton = mountDslComponent(this, DropdownButton(label = label, type = type), classes, id, block)

/**
 * In-place `<input type="text">` [TextField] builder.
 */
inline fun AbstractContainer.textInput(
    value: String = "",
    placeholder: String = "",
    classes: String? = null,
    id: String? = null,
    noinline onValueChanged: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker TextField).() -> Unit = {}
): TextField = mountDslComponent(
    this,
    TextField(value = value, placeholder = placeholder),
    classes,
    id
) {
    if (onValueChanged != null) {
        this.onValueChanged = onValueChanged
    }
    block()
}

/**
 * Alias for [textInput] (`textField`).
 */
inline fun AbstractContainer.textField(
    value: String = "",
    placeholder: String = "",
    classes: String? = null,
    id: String? = null,
    noinline onValueChanged: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker TextField).() -> Unit = {}
): TextField = textInput(
    value = value,
    placeholder = placeholder,
    classes = classes,
    id = id,
    onValueChanged = onValueChanged,
    block = block
)

/**
 * Alias for [textInput] (`input` matching `kotlinx.html`'s `input`).
 */
inline fun AbstractContainer.input(
    value: String = "",
    placeholder: String = "",
    classes: String? = null,
    id: String? = null,
    noinline onValueChanged: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker TextField).() -> Unit = {}
): TextField = textInput(
    value = value,
    placeholder = placeholder,
    classes = classes,
    id = id,
    onValueChanged = onValueChanged,
    block = block
)

/**
 * In-place `<input type="password">` [PasswordField] builder.
 */
inline fun AbstractContainer.passwordInput(
    value: String = "",
    placeholder: String = "Password",
    classes: String? = null,
    id: String? = null,
    noinline onValueChanged: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker PasswordField).() -> Unit = {}
): PasswordField = mountDslComponent(
    this,
    PasswordField(value = value, placeholder = placeholder),
    classes,
    id
) {
    if (onValueChanged != null) {
        this.onValueChanged = onValueChanged
    }
    block()
}

/**
 * Alias for [passwordInput].
 */
inline fun AbstractContainer.passwordField(
    value: String = "",
    placeholder: String = "Password",
    classes: String? = null,
    id: String? = null,
    noinline onValueChanged: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker PasswordField).() -> Unit = {}
): PasswordField = passwordInput(
    value = value,
    placeholder = placeholder,
    classes = classes,
    id = id,
    onValueChanged = onValueChanged,
    block = block
)

/**
 * In-place `<input type="number">` [NumberField] builder.
 */
inline fun AbstractContainer.numberInput(
    numericValue: Double = 0.0,
    minValue: Double = -Double.MAX_VALUE,
    maxValue: Double = Double.MAX_VALUE,
    step: Double = 1.0,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker NumberField).() -> Unit = {}
): NumberField = mountDslComponent(
    this,
    NumberField(numericValue = numericValue, minValue = minValue, maxValue = maxValue, step = step),
    classes,
    id,
    block
)

/**
 * Alias for [numberInput].
 */
inline fun AbstractContainer.numberField(
    numericValue: Double = 0.0,
    minValue: Double = -Double.MAX_VALUE,
    maxValue: Double = Double.MAX_VALUE,
    step: Double = 1.0,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker NumberField).() -> Unit = {}
): NumberField = numberInput(
    numericValue = numericValue,
    minValue = minValue,
    maxValue = maxValue,
    step = step,
    classes = classes,
    id = id,
    block = block
)

/**
 * In-place `<textarea>` [TextArea] builder matching `kotlinx.html`'s `textArea`.
 */
inline fun AbstractContainer.textArea(
    value: String = "",
    placeholder: String = "",
    rows: Int = 4,
    classes: String? = null,
    id: String? = null,
    noinline onValueChanged: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker TextArea).() -> Unit = {}
): TextArea = mountDslComponent(
    this,
    TextArea(value = value, placeholder = placeholder, rows = rows),
    classes,
    id
) {
    if (onValueChanged != null) {
        this.onValueChanged = onValueChanged
    }
    block()
}

/**
 * In-place `<input type="search">` [SearchField] builder.
 */
inline fun AbstractContainer.searchInput(
    query: String = "",
    placeholder: String = "Search...",
    classes: String? = null,
    id: String? = null,
    noinline onValueChanged: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker SearchField).() -> Unit = {}
): SearchField = mountDslComponent(
    this,
    SearchField(query = query, placeholder = placeholder),
    classes,
    id
) {
    if (onValueChanged != null) {
        this.onValueChanged = onValueChanged
    }
    block()
}

/**
 * Alias for [searchInput].
 */
inline fun AbstractContainer.searchField(
    query: String = "",
    placeholder: String = "Search...",
    classes: String? = null,
    id: String? = null,
    noinline onValueChanged: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker SearchField).() -> Unit = {}
): SearchField = searchInput(
    query = query,
    placeholder = placeholder,
    classes = classes,
    id = id,
    onValueChanged = onValueChanged,
    block = block
)

/**
 * In-place `<select>` [Select] builder matching `kotlinx.html`'s `select`.
 */
inline fun AbstractContainer.select(
    options: List<SelectOption> = emptyList(),
    selectedValue: String? = options.firstOrNull()?.value,
    classes: String? = null,
    id: String? = null,
    noinline onSelectionChanged: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker Select).() -> Unit = {}
): Select = mountDslComponent(
    this,
    Select(options = options, selectedValue = selectedValue),
    classes,
    id
) {
    if (onSelectionChanged != null) {
        this.onSelectionChanged = onSelectionChanged
    }
    block()
}

/**
 * In-place `<option>` helper on [Select] matching `kotlinx.html`'s `select { option(...) }`.
 */
fun Select.option(value: String, label: String = value, selected: Boolean = false): Select = apply {
    addOption(value, label)
    if (selected) {
        selectedValue = value
    }
}

/**
 * In-place `<input type="checkbox">` [CheckBox] builder.
 */
inline fun AbstractContainer.checkBox(
    label: String = "",
    checked: Boolean = false,
    classes: String? = null,
    id: String? = null,
    noinline onCheckedChanged: ((Boolean) -> Unit)? = null,
    block: (@GenericUiDslMarker CheckBox).() -> Unit = {}
): CheckBox = mountDslComponent(
    this,
    CheckBox(label = label, checked = checked),
    classes,
    id
) {
    if (onCheckedChanged != null) {
        this.onCheckedChanged = onCheckedChanged
    }
    block()
}

/**
 * In-place toggle [Switch] builder.
 */
inline fun AbstractContainer.switch(
    label: String = "",
    checked: Boolean = false,
    classes: String? = null,
    id: String? = null,
    noinline onCheckedChanged: ((Boolean) -> Unit)? = null,
    block: (@GenericUiDslMarker Switch).() -> Unit = {}
): Switch = mountDslComponent(
    this,
    Switch(label = label, checked = checked),
    classes,
    id
) {
    if (onCheckedChanged != null) {
        this.onCheckedChanged = onCheckedChanged
    }
    block()
}

/**
 * In-place [RadioButton] builder.
 */
inline fun AbstractContainer.radioButton(
    optionValue: String,
    label: String = optionValue,
    checked: Boolean = false,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker RadioButton).() -> Unit = {}
): RadioButton {
    val rb = RadioButton(optionValue = optionValue, label = label, checked = checked)
    if (!classes.isNullOrBlank()) rb.addClass(classes)
    if (!id.isNullOrBlank()) rb.id = id
    rb.block()
    if (this is RadioGroup) {
        this.addRadioButton(rb)
    } else {
        this.addChild(rb)
    }
    return rb
}

/**
 * In-place [RadioGroup] builder.
 */
inline fun AbstractContainer.radioGroup(
    selectedValue: String? = null,
    classes: String? = null,
    id: String? = null,
    noinline onSelectionChanged: ((String) -> Unit)? = null,
    block: (@GenericUiDslMarker RadioGroup).() -> Unit = {}
): RadioGroup = mountDslComponent(this, RadioGroup(), classes, id) {
    if (onSelectionChanged != null) {
        this.onSelectionChanged = onSelectionChanged
    }
    block()
    if (selectedValue != null) {
        this.selectedValue = selectedValue
    }
}

/**
 * In-place [RangeSlider] builder.
 */
inline fun AbstractContainer.rangeSlider(
    minValue: Double = 0.0,
    maxValue: Double = 100.0,
    min: Double = 0.0,
    max: Double = 100.0,
    step: Double = 1.0,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker RangeSlider).() -> Unit = {}
): RangeSlider = mountDslComponent(
    this,
    RangeSlider(minValue = minValue, maxValue = maxValue, min = min, max = max, step = step),
    classes,
    id,
    block
)

/**
 * In-place [CompoundUnitInput] builder (e.g., Feet + Inches or Pounds + Ounces).
 */
inline fun AbstractContainer.compoundUnitInput(
    primaryUnitSuffix: String = "ft.",
    secondaryUnitSuffix: String = "in.",
    subdivisionsPerPrimary: Double = 12.0,
    minTotal: Double = 0.0,
    maxTotal: Double = 1000.0,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker CompoundUnitInput).() -> Unit = {}
): CompoundUnitInput = mountDslComponent(
    this,
    CompoundUnitInput(
        primaryUnitSuffix = primaryUnitSuffix,
        secondaryUnitSuffix = secondaryUnitSuffix,
        subdivisionsPerPrimary = subdivisionsPerPrimary,
        minTotal = minTotal,
        maxTotal = maxTotal
    ),
    classes,
    id,
    block
)

/**
 * In-place [FormGroup] builder.
 */
inline fun AbstractContainer.formGroup(
    labelText: String = "",
    helpText: String? = null,
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker FormGroup).() -> Unit = {}
): FormGroup = mountDslComponent(
    this,
    FormGroup(labelText = labelText, helpText = helpText),
    classes,
    id,
    block
)

/**
 * In-place [InputGroup] builder.
 */
inline fun AbstractContainer.inputGroup(
    classes: String? = null,
    id: String? = null,
    block: (@GenericUiDslMarker InputGroup).() -> Unit = {}
): InputGroup = mountDslComponent(this, InputGroup(), classes, id, block)
