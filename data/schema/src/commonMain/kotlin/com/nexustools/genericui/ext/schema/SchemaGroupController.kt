package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.component.surface.CollapsiblePanel
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale

internal class SchemaGroupController(
    private val groupSpec: SchemaGroupSpec,
    initialLabel: String,
    private val labelTranslator: ((String) -> String)?,
    private val customRenderers: Map<String, SchemaCustomFieldRenderer>,
    private val evalContextProvider: () -> SchemaEvalContext
) : SchemaFieldController() {

    override val spec: SchemaNode = groupSpec
    override val key: String = groupSpec.key

    private val changeListeners = mutableListOf<(Any?) -> Unit>()

    private val childSchema = ObjectSchema(
        id = "${groupSpec.key}_group",
        title = "",
        description = null,
        nodes = groupSpec.children
    )

    internal val childForm: SchemaForm = SchemaForm(
        schema = childSchema,
        layoutMode = SchemaLayoutMode.STACKED,
        labelTranslator = labelTranslator,
        customRenderers = customRenderers,
        parentContextProvider = { evalContextProvider() }
    )

    private val collapsiblePanel: CollapsiblePanel?
    private val cardContainer: Card?

    override val labelComponent: Label?
    override val labelHeaderContainer: HStack = HStack(SpacingScale.S2).apply {
        alignItems = AlignItems.CENTER
    }
    override val errorLabel: Label? = null
    override val descriptionLabel: Text? = if (!groupSpec.description.isNullOrBlank()) {
        Text(groupSpec.description).apply {
            addClass("form-text")
            textColorType = BootstrapType.SECONDARY
        }
    } else {
        null
    }

    override val rootComponent: AbstractComponent

    init {
        if (groupSpec.collapsible) {
            val titleText = initialLabel.ifBlank { groupSpec.key }
            val panel = CollapsiblePanel(title = titleText, expanded = groupSpec.defaultExpanded)
            descriptionLabel?.let { panel.addChild(it) }
            panel.addChild(childForm)
            collapsiblePanel = panel
            cardContainer = null
            labelComponent = null
            rootComponent = panel
        } else if (groupSpec.bordered) {
            val card = Card(title = if (initialLabel.isNotBlank()) initialLabel else null).apply {
                addClass("gui-schema-group")
                addClass("p-2")
            }
            descriptionLabel?.let { card.addChild(it) }
            card.addChild(childForm)
            collapsiblePanel = null
            cardContainer = card
            labelComponent = null
            rootComponent = card
        } else {
            val container = StyledContainer(classPrefix = "gui-schema-group").apply {
                if (initialLabel.isNotBlank()) {
                    val lbl = Label(initialLabel)
                    labelHeaderContainer.addChild(lbl)
                }
            }
            labelComponent = labelHeaderContainer.children.firstOrNull() as? Label
            val box = VStack(SpacingScale.S1)
            if (initialLabel.isNotBlank()) {
                box.addChild(labelHeaderContainer)
            }
            descriptionLabel?.let { box.addChild(it) }
            box.addChild(childForm)
            container.addChild(box)
            collapsiblePanel = null
            cardContainer = null
            rootComponent = container
        }

        childForm.onChange { subKey, subVal ->
            val currentMap = childForm.getData()
            for (listener in changeListeners.toList()) {
                listener(currentMap)
            }
            onAnyChangedCallback?.invoke("${groupSpec.key}.$subKey", subVal)
        }
        groupSpec.onMounted?.invoke(childForm)
    }

    override var value: Any?
        get() = childForm.getData()
        set(newVal) {
            childForm.setData(SchemaValueCodec.coerceToMap(newVal), markAsSaved = false)
        }

    override var savedValue: Any?
        get() = childForm.getSavedData()
        set(newVal) {
            childForm.updateSavedBaseline(SchemaValueCodec.coerceToMap(newVal))
        }

    override val isChanged: Boolean
        get() = childForm.isChanged

    override val stringValue: String
        get() = SchemaJsonCodec.toJson(childForm.getData())

    override fun setDataValue(value: Any?, markAsSaved: Boolean) {
        childForm.setData(SchemaValueCodec.coerceToMap(value), markAsSaved = markAsSaved)
    }

    override fun setValueAndSaved(value: Any?) {
        setDataValue(value, markAsSaved = true)
    }

    override fun save() {
        childForm.save()
    }

    override fun reset() {
        childForm.reset()
    }

    override fun resetToDefault() {
        childForm.resetToDefaults()
    }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        if (enabled) childForm.enableAll() else childForm.disableAll()
    }

    override fun validate(context: SchemaEvalContext): Boolean {
        if (!isVisible) return true
        return childForm.validate()
    }

    override fun refreshOptions(context: SchemaEvalContext) {
        childForm.refreshDynamicOptions()
    }

    override fun reconcileNestedContext(parentContext: SchemaEvalContext) {
        childForm.reconcileConditionsAndRules(refreshParentDependentSelects = true)
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
                val text = constraintValue?.toString().orEmpty()
                collapsiblePanel?.title = text
                cardContainer?.title = text
                labelComponent?.text = text
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

    override fun findNestedController(path: String): SchemaFieldController? =
        childForm.findField(path)

    override fun getModifiedDataValue(): Pair<Boolean, Any?> {
        val modified = childForm.getModifiedData()
        return if (modified.isNotEmpty()) true to modified else false to null
    }

    override fun getModifiedStringDataValue(): Pair<Boolean, String> {
        val modified = childForm.getModifiedData()
        return if (modified.isNotEmpty()) true to SchemaJsonCodec.toJson(modified) else false to ""
    }
}
