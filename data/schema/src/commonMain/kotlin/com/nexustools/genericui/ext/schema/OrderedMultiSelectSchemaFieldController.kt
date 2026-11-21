package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.form.ComboBox
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.SelectOption
import com.nexustools.genericui.component.layout.Divider
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale

internal class OrderedMultiSelectSchemaFieldController(
    private val multiSpec: OrderedMultiSelectFieldSpec,
    initialLabel: String,
    compactInline: Boolean,
    initialContext: SchemaEvalContext,
    deferInitialOptionsLoad: Boolean = false
) : SchemaBaseFieldController(multiSpec, initialLabel, compactInline) {

    private val selectedItems = mutableListOf<String>().apply {
        addAll(SchemaValueCodec.coerceOrderedMultiSelectValue(multiSpec, multiSpec.defaultValue))
    }

    override var savedValue: Any? = SchemaValueCodec.coerceValue(multiSpec, multiSpec.defaultValue)

    private var dynamicOptionsOverride: List<SchemaOption>? = null
    private var dynamicFilterOverride: Map<String, String>? = null
    private var maxItemsOverride: Int? = null
    private var hasMaxItemsOverride: Boolean = false
    private var dynamicSeparatorOverride: ((Int) -> String?)? = null
    private var hasSeparatorOverride: Boolean = false
    private var resolvedOptions: List<SchemaOption> = emptyList()
    private val optionLabelsByValue = LinkedHashMap<String, String>()
    private var lastEvalContext: SchemaEvalContext = initialContext
    private var loadGeneration: Long = 0L

    private val capacityBadge = Badge("", type = BootstrapType.SECONDARY, pill = true).apply {
        isVisible = false
    }

    internal val pickerSelect: Select = if (multiSpec.searchable) {
        ComboBox(allowCustomInput = true)
    } else {
        Select()
    }

    internal val addButton = Button(
        text = multiSpec.addButtonText,
        type = BootstrapType.PRIMARY,
        size = BootstrapSize.SM
    ).apply {
        onClick = { addSelectedFromPicker() }
    }

    internal val itemsContainer = VStack(SpacingScale.S1).apply {
        addClass("gui-schema-multiselect-list")
    }

    private val moveUpButtons = mutableListOf<Button>()
    private val moveDownButtons = mutableListOf<Button>()
    private val deleteButtons = mutableListOf<Button>()

    init {
        labelHeaderContainer.addChild(capacityBadge)
        val pickerRow = HStack(SpacingScale.S2).apply {
            alignItems = AlignItems.CENTER
            addChild(pickerSelect, weight = 1f)
            addChild(addButton)
        }
        val bodyContainer = VStack(SpacingScale.S2).apply {
            addChild(pickerRow)
            addChild(itemsContainer)
        }
        mountStandardBody(bodyContainer, compactInline, pickerSelect, itemsContainer)
        if (!deferInitialOptionsLoad) {
            refreshOptions(initialContext)
        }
        rebuildRows()
        syncPickerAndCapacityUi()
    }

    override var value: Any?
        get() = if (multiSpec.serializeAsJsonString) {
            SchemaJsonCodec.toJson(selectedItems.toList())
        } else {
            selectedItems.toList()
        }
        set(newVal) {
            val coerced = SchemaValueCodec.coerceOrderedMultiSelectValue(multiSpec, newVal)
            if (selectedItems != coerced) {
                selectedItems.clear()
                selectedItems.addAll(coerced)
                rebuildRows()
                syncPickerAndCapacityUi()
                notifyValueChanged(value)
            }
        }

    fun addItem(candidate: String): Boolean {
        if (!isEnabled) return false
        val cap = effectiveMaxItems()
        if (cap != null && cap >= 0 && selectedItems.size >= cap) return false
        if (!multiSpec.allowDuplicates && candidate in selectedItems) return false
        selectedItems.add(candidate)
        rebuildRows()
        syncPickerAndCapacityUi()
        notifyValueChanged(value)
        return true
    }

    fun moveItemUp(index: Int): Boolean {
        if (!isEnabled || index <= 0 || index >= selectedItems.size) return false
        val tmp = selectedItems[index - 1]
        selectedItems[index - 1] = selectedItems[index]
        selectedItems[index] = tmp
        rebuildRows()
        syncPickerAndCapacityUi()
        notifyValueChanged(value)
        return true
    }

    fun moveItemDown(index: Int): Boolean {
        if (!isEnabled || index < 0 || index >= selectedItems.lastIndex) return false
        val tmp = selectedItems[index + 1]
        selectedItems[index + 1] = selectedItems[index]
        selectedItems[index] = tmp
        rebuildRows()
        syncPickerAndCapacityUi()
        notifyValueChanged(value)
        return true
    }

    fun removeItemAt(index: Int): Boolean {
        if (!isEnabled || !multiSpec.allowDeletion || index < 0 || index >= selectedItems.size) return false
        selectedItems.removeAt(index)
        rebuildRows()
        syncPickerAndCapacityUi()
        notifyValueChanged(value)
        return true
    }

    override fun refreshOptions(context: SchemaEvalContext) {
        lastEvalContext = context
        val generation = ++loadGeneration

        val overrideOpts = dynamicOptionsOverride
        if (overrideOpts != null) {
            applyResolvedOptions(overrideOpts, context)
            return
        }

        val asyncLoader = multiSpec.asyncOptionsLoader
        if (asyncLoader != null) {
            val filters = SchemaOptionResolver.resolveQueryFilters(
                multiSpec.queryFilterRefs,
                dynamicFilterOverride,
                context
            )
            if (filters == null) {
                applyResolvedOptions(emptyList(), context)
                return
            }
            if (multiSpec.staticOptions.isNotEmpty() && resolvedOptions.isEmpty()) {
                applyResolvedOptions(multiSpec.staticOptions, context)
            }
            asyncLoader.invoke(context, filters).onComplete(
                onSuccess = { loaded ->
                    if (generation == loadGeneration) {
                        setValidationErrors(emptyList())
                        applyResolvedOptions(loaded, context)
                    }
                },
                onFailure = { error ->
                    if (generation == loadGeneration) {
                        setValidationErrors(listOf(error.message ?: "Failed to load options."))
                    }
                }
            )
            return
        }

        val raw = multiSpec.optionsProvider?.invoke(context) ?: multiSpec.staticOptions
        applyResolvedOptions(raw, context)
    }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        syncPickerAndCapacityUi()
        for (i in moveUpButtons.indices) {
            moveUpButtons[i].isEnabled = enabled && i > 0
        }
        for (i in moveDownButtons.indices) {
            moveDownButtons[i].isEnabled = enabled && i < selectedItems.lastIndex
        }
        for (btn in deleteButtons) {
            btn.isEnabled = enabled
        }
    }

    override fun collectBuiltInValidationErrors(
        current: Any?,
        context: SchemaEvalContext,
        label: String,
        errorsOut: MutableList<String>
    ) {
        if (multiSpec.required && selectedItems.isEmpty()) {
            errorsOut.add("$label is required.")
        }
        val cap = effectiveMaxItems()
        if (cap != null && cap >= 0 && selectedItems.size > cap) {
            errorsOut.add("$label can contain at most $cap items.")
        }
    }

    override fun applySpecificConstraint(target: SchemaConstraintTarget, constraintValue: Any?) {
        when (target) {
            SchemaConstraintTarget.OPTIONS -> {
                dynamicOptionsOverride = SchemaOptionResolver.coerceOptionsList(constraintValue)
                refreshOptions(lastEvalContext)
            }
            SchemaConstraintTarget.QUERY_FILTER -> {
                dynamicFilterOverride = SchemaOptionResolver.coerceFilterMap(constraintValue)
                refreshOptions(lastEvalContext)
            }
            SchemaConstraintTarget.MAX -> {
                hasMaxItemsOverride = true
                maxItemsOverride = when (constraintValue) {
                    null -> null
                    is Number -> constraintValue.toInt()
                    else -> constraintValue.toString().trim().toIntOrNull()
                }
                syncPickerAndCapacityUi()
            }
            SchemaConstraintTarget.ITEM_SEPARATOR -> {
                @Suppress("UNCHECKED_CAST")
                dynamicSeparatorOverride = constraintValue as? ((Int) -> String?)
                hasSeparatorOverride = true
                rebuildRows()
            }
            else -> Unit
        }
    }

    private fun effectiveMaxItems(): Int? =
        if (hasMaxItemsOverride) maxItemsOverride else multiSpec.maxItems

    private fun addSelectedFromPicker() {
        val candidate = pickerSelect.selectedValue?.takeIf { it.isNotEmpty() }
            ?: availablePickerOptions().firstOrNull()?.value
            ?: return
        addItem(candidate)
    }

    private fun availablePickerOptions(): List<SchemaOption> =
        if (multiSpec.allowDuplicates) {
            resolvedOptions
        } else {
            resolvedOptions.filter { it.value !in selectedItems }
        }

    private fun applyResolvedOptions(raw: List<SchemaOption>, context: SchemaEvalContext) {
        resolvedOptions = SchemaOptionResolver.transformAndPrepend(
            rawOptions = raw,
            prependNullOption = null,
            transformer = multiSpec.optionTransformer,
            context = context
        )
        optionLabelsByValue.clear()
        for (opt in resolvedOptions) {
            optionLabelsByValue[opt.value] = opt.label
        }
        rebuildRows()
        syncPickerAndCapacityUi()
    }

    private fun syncPickerAndCapacityUi() {
        val cap = effectiveMaxItems()
        if (cap != null && cap >= 0) {
            val remaining = (cap - selectedItems.size).coerceAtLeast(0)
            capacityBadge.text = "$remaining Left"
            capacityBadge.type = if (remaining == 0) BootstrapType.WARNING else BootstrapType.SECONDARY
            capacityBadge.isVisible = true
        } else {
            capacityBadge.isVisible = false
        }
        labelHeaderContainer.isVisible = labelComponent.text.isNotBlank() || capacityBadge.isVisible

        val available = availablePickerOptions()
        val prev = pickerSelect.selectedValue
        suppressUiSync = true
        try {
            pickerSelect.setOptions(available.map { SelectOption(it.value, it.label) })
            if (prev != null && available.any { it.value == prev }) {
                pickerSelect.selectedValue = prev
            }
        } finally {
            suppressUiSync = false
        }

        val atCapacity = cap != null && cap >= 0 && selectedItems.size >= cap
        val canAdd = isEnabled && !atCapacity && (available.isNotEmpty() || multiSpec.searchable)
        pickerSelect.isEnabled = isEnabled && !atCapacity && (available.isNotEmpty() || multiSpec.searchable)
        addButton.isEnabled = canAdd
    }

    private fun rebuildRows() {
        itemsContainer.clearChildren()
        moveUpButtons.clear()
        moveDownButtons.clear()
        deleteButtons.clear()

        val separatorProvider = if (hasSeparatorOverride) {
            dynamicSeparatorOverride
        } else {
            multiSpec.itemSeparatorProvider
        }

        for ((index, itemValue) in selectedItems.withIndex()) {
            val sepTitle = separatorProvider?.invoke(index + 1)
            if (!sepTitle.isNullOrBlank()) {
                val sepRow = HStack(SpacingScale.S2).apply {
                    alignItems = AlignItems.CENTER
                    addClass("gui-schema-item-separator")
                    addChild(Label(sepTitle).apply { textColorType = BootstrapType.SECONDARY })
                    addChild(Divider(), weight = 1f)
                }
                itemsContainer.addChild(sepRow)
            }

            val displayLabel = optionLabelsByValue[itemValue] ?: itemValue
            val row = HStack(SpacingScale.S1).apply {
                alignItems = AlignItems.CENTER
                addClass("gui-schema-multiselect-row")
                addChild(Label(displayLabel), weight = 1f)
            }

            val upBtn = Button(
                text = "⮝",
                type = BootstrapType.SECONDARY,
                size = BootstrapSize.SM,
                outline = true
            ).apply {
                isEnabled = this@OrderedMultiSelectSchemaFieldController.isEnabled && index > 0
                onClick = { moveItemUp(index) }
            }
            val downBtn = Button(
                text = "⮟",
                type = BootstrapType.SECONDARY,
                size = BootstrapSize.SM,
                outline = true
            ).apply {
                isEnabled = this@OrderedMultiSelectSchemaFieldController.isEnabled && index < selectedItems.lastIndex
                onClick = { moveItemDown(index) }
            }
            row.addChild(upBtn)
            row.addChild(downBtn)
            moveUpButtons.add(upBtn)
            moveDownButtons.add(downBtn)

            if (multiSpec.allowDeletion) {
                val delBtn = Button(
                    text = "X",
                    type = BootstrapType.DANGER,
                    size = BootstrapSize.SM,
                    outline = true
                ).apply {
                    isEnabled = this@OrderedMultiSelectSchemaFieldController.isEnabled
                    onClick = { removeItemAt(index) }
                }
                row.addChild(delBtn)
                deleteButtons.add(delBtn)
            }

            itemsContainer.addChild(row)
        }
    }
}
