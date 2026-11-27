package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.form.ComboBox
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.SelectOption
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.enums.bootstrap.BootstrapType

internal class SelectSchemaFieldController(
    private val selectSpec: SelectFieldSpec,
    initialLabel: String,
    compactInline: Boolean,
    initialContext: SchemaEvalContext,
    deferInitialOptionsLoad: Boolean = false
) : SchemaBaseFieldController(selectSpec, initialLabel, compactInline) {

    private var currentVal: String? = selectSpec.defaultValue
    override var savedValue: Any? = selectSpec.defaultValue

    private var dynamicOptionsOverride: List<SchemaOption>? = null
    private var dynamicFilterOverride: Map<String, String>? = null
    private var lastEvalContext: SchemaEvalContext = initialContext
    private var loadGeneration: Long = 0L
    private var isLoadingOptions: Boolean = false
    private var isSuspended: Boolean = false
    private var resolvedOptions: List<SchemaOption> = emptyList()
    private var lastRawLabelText: String = initialLabel

    private val statusBadge = Badge("", type = BootstrapType.INFO, pill = true).apply {
        isVisible = false
    }

    internal val selectWidget: Select = if (selectSpec.searchable) {
        ComboBox(allowCustomInput = selectSpec.allowUnlistedSavedValue || selectSpec.searchable)
    } else {
        Select()
    }

    init {
        labelHeaderContainer.addChild(statusBadge)
        selectWidget.onSelectionChanged = { selected ->
            if (!suppressUiSync && currentVal != selected) {
                currentVal = selected
                notifyValueChanged(selected)
            }
        }
        bindLabelTarget(selectWidget)
        mountStandardBody(selectWidget, compactInline, selectWidget)
        if (!deferInitialOptionsLoad) {
            refreshOptions(initialContext)
        }
    }

    override var value: Any?
        get() = currentVal
        set(newVal) {
            val next = newVal?.toString() ?: selectSpec.defaultValue
            val changed = currentVal != next
            currentVal = next
            syncSelectionToWidget()
            if (changed) notifyValueChanged(currentVal)
        }

    override fun applyEnabledState(enabled: Boolean) {
        super.applyEnabledState(enabled)
        selectWidget.isEnabled = enabled
    }

    override fun refreshOptions(context: SchemaEvalContext) {
        lastEvalContext = context
        val generation = ++loadGeneration

        val overrideOpts = dynamicOptionsOverride
        if (overrideOpts != null) {
            isLoadingOptions = false
            isSuspended = false
            updateStatusBadge(visible = false)
            applyResolvedOptions(overrideOpts, context)
            return
        }

        val asyncLoader = selectSpec.asyncOptionsLoader
        if (asyncLoader != null) {
            val filters = SchemaOptionResolver.resolveQueryFilters(
                selectSpec.queryFilterRefs,
                dynamicFilterOverride,
                context
            )
            if (filters == null) {
                isLoadingOptions = false
                isSuspended = true
                updateStatusBadge(visible = false)
                applyResolvedOptions(emptyList(), context)
                return
            }

            isSuspended = false
            isLoadingOptions = true
            updateStatusBadge(visible = true, text = "Loading...", type = BootstrapType.INFO)
            if (selectSpec.staticOptions.isNotEmpty() || resolvedOptions.isEmpty()) {
                applyResolvedOptions(selectSpec.staticOptions, context)
            }

            val asyncRes = asyncLoader.invoke(context, filters)
            asyncRes.onComplete(
                onSuccess = { loaded ->
                    if (generation == loadGeneration) {
                        isLoadingOptions = false
                        updateStatusBadge(visible = false)
                        setValidationErrors(emptyList())
                        applyResolvedOptions(loaded, context)
                    }
                },
                onFailure = { error ->
                    if (generation == loadGeneration) {
                        isLoadingOptions = false
                        updateStatusBadge(visible = true, text = "Error", type = BootstrapType.DANGER)
                        setValidationErrors(listOf(error.message ?: "Failed to load options."))
                    }
                }
            )
            return
        }

        isLoadingOptions = false
        isSuspended = false
        updateStatusBadge(visible = false)
        val raw = selectSpec.optionsProvider?.invoke(context) ?: selectSpec.staticOptions
        applyResolvedOptions(raw, context)
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
            SchemaConstraintTarget.LABEL -> {
                lastRawLabelText = constraintValue?.toString().orEmpty()
                syncHeaderVisibility()
            }
            else -> Unit
        }
    }

    private fun updateStatusBadge(
        visible: Boolean,
        text: String = "",
        type: BootstrapType = BootstrapType.INFO
    ) {
        statusBadge.text = text
        statusBadge.type = type
        statusBadge.isVisible = visible
        syncHeaderVisibility()
    }

    private fun syncHeaderVisibility() {
        labelHeaderContainer.isVisible = labelComponent.text.isNotBlank() || statusBadge.isVisible
    }

    private fun applyResolvedOptions(raw: List<SchemaOption>, context: SchemaEvalContext) {
        resolvedOptions = SchemaOptionResolver.transformAndPrepend(
            rawOptions = raw,
            prependNullOption = selectSpec.prependNullOption,
            transformer = selectSpec.optionTransformer,
            context = context
        )
        syncSelectionToWidget()
    }

    private fun syncSelectionToWidget() {
        val displayOptions = resolvedOptions.map { SelectOption(it.value, it.label) }.toMutableList()
        val preservedVal = currentVal
        if (preservedVal != null && displayOptions.none { it.value == preservedVal }) {
            if (selectSpec.allowUnlistedSavedValue || isLoadingOptions || isSuspended) {
                displayOptions.add(0, SelectOption(preservedVal, preservedVal))
            }
        }
        suppressUiSync = true
        try {
            selectWidget.setOptions(displayOptions)
            val targetSelection = when {
                preservedVal != null && displayOptions.any { it.value == preservedVal } -> preservedVal
                preservedVal == null && selectSpec.prependNullOption != null -> selectSpec.prependNullOption.value
                else -> displayOptions.firstOrNull()?.value
            }
            if (targetSelection != null) {
                selectWidget.selectedValue = targetSelection
            }
        } finally {
            suppressUiSync = false
        }
    }
}
