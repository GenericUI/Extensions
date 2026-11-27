package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.feedback.Spinner
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

internal class SchemaAsyncGroupController(
    private val asyncSpec: SchemaAsyncGroupSpec,
    initialLabel: String,
    private val labelTranslator: ((String) -> String)?,
    private val customRenderers: Map<String, SchemaCustomFieldRenderer>,
    private val evalContextProvider: () -> SchemaEvalContext,
    private val onAsyncResolvedCallback: () -> Unit
) : SchemaFieldController() {

    override val spec: SchemaNode = asyncSpec
    override val key: String = asyncSpec.key

    private val changeListeners = mutableListOf<(Any?) -> Unit>()
    private var childForm: SchemaForm? = null
    private var pendingData: LinkedHashMap<String, Any?>? = null
    private var hasPendingData: Boolean = false
    private var pendingMarkAsSaved: Boolean = true
    private var pendingSavedBaseline: LinkedHashMap<String, Any?> = linkedMapOf()
    private var pendingEnabledOverride: Boolean? = null
    private var loadError: Throwable? = null
    private var isLoading: Boolean = true

    private val bodyHost = VStack(SpacingScale.S2)
    private val collapsiblePanel: CollapsiblePanel?
    private val cardContainer: Card?

    override val labelComponent: Label?
    override val labelHeaderContainer: HStack = HStack(SpacingScale.S2).apply {
        alignItems = AlignItems.CENTER
    }
    override val errorLabel: Label? = null
    override val descriptionLabel: Text? = if (!asyncSpec.description.isNullOrBlank()) {
        Text(asyncSpec.description).apply {
            addClass("form-text")
            textColorType = BootstrapType.SECONDARY
        }
    } else {
        null
    }

    override val rootComponent: AbstractComponent

    init {
        if (asyncSpec.collapsible) {
            val panel = CollapsiblePanel(
                title = initialLabel.ifBlank { asyncSpec.key },
                expanded = asyncSpec.defaultExpanded
            )
            descriptionLabel?.let { panel.addChild(it) }
            panel.addChild(bodyHost)
            collapsiblePanel = panel
            cardContainer = null
            labelComponent = null
            rootComponent = panel
        } else if (asyncSpec.bordered) {
            val card = Card(title = if (initialLabel.isNotBlank()) initialLabel else null).apply {
                addClass("gui-schema-async-group")
                addClass("p-2")
            }
            descriptionLabel?.let { card.addChild(it) }
            card.addChild(bodyHost)
            collapsiblePanel = null
            cardContainer = card
            labelComponent = null
            rootComponent = card
        } else {
            val container = StyledContainer(classPrefix = "gui-schema-async-group").apply {
                if (initialLabel.isNotBlank()) {
                    labelHeaderContainer.addChild(Label(initialLabel))
                }
            }
            labelComponent = labelHeaderContainer.children.firstOrNull() as? Label
            val box = VStack(SpacingScale.S1)
            if (initialLabel.isNotBlank()) {
                box.addChild(labelHeaderContainer)
            }
            descriptionLabel?.let { box.addChild(it) }
            box.addChild(bodyHost)
            container.addChild(box)
            collapsiblePanel = null
            cardContainer = null
            rootComponent = container
        }

        startLoading()
    }

    private fun startLoading() {
        isLoading = true
        loadError = null
        bodyHost.clearChildren()
        val loadingRow = HStack(SpacingScale.S2).apply {
            alignItems = AlignItems.CENTER
            addClass("gui-schema-async-loading")
            addChild(Spinner(label = asyncSpec.loadingText))
            addChild(Label(asyncSpec.loadingText))
        }
        bodyHost.addChild(loadingRow)

        val result = try {
            asyncSpec.loader.invoke()
        } catch (t: Throwable) {
            SchemaAsyncResult.failed<List<SchemaNode>>(t)
        }

        result.onComplete(
            onSuccess = { loadedNodes ->
                isLoading = false
                loadError = null
                mountResolvedNodes(loadedNodes)
            },
            onFailure = { error ->
                isLoading = false
                loadError = error
                bodyHost.clearChildren()
                val alert = Alert(
                    message = error.message ?: "Failed to load schema group '${asyncSpec.key}'.",
                    type = BootstrapType.DANGER
                )
                bodyHost.addChild(alert)
            }
        )
    }

    private fun mountResolvedNodes(loadedNodes: List<SchemaNode>) {
        bodyHost.clearChildren()
        val childSchema = ObjectSchema(
            id = "${asyncSpec.key}_async",
            title = "",
            description = null,
            nodes = loadedNodes
        )
        val form = SchemaForm(
            schema = childSchema,
            layoutMode = SchemaLayoutMode.STACKED,
            labelTranslator = labelTranslator,
            customRenderers = customRenderers,
            parentContextProvider = { evalContextProvider() }
        )
        if (hasPendingData) {
            form.setData(pendingData ?: emptyMap(), markAsSaved = pendingMarkAsSaved)
            if (!pendingMarkAsSaved && pendingSavedBaseline.isNotEmpty()) {
                form.updateSavedBaseline(pendingSavedBaseline)
            }
        } else {
            form.save()
        }
        val effectiveEnabled = pendingEnabledOverride ?: isEnabled
        if (effectiveEnabled) {
            form.enableAll()
        } else {
            form.disableAll()
        }
        form.onChange { subKey, subVal ->
            val currentMap = form.getData()
            for (listener in changeListeners.toList()) {
                listener(currentMap)
            }
            onAnyChangedCallback?.invoke("${asyncSpec.key}.$subKey", subVal)
        }
        childForm = form
        bodyHost.addChild(form)
        asyncSpec.onMounted?.invoke(form)
        onAsyncResolvedCallback()
    }

    override var value: Any?
        get() = childForm?.getData() ?: pendingData ?: linkedMapOf<String, Any?>()
        set(newVal) {
            val map = LinkedHashMap(SchemaValueCodec.coerceToMap(newVal))
            val form = childForm
            if (form != null) {
                form.setData(map, markAsSaved = false)
            } else {
                pendingData = map
                hasPendingData = true
                pendingMarkAsSaved = false
            }
        }

    override var savedValue: Any?
        get() = childForm?.getSavedData() ?: pendingSavedBaseline
        set(newVal) {
            val map = LinkedHashMap(SchemaValueCodec.coerceToMap(newVal))
            val form = childForm
            if (form != null) {
                form.updateSavedBaseline(map)
            } else {
                pendingSavedBaseline = map
            }
        }

    override val isChanged: Boolean
        get() = childForm?.isChanged ?: (hasPendingData && !pendingMarkAsSaved && pendingData != pendingSavedBaseline)

    override val stringValue: String
        get() = SchemaJsonCodec.toJson(value)

    override fun setDataValue(value: Any?, markAsSaved: Boolean) {
        val map = LinkedHashMap(SchemaValueCodec.coerceToMap(value))
        val form = childForm
        if (form != null) {
            form.setData(map, markAsSaved = markAsSaved)
        } else {
            pendingData = map
            hasPendingData = true
            pendingMarkAsSaved = markAsSaved
            if (markAsSaved) {
                pendingSavedBaseline = LinkedHashMap(map)
            }
        }
    }

    override fun setValueAndSaved(value: Any?) {
        setDataValue(value, markAsSaved = true)
    }

    override fun save() {
        val form = childForm
        if (form != null) {
            form.save()
        } else if (hasPendingData) {
            pendingSavedBaseline = LinkedHashMap(pendingData ?: emptyMap())
            pendingMarkAsSaved = true
        }
    }

    override fun reset() {
        val form = childForm
        if (form != null) {
            form.reset()
        } else {
            pendingData = LinkedHashMap(pendingSavedBaseline)
            hasPendingData = pendingSavedBaseline.isNotEmpty()
            pendingMarkAsSaved = true
        }
    }

    override fun resetToDefault() {
        val form = childForm
        if (form != null) {
            form.resetToDefaults()
        } else {
            pendingData = linkedMapOf()
            hasPendingData = false
            pendingMarkAsSaved = false
        }
    }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        pendingEnabledOverride = enabled
        val form = childForm ?: return
        if (enabled) form.enableAll() else form.disableAll()
    }

    override fun validate(context: SchemaEvalContext): Boolean {
        if (!isVisible) return true
        if (loadError != null) return false
        return childForm?.validate() ?: true
    }

    override fun refreshOptions(context: SchemaEvalContext) {
        childForm?.refreshDynamicOptions()
    }

    override fun reconcileNestedContext(parentContext: SchemaEvalContext) {
        childForm?.reconcileConditionsAndRules(refreshParentDependentSelects = true)
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
        childForm?.findField(path)

    override fun getModifiedDataValue(): Pair<Boolean, Any?> {
        val form = childForm ?: return false to null
        val modified = form.getModifiedData()
        return if (modified.isNotEmpty()) true to modified else false to null
    }

    override fun getModifiedStringDataValue(): Pair<Boolean, String> {
        val form = childForm ?: return false to ""
        val modified = form.getModifiedData()
        return if (modified.isNotEmpty()) true to SchemaJsonCodec.toJson(modified) else false to ""
    }
}
