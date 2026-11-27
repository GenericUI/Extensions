package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.SelectOption
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.style.color.RgbaColor

internal class SchemaIndexedGroupController(
    private val indexedSpec: SchemaIndexedGroupSpec,
    initialLabel: String,
    private val labelTranslator: ((String) -> String)?,
    private val customRenderers: Map<String, SchemaCustomFieldRenderer>,
    private val evalContextProvider: () -> SchemaEvalContext
) : SchemaFieldController() {

    override val spec: SchemaNode = indexedSpec
    override val key: String = indexedSpec.key

    private val changeListeners = mutableListOf<(Any?) -> Unit>()
    private val slotSchema = ObjectSchema(
        id = "${indexedSpec.key}_slot",
        title = "",
        description = null,
        nodes = indexedSpec.children
    )
    private val slotsData = Array(indexedSpec.count) { slotSchema.resolveDefaults() }
    private var savedSlots: List<LinkedHashMap<String, Any?>> =
        List(indexedSpec.count) { idx -> LinkedHashMap(slotsData[idx]) }
    private val slotForms = Array<SchemaForm?>(indexedSpec.count) { null }
    private var activeIndex: Int = 0
    private var suppressSwitch = false

    internal val indexSelect = Select(
        options = (1..indexedSpec.count).map { oneBased ->
            SelectOption(
                value = (oneBased - 1).toString(),
                label = "${indexedSpec.indexLabelPrefix} $oneBased".trim()
            )
        },
        selectedValue = "0"
    )

    private val slotHost = VStack(SpacingScale.S2)

    override val labelComponent: Label? = if (initialLabel.isNotBlank()) Label(initialLabel) else null
    override val labelHeaderContainer: HStack = HStack(SpacingScale.S2).apply {
        alignItems = AlignItems.CENTER
        labelComponent?.let { addChild(it, weight = 1f) }
        addChild(indexSelect)
    }
    override val errorLabel: Label? = null
    override val descriptionLabel: Text? = if (!indexedSpec.description.isNullOrBlank()) {
        Text(indexedSpec.description).apply {
            addClass("form-text")
            textColorType = BootstrapType.SECONDARY
        }
    } else {
        null
    }

    override val rootComponent: AbstractComponent

    init {
        labelComponent?.target = indexSelect
        indexSelect.onSelectionChanged = { selectedStr ->
            if (!suppressSwitch) {
                val target = selectedStr.toIntOrNull()
                if (target != null && target in 0 until indexedSpec.count && target != activeIndex) {
                    selectSlot(target)
                }
            }
        }

        val inner = VStack(SpacingScale.S2).apply {
            addChild(labelHeaderContainer)
            descriptionLabel?.let { addChild(it) }
            addChild(slotHost)
        }
        rootComponent = if (indexedSpec.bordered) {
            StyledContainer(classPrefix = "gui-schema-indexed-group").apply {
                addClass("p-2")
                setBorder(1, RgbaColor(200, 200, 200, 120), radiusPx = 4)
                addChild(inner)
            }
        } else {
            inner.apply { addClass("gui-schema-indexed-group") }
        }

        selectSlot(0)
    }

    fun selectSlot(index: Int) {
        val target = index.coerceIn(0, indexedSpec.count - 1)
        activeIndex = target
        suppressSwitch = true
        try {
            indexSelect.selectedValue = target.toString()
        } finally {
            suppressSwitch = false
        }
        val form = getOrCreateSlotForm(target)
        slotHost.clearChildren()
        slotHost.addChild(form)
    }

    private fun getOrCreateSlotForm(index: Int): SchemaForm {
        slotForms[index]?.let { return it }
        val form = SchemaForm(
            schema = slotSchema,
            layoutMode = SchemaLayoutMode.STACKED,
            initialData = savedSlots[index],
            labelTranslator = labelTranslator,
            customRenderers = customRenderers,
            parentContextProvider = {
                val base = evalContextProvider()
                SchemaEvalContext(
                    values = base.values,
                    parent = base.parent,
                    index = index,
                    stringValues = base.stringValues
                )
            }
        )
        form.setData(savedSlots[index], markAsSaved = true)
        if (!SchemaValueCodec.valuesEqual(null, slotsData[index], savedSlots[index])) {
            form.setData(slotsData[index], markAsSaved = false)
        }
        if (!isEnabled) {
            form.disableAll()
        }
        form.onChange { subKey, subVal ->
            slotsData[index] = form.getData()
            val snapshot = value
            for (listener in changeListeners.toList()) {
                listener(snapshot)
            }
            onAnyChangedCallback?.invoke("${indexedSpec.key}[$index].$subKey", subVal)
        }
        slotForms[index] = form
        return form
    }

    private fun snapshotList(): List<Map<String, Any?>> {
        for (i in 0 until indexedSpec.count) {
            val form = slotForms[i]
            if (form != null) {
                slotsData[i] = form.getData()
            }
        }
        return List(indexedSpec.count) { i -> LinkedHashMap(slotsData[i]) }
    }

    override var value: Any?
        get() {
            val list = snapshotList()
            return if (indexedSpec.serializeAsJsonString) {
                SchemaJsonCodec.toJson(list)
            } else {
                list
            }
        }
        set(newVal) {
            setDataValue(newVal, markAsSaved = false)
        }

    override var savedValue: Any?
        get() = if (indexedSpec.serializeAsJsonString) {
            SchemaJsonCodec.toJson(savedSlots)
        } else {
            savedSlots.map { LinkedHashMap(it) }
        }
        set(newVal) {
            val parsed = SchemaValueCodec.coerceIndexedGroupValue(indexedSpec, newVal)
            savedSlots = parsed.map { LinkedHashMap(it) }
            for (i in 0 until indexedSpec.count) {
                slotForms[i]?.updateSavedBaseline(savedSlots[i])
            }
        }

    override val isChanged: Boolean
        get() {
            val current = snapshotList()
            for (i in 0 until indexedSpec.count) {
                if (!SchemaValueCodec.valuesEqual(null, current[i], savedSlots[i])) {
                    return true
                }
            }
            return false
        }

    override val stringValue: String
        get() = SchemaJsonCodec.toJson(snapshotList())

    override fun setDataValue(value: Any?, markAsSaved: Boolean) {
        val parsed = SchemaValueCodec.coerceIndexedGroupValue(indexedSpec, value)
        for (i in 0 until indexedSpec.count) {
            val slotMap = LinkedHashMap(parsed[i])
            slotsData[i] = slotMap
            if (markAsSaved) {
                savedSlots[i].clear()
                savedSlots[i].putAll(slotMap)
            }
            slotForms[i]?.setData(slotMap, markAsSaved = markAsSaved)
        }
        val snapshot = this.value
        for (listener in changeListeners.toList()) {
            listener(snapshot)
        }
    }

    override fun setValueAndSaved(value: Any?) {
        setDataValue(value, markAsSaved = true)
    }

    override fun save() {
        val current = snapshotList()
        savedSlots = current.map { LinkedHashMap(it) }
        for (form in slotForms) {
            form?.save()
        }
    }

    override fun reset() {
        for (i in 0 until indexedSpec.count) {
            slotsData[i] = LinkedHashMap(savedSlots[i])
            slotForms[i]?.reset()
        }
    }

    override fun resetToDefault() {
        val def = slotSchema.resolveDefaults()
        for (i in 0 until indexedSpec.count) {
            slotsData[i] = LinkedHashMap(def)
            slotForms[i]?.resetToDefaults()
        }
    }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        indexSelect.isEnabled = enabled
        for (form in slotForms) {
            if (form != null) {
                if (enabled) form.enableAll() else form.disableAll()
            }
        }
    }

    override fun validate(context: SchemaEvalContext): Boolean {
        if (!isVisible) return true
        var allValid = true
        for (i in 0 until indexedSpec.count) {
            val form = slotForms[i]
            if (form != null) {
                if (!form.validate()) allValid = false
            } else {
                val res = slotSchema.validateValues(slotsData[i], parentContext = context)
                if (!res.isValid) allValid = false
            }
        }
        return allValid
    }

    override fun refreshOptions(context: SchemaEvalContext) {
        for (form in slotForms) {
            form?.refreshDynamicOptions()
        }
    }

    override fun reconcileNestedContext(parentContext: SchemaEvalContext) {
        for (form in slotForms) {
            form?.reconcileConditionsAndRules(refreshParentDependentSelects = true)
        }
    }

    override fun applyConstraint(target: SchemaConstraintTarget, constraintValue: Any?) {
        when (target) {
            SchemaConstraintTarget.VISIBLE -> {
                ruleVisibleOverride = constraintValue?.let { SchemaCondition.isTruthy(it) }
                syncEffectiveStates()
            }
            SchemaConstraintTarget.ENABLED -> {
                ruleEnabledOverride = constraintValue?.let { SchemaCondition.isTruthy(it) }
                syncEffectiveStates()
            }
            SchemaConstraintTarget.LABEL -> {
                labelComponent?.text = constraintValue?.toString().orEmpty()
            }
            SchemaConstraintTarget.DESCRIPTION -> {
                val text = constraintValue?.toString().orEmpty()
                descriptionLabel?.text = text
                descriptionLabel?.isVisible = text.isNotBlank()
            }
            else -> Unit
        }
    }

    override fun onChange(listener: (Any?) -> Unit): Disposable {
        changeListeners.add(listener)
        return object : Disposable {
            override var isDisposed: Boolean = false
                private set

            override fun dispose() {
                if (!isDisposed) {
                    isDisposed = true
                    changeListeners.remove(listener)
                }
            }
        }
    }

    override fun findNestedController(path: String): SchemaFieldController? {
        if (path.startsWith("[")) {
            val closeIdx = path.indexOf(']')
            if (closeIdx > 1) {
                val idx = path.substring(1, closeIdx).toIntOrNull()
                if (idx != null && idx in 0 until indexedSpec.count) {
                    val rest = path.substring(closeIdx + 1).removePrefix(".")
                    return if (rest.isEmpty()) null else getOrCreateSlotForm(idx).findField(rest)
                }
            }
        }
        val dotIdx = path.indexOf('.')
        if (dotIdx > 0) {
            val headIdx = path.substring(0, dotIdx).toIntOrNull()
            if (headIdx != null && headIdx in 0 until indexedSpec.count) {
                return getOrCreateSlotForm(headIdx).findField(path.substring(dotIdx + 1))
            }
        }
        return getOrCreateSlotForm(activeIndex).findField(path)
    }

    override fun getModifiedDataValue(): Pair<Boolean, Any?> {
        if (!isChanged) return false to null
        return true to value
    }

    override fun getModifiedStringDataValue(): Pair<Boolean, String> {
        if (!isChanged) return false to ""
        return true to stringValue
    }
}
