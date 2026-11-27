package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.SelectOption
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.style.color.RgbaColor

internal class PolymorphicRowListSchemaFieldController(
    private val polySpec: PolymorphicRowListFieldSpec,
    initialLabel: String,
    compactInline: Boolean,
    private val labelTranslator: ((String) -> String)?,
    private val evalContextProvider: () -> SchemaEvalContext,
    private val controllerFactory: (SchemaFieldSpec, String, Boolean) -> SchemaFieldController
) : SchemaBaseFieldController(polySpec, initialLabel, compactInline) {

    private val rowsData = mutableListOf<MutableList<String?>>()
    private val mountedRowSlots = mutableListOf<List<SchemaFieldController>>()
    private val actionButtons = mutableListOf<Button>()

    override var savedValue: Any? = SchemaValueCodec.coerceValue(polySpec, polySpec.defaultValue)

    internal val variantPicker = Select(
        options = polySpec.variants.map { SelectOption(it.id, it.label) },
        selectedValue = polySpec.variants.firstOrNull()?.id
    ).apply {
        isVisible = polySpec.variants.size > 1
    }

    internal val addButton = Button(
        text = polySpec.addButtonText,
        type = BootstrapType.PRIMARY,
        size = BootstrapSize.SM
    ).apply {
        onClick = { addSelectedVariantRow() }
    }

    internal val rowsContainer = VStack(SpacingScale.S2).apply {
        addClass("gui-schema-poly-rows")
    }

    init {
        val initialRows = SchemaValueCodec.coercePolymorphicRowsValue(polySpec, polySpec.defaultValue)
        for (row in initialRows) {
            rowsData.add(row.toMutableList())
        }

        val headerBar = HStack(SpacingScale.S2).apply {
            alignItems = AlignItems.CENTER
            if (polySpec.variants.size > 1) {
                addChild(variantPicker, weight = 1f)
            }
            addChild(addButton)
        }
        val bodyContainer = VStack(SpacingScale.S2).apply {
            addChild(headerBar)
            addChild(rowsContainer)
        }
        bindLabelTarget(if (polySpec.variants.size > 1) variantPicker else addButton)
        mountStandardBody(bodyContainer, compactInline, rowsContainer)
        rebuildRows()
        syncHeaderState()
    }

    override var value: Any?
        get() {
            val snapshot = rowsData.map { it.toList() }
            return if (polySpec.serializeAsJsonString) {
                SchemaJsonCodec.toJson(snapshot)
            } else {
                snapshot
            }
        }
        set(newVal) {
            val coerced = SchemaValueCodec.coercePolymorphicRowsValue(polySpec, newVal)
            val currentSnapshot = rowsData.map { it.toList() }
            if (currentSnapshot != coerced) {
                rowsData.clear()
                for (row in coerced) {
                    rowsData.add(row.toMutableList())
                }
                rebuildRows()
                syncHeaderState()
                notifyValueChanged(value)
            }
        }

    fun addRow(variantId: String? = null): Boolean {
        if (!isEnabled) return false
        val cap = polySpec.maxRows
        if (cap != null && cap >= 0 && rowsData.size >= cap) return false
        val variant = resolveVariantById(variantId ?: variantPicker.selectedValue) ?: return false
        val newRow = mutableListOf<String?>()
        if (polySpec.includeVariantIdInRow || polySpec.variants.size > 1) {
            newRow.add(variant.id)
        }
        for (slotSpec in variant.slotFields) {
            val defVal = SchemaValueCodec.defaultFieldValue(slotSpec)
            newRow.add(if (defVal == null) null else SchemaValueCodec.toWireString(slotSpec, defVal))
        }
        rowsData.add(newRow)
        rebuildRows()
        syncHeaderState()
        notifyValueChanged(value)
        return true
    }

    fun removeRowAt(index: Int): Boolean {
        if (!isEnabled || !polySpec.allowDeletion || index < 0 || index >= rowsData.size) return false
        rowsData.removeAt(index)
        rebuildRows()
        syncHeaderState()
        notifyValueChanged(value)
        return true
    }

    fun moveRowUp(index: Int): Boolean {
        if (!isEnabled || !polySpec.allowReordering || index <= 0 || index >= rowsData.size) return false
        val tmp = rowsData[index - 1]
        rowsData[index - 1] = rowsData[index]
        rowsData[index] = tmp
        rebuildRows()
        syncHeaderState()
        notifyValueChanged(value)
        return true
    }

    fun moveRowDown(index: Int): Boolean {
        if (!isEnabled || !polySpec.allowReordering || index < 0 || index >= rowsData.lastIndex) return false
        val tmp = rowsData[index + 1]
        rowsData[index + 1] = rowsData[index]
        rowsData[index] = tmp
        rebuildRows()
        syncHeaderState()
        notifyValueChanged(value)
        return true
    }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        syncHeaderState()
        for (slotList in mountedRowSlots) {
            for (slotCtrl in slotList) {
                slotCtrl.formEnabled = enabled
                slotCtrl.syncEffectiveStates()
            }
        }
        for (btn in actionButtons) {
            btn.isEnabled = enabled
        }
    }

    override fun refreshOptions(context: SchemaEvalContext) {
        for (slotList in mountedRowSlots) {
            for (slotCtrl in slotList) {
                slotCtrl.refreshOptions(context)
            }
        }
    }

    override fun collectBuiltInValidationErrors(
        current: Any?,
        context: SchemaEvalContext,
        label: String,
        errorsOut: MutableList<String>
    ) {
        if (polySpec.required && rowsData.isEmpty()) {
            errorsOut.add("$label is required.")
        }
        val cap = polySpec.maxRows
        if (cap != null && cap >= 0 && rowsData.size > cap) {
            errorsOut.add("$label can contain at most $cap rows.")
        }
        for (slotList in mountedRowSlots) {
            for (slotCtrl in slotList) {
                if (!slotCtrl.validate(context)) {
                    val slotErr = slotCtrl.errorLabel?.text
                    if (!slotErr.isNullOrBlank()) {
                        errorsOut.add(slotErr)
                    }
                }
            }
        }
    }

    private fun addSelectedVariantRow() {
        addRow(variantPicker.selectedValue ?: polySpec.variants.firstOrNull()?.id)
    }

    private fun resolveVariantById(variantId: String?): SchemaRowVariantSpec? {
        if (polySpec.variants.isEmpty()) return null
        if (variantId == null) return polySpec.variants.first()
        return polySpec.variants.firstOrNull { it.id == variantId } ?: polySpec.variants.first()
    }

    private fun syncHeaderState() {
        val cap = polySpec.maxRows
        val atCap = cap != null && cap >= 0 && rowsData.size >= cap
        variantPicker.isEnabled = isEnabled && !atCap && polySpec.variants.size > 1
        addButton.isEnabled = isEnabled && !atCap && polySpec.variants.isNotEmpty()
    }

    private fun rebuildRows() {
        rowsContainer.clearChildren()
        mountedRowSlots.clear()
        actionButtons.clear()

        val hasIdColumn = polySpec.includeVariantIdInRow || polySpec.variants.size > 1
        for (rowIndex in rowsData.indices) {
            val rowValues = rowsData[rowIndex]
            val variant = if (hasIdColumn) {
                resolveVariantById(rowValues.firstOrNull())
            } else {
                polySpec.variants.firstOrNull()
            } ?: continue

            val slotOffset = if (hasIdColumn) 1 else 0
            val expectedLength = slotOffset + variant.slotFields.size
            while (rowValues.size < expectedLength) {
                rowValues.add(null)
            }

            val rowBox = StyledContainer(classPrefix = "gui-schema-poly-row").apply {
                addClass("p-2")
                setBorder(1, RgbaColor(200, 200, 200, 120), radiusPx = 4)
            }
            val rowContent = HStack(SpacingScale.S2).apply {
                alignItems = AlignItems.CENTER
            }
            if (polySpec.variants.size > 1 || hasIdColumn) {
                rowContent.addChild(Badge(variant.label, type = BootstrapType.INFO))
            }

            val rowSlotControllers = mutableListOf<SchemaFieldController>()
            for ((slotIdx, slotSpec) in variant.slotFields.withIndex()) {
                val cellIndex = slotOffset + slotIdx
                val slotLabel = SchemaLabelUtil.resolveLabel(slotSpec.label, slotSpec.key, labelTranslator)
                val slotCtrl = controllerFactory(slotSpec, slotLabel, false)
                val rawCell = rowValues[cellIndex]
                val coercedCell = if (rawCell != null) {
                    SchemaValueCodec.coerceValue(slotSpec, rawCell)
                } else {
                    SchemaValueCodec.defaultFieldValue(slotSpec)
                }
                slotCtrl.setDataValue(coercedCell, markAsSaved = true)
                slotCtrl.formEnabled = isEnabled
                slotCtrl.syncEffectiveStates()
                slotCtrl.onChange { updated ->
                    rowValues[cellIndex] = if (updated == null) {
                        null
                    } else {
                        SchemaValueCodec.toWireString(slotSpec, updated)
                    }
                    notifyValueChanged(value)
                }
                rowSlotControllers.add(slotCtrl)
                rowContent.addChild(slotCtrl.rootComponent, weight = 1f)
            }
            mountedRowSlots.add(rowSlotControllers)

            if (polySpec.allowReordering) {
                val upBtn = Button("⮝", type = BootstrapType.SECONDARY, size = BootstrapSize.SM, outline = true).apply {
                    isEnabled = this@PolymorphicRowListSchemaFieldController.isEnabled && rowIndex > 0
                    onClick = { moveRowUp(rowIndex) }
                }
                val downBtn = Button("⮟", type = BootstrapType.SECONDARY, size = BootstrapSize.SM, outline = true).apply {
                    isEnabled = this@PolymorphicRowListSchemaFieldController.isEnabled && rowIndex < rowsData.lastIndex
                    onClick = { moveRowDown(rowIndex) }
                }
                rowContent.addChild(upBtn)
                rowContent.addChild(downBtn)
                if (rowIndex > 0) actionButtons.add(upBtn)
                if (rowIndex < rowsData.lastIndex) actionButtons.add(downBtn)
            }

            if (polySpec.allowDeletion) {
                val delBtn = Button("X", type = BootstrapType.DANGER, size = BootstrapSize.SM, outline = true).apply {
                    isEnabled = this@PolymorphicRowListSchemaFieldController.isEnabled
                    onClick = { removeRowAt(rowIndex) }
                }
                rowContent.addChild(delBtn)
                actionButtons.add(delBtn)
            }

            rowBox.addChild(rowContent)
            rowsContainer.addChild(rowBox)
        }
    }
}
