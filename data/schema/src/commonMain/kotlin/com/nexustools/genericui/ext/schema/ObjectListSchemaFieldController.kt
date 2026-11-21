package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.style.color.RgbaColor

internal class ObjectListSchemaFieldController(
    private val listSpec: ObjectListFieldSpec,
    initialLabel: String,
    compactInline: Boolean,
    private val labelTranslator: ((String) -> String)?,
    private val customRenderers: Map<String, SchemaCustomFieldRenderer>,
    private val evalContextProvider: () -> SchemaEvalContext
) : SchemaBaseFieldController(listSpec, initialLabel, compactInline) {

    private val itemsData = mutableListOf<LinkedHashMap<String, Any?>>()
    private val itemForms = mutableListOf<SchemaForm>()
    private val actionButtons = mutableListOf<Button>()

    override var savedValue: Any? = SchemaValueCodec.coerceValue(listSpec, listSpec.defaultValue)

    internal val addButton = Button(
        text = listSpec.addButtonText,
        type = BootstrapType.PRIMARY,
        size = BootstrapSize.SM
    ).apply {
        onClick = { addItem() }
    }

    internal val listContainer = VStack(SpacingScale.S2).apply {
        addClass("gui-schema-object-list")
    }

    init {
        val initial = SchemaValueCodec.coerceObjectListValue(listSpec, listSpec.defaultValue)
        for (item in initial) {
            itemsData.add(LinkedHashMap(item))
        }
        val bodyContainer = VStack(SpacingScale.S2).apply {
            addChild(addButton)
            addChild(listContainer)
        }
        mountStandardBody(bodyContainer, compactInline, listContainer)
        rebuildItems()
        syncAddButtonState()
    }

    override var value: Any?
        get() {
            syncItemsFromForms()
            val snapshot = itemsData.map { LinkedHashMap(it) }
            return if (listSpec.serializeAsJsonString) {
                SchemaJsonCodec.toJson(snapshot)
            } else {
                snapshot
            }
        }
        set(newVal) {
            val coerced = SchemaValueCodec.coerceObjectListValue(listSpec, newVal)
            itemsData.clear()
            for (item in coerced) {
                itemsData.add(LinkedHashMap(item))
            }
            rebuildItems()
            syncAddButtonState()
            notifyValueChanged(value)
        }

    fun addItem(initialValues: Map<String, Any?> = emptyMap()): Boolean {
        if (!isEnabled) return false
        val cap = listSpec.maxItems
        if (cap != null && cap >= 0 && itemsData.size >= cap) return false
        syncItemsFromForms()
        val defaults = listSpec.itemSchema.resolveDefaults()
        val merged = SchemaValueCodec.coerceNodesValues(
            listSpec.itemSchema.nodes,
            defaults + initialValues
        )
        itemsData.add(merged)
        rebuildItems()
        syncAddButtonState()
        notifyValueChanged(value)
        return true
    }

    fun removeItemAt(index: Int): Boolean {
        if (!isEnabled || !listSpec.allowDeletion || index < 0 || index >= itemsData.size) return false
        syncItemsFromForms()
        itemsData.removeAt(index)
        rebuildItems()
        syncAddButtonState()
        notifyValueChanged(value)
        return true
    }

    fun moveItemUp(index: Int): Boolean {
        if (!isEnabled || !listSpec.allowReordering || index <= 0 || index >= itemsData.size) return false
        syncItemsFromForms()
        val tmp = itemsData[index - 1]
        itemsData[index - 1] = itemsData[index]
        itemsData[index] = tmp
        rebuildItems()
        syncAddButtonState()
        notifyValueChanged(value)
        return true
    }

    fun moveItemDown(index: Int): Boolean {
        if (!isEnabled || !listSpec.allowReordering || index < 0 || index >= itemsData.lastIndex) return false
        syncItemsFromForms()
        val tmp = itemsData[index + 1]
        itemsData[index + 1] = itemsData[index]
        itemsData[index] = tmp
        rebuildItems()
        syncAddButtonState()
        notifyValueChanged(value)
        return true
    }

    override fun save() {
        syncItemsFromForms()
        for (form in itemForms) {
            form.save()
        }
        super.save()
    }

    override fun reset() {
        super.reset()
        for (form in itemForms) {
            form.save()
        }
    }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        syncAddButtonState()
        for (form in itemForms) {
            if (enabled) form.enableAll() else form.disableAll()
        }
        for (btn in actionButtons) {
            btn.isEnabled = enabled
        }
    }

    override fun refreshOptions(context: SchemaEvalContext) {
        for (form in itemForms) {
            form.refreshDynamicOptions()
        }
    }

    override fun reconcileNestedContext(parentContext: SchemaEvalContext) {
        for (form in itemForms) {
            form.reconcileConditionsAndRules(refreshParentDependentSelects = true)
        }
    }

    override fun collectBuiltInValidationErrors(
        current: Any?,
        context: SchemaEvalContext,
        label: String,
        errorsOut: MutableList<String>
    ) {
        if (listSpec.required && itemsData.isEmpty()) {
            errorsOut.add("$label is required.")
        }
        val cap = listSpec.maxItems
        if (cap != null && cap >= 0 && itemsData.size > cap) {
            errorsOut.add("$label can contain at most $cap items.")
        }
        for (form in itemForms) {
            val res = form.validateDetailed()
            for ((_, msgs) in res.fieldErrors) {
                errorsOut.addAll(msgs)
            }
        }
    }

    override fun findNestedController(path: String): SchemaFieldController? {
        val bracketIdx = path.indexOf('[')
        if (bracketIdx == 0) {
            val closeIdx = path.indexOf(']')
            if (closeIdx > 1) {
                val itemIndex = path.substring(1, closeIdx).toIntOrNull()
                if (itemIndex != null && itemIndex in itemForms.indices) {
                    val rest = path.substring(closeIdx + 1).removePrefix(".")
                    return if (rest.isEmpty()) null else itemForms[itemIndex].findField(rest)
                }
            }
        }
        return null
    }

    private fun syncItemsFromForms() {
        for (i in itemForms.indices) {
            if (i < itemsData.size) {
                itemsData[i] = itemForms[i].getData()
            }
        }
    }

    private fun syncAddButtonState() {
        val cap = listSpec.maxItems
        val atCap = cap != null && cap >= 0 && itemsData.size >= cap
        addButton.isEnabled = isEnabled && !atCap
    }

    private fun rebuildItems() {
        listContainer.clearChildren()
        itemForms.clear()
        actionButtons.clear()

        for (index in itemsData.indices) {
            val itemMap = itemsData[index]
            val itemBox = StyledContainer(classPrefix = "gui-schema-object-list-item").apply {
                addClass("p-2")
                setBorder(1, RgbaColor(200, 200, 200, 120), radiusPx = 4)
            }
            val headerRow = HStack(SpacingScale.S1).apply {
                alignItems = AlignItems.CENTER
                addChild(Label("#${index + 1}"), weight = 1f)
            }
            if (listSpec.allowReordering) {
                val upBtn = Button("⮝", type = BootstrapType.SECONDARY, size = BootstrapSize.SM, outline = true).apply {
                    isEnabled = this@ObjectListSchemaFieldController.isEnabled && index > 0
                    onClick = { moveItemUp(index) }
                }
                val downBtn = Button("⮟", type = BootstrapType.SECONDARY, size = BootstrapSize.SM, outline = true).apply {
                    isEnabled = this@ObjectListSchemaFieldController.isEnabled && index < itemsData.lastIndex
                    onClick = { moveItemDown(index) }
                }
                headerRow.addChild(upBtn)
                headerRow.addChild(downBtn)
                if (index > 0) actionButtons.add(upBtn)
                if (index < itemsData.lastIndex) actionButtons.add(downBtn)
            }
            if (listSpec.allowDeletion) {
                val delBtn = Button("X", type = BootstrapType.DANGER, size = BootstrapSize.SM, outline = true).apply {
                    isEnabled = this@ObjectListSchemaFieldController.isEnabled
                    onClick = { removeItemAt(index) }
                }
                headerRow.addChild(delBtn)
                actionButtons.add(delBtn)
            }

            val itemForm = SchemaForm(
                schema = listSpec.itemSchema,
                layoutMode = SchemaLayoutMode.STACKED,
                initialData = itemMap,
                labelTranslator = labelTranslator,
                customRenderers = customRenderers,
                parentContextProvider = {
                    val parentCtx = evalContextProvider()
                    SchemaEvalContext(
                        values = parentCtx.values,
                        parent = parentCtx.parent,
                        index = index,
                        stringValues = parentCtx.stringValues
                    )
                }
            )
            itemForm.setData(itemMap, markAsSaved = true)
            if (!isEnabled) {
                itemForm.disableAll()
            }
            itemForm.onChange { _, _ ->
                if (!suppressUiSync && index < itemsData.size) {
                    itemsData[index] = itemForm.getData()
                    notifyValueChanged(value)
                }
            }
            itemForms.add(itemForm)

            val cardContent = VStack(SpacingScale.S2).apply {
                addChild(headerRow)
                addChild(itemForm)
            }
            itemBox.addChild(cardContent)
            listContainer.addChild(itemBox)
        }
    }
}
