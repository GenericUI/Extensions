package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.layout.Column
import com.nexustools.genericui.component.layout.ColumnLayout
import com.nexustools.genericui.component.layout.ColumnSpec
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.Disposable
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.SpacingScale

/**
 * Schema-driven GenericUI form engine supporting stacked, responsive 12-column grid,
 * compact inline, and tabbed layouts, live non-rebuilding `visibleWhen`/`enabledWhen`
 * reconciliation, single deduplicated cross-field reactive rule subscriptions, async
 * option/group loading, dirty tracking, validation, and data-class binding.
 */
open class SchemaForm(
    val schema: ObjectSchema,
    val layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
    initialData: Map<String, Any?>? = null,
    val labelTranslator: ((String) -> String)? = null,
    private val customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
    private val parentContextProvider: (() -> SchemaEvalContext?)? = null,
    showHeader: Boolean = false
) : VStack(SpacingScale.S3) {

    private data class IndexedRule(
        val index: Int,
        val rule: SchemaReactiveRule
    )

    val bannerAlert: Alert = Alert(
        message = "",
        type = BootstrapType.DANGER,
        dismissible = false
    ).apply {
        isVisible = false
    }

    var customConstraintHandler: ((targetPath: String, customKey: String?, value: Any?) -> Unit)? = null
    var tabContainer: TabContainer? = null
        private set

    private val controllersByKey = LinkedHashMap<String, SchemaFieldController>()
    private val orderedControllers = mutableListOf<SchemaFieldController>()
    private val changeListeners = mutableListOf<(String, Any?) -> Unit>()

    private val globalRules = mutableListOf<IndexedRule>()
    private val rulesBySourceRoot = LinkedHashMap<String, MutableList<IndexedRule>>()
    private val selectRefreshBySourceRoot = LinkedHashMap<String, MutableList<SchemaFieldController>>()
    private val dynamicProviderSelects = mutableListOf<SchemaFieldController>()
    private val ruleGenerations = IntArray(schema.reactiveRules.size)

    private val initialDataSnapshot: LinkedHashMap<String, Any?> = LinkedHashMap(initialData ?: emptyMap())

    private var suppressGlobalReconcile: Boolean = false
    private var reconcileDepth: Int = 0
    private var allEnabled: Boolean = true

    init {
        addClass("gui-schema-form")
        addChild(bannerAlert)

        if (showHeader) {
            if (schema.title.isNotBlank()) {
                addChild(Heading(schema.title, level = 4))
            }
            if (!schema.description.isNullOrBlank()) {
                addChild(Text(schema.description).apply {
                    addClass("form-text")
                    textColorType = BootstrapType.SECONDARY
                })
            }
        }

        mountAllNodes()
        indexReactiveDependencies()

        if (initialData != null) {
            setData(initialData, markAsSaved = true)
        } else {
            reconcileConditionsAndRules(
                evaluateAllRules = true,
                refreshAllDynamicSelects = true
            )
        }
    }

    fun setBannerMessage(message: String, type: BootstrapType = BootstrapType.INFO) {
        bannerAlert.messageText.text = message
        bannerAlert.type = type
        bannerAlert.isVisible = message.isNotBlank()
    }

    fun setError(message: String) {
        setBannerMessage(message, BootstrapType.DANGER)
    }

    fun hideBannerMessage() {
        bannerAlert.messageText.text = ""
        bannerAlert.isVisible = false
    }

    fun getData(): LinkedHashMap<String, Any?> {
        val result = LinkedHashMap<String, Any?>(orderedControllers.size)
        for (ctrl in orderedControllers) {
            if (ctrl.spec is SchemaSeparatorSpec) continue
            result[ctrl.key] = ctrl.value
        }
        return result
    }

    fun getStringData(): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>(orderedControllers.size)
        for (ctrl in orderedControllers) {
            if (ctrl.spec is SchemaSeparatorSpec) continue
            result[ctrl.key] = ctrl.stringValue
        }
        return result
    }

    fun getModifiedData(): LinkedHashMap<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        for (ctrl in orderedControllers) {
            if (ctrl.spec is SchemaSeparatorSpec) continue
            val (changed, modifiedVal) = ctrl.getModifiedDataValue()
            if (changed) {
                result[ctrl.key] = modifiedVal
            }
        }
        return result
    }

    fun getModifiedStringData(): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        for (ctrl in orderedControllers) {
            if (ctrl.spec is SchemaSeparatorSpec) continue
            val (changed, modifiedStr) = ctrl.getModifiedStringDataValue()
            if (changed) {
                result[ctrl.key] = modifiedStr
            }
        }
        return result
    }

    fun getSavedData(): LinkedHashMap<String, Any?> {
        val result = LinkedHashMap<String, Any?>(orderedControllers.size)
        for (ctrl in orderedControllers) {
            if (ctrl.spec is SchemaSeparatorSpec) continue
            result[ctrl.key] = ctrl.savedValue
        }
        return result
    }

    val isChanged: Boolean
        get() = orderedControllers.any { it.spec !is SchemaSeparatorSpec && it.isChanged }

    fun setData(data: Map<String, Any?>, markAsSaved: Boolean = true) {
        suppressGlobalReconcile = true
        try {
            for (node in schema.nodes) {
                if (node is SchemaSeparatorSpec) continue
                val ctrl = controllersByKey[node.key] ?: continue
                val (found, raw) = SchemaValueCodec.resolveRawValue(data, node)
                val targetValue = when {
                    found -> raw
                    node is SchemaFieldSpec -> SchemaValueCodec.defaultFieldValue(node)
                    node is SchemaGroupSpec -> SchemaValueCodec.coerceNodesValues(node.children, emptyMap())
                    node is SchemaAsyncGroupSpec -> linkedMapOf<String, Any?>()
                    node is SchemaIndexedGroupSpec -> SchemaValueCodec.coerceIndexedGroupValue(node, null)
                    else -> null
                }
                if (found || markAsSaved) {
                    ctrl.setDataValue(targetValue, markAsSaved = markAsSaved)
                }
            }
        } finally {
            suppressGlobalReconcile = false
        }
        reconcileConditionsAndRules(
            evaluateAllRules = true,
            refreshAllDynamicSelects = true
        )
    }

    fun updateValues(partial: Map<String, Any?>) {
        suppressGlobalReconcile = true
        try {
            val handledTopLevel = HashSet<String>()
            for (node in schema.nodes) {
                if (node is SchemaSeparatorSpec) continue
                val ctrl = controllersByKey[node.key] ?: continue
                val (found, raw) = SchemaValueCodec.resolveRawValue(partial, node)
                if (found) {
                    ctrl.setDataValue(raw, markAsSaved = false)
                    handledTopLevel.add(node.key)
                }
            }
            for ((pathKey, pathVal) in partial) {
                if (pathKey in handledTopLevel) continue
                if ('.' in pathKey || '[' in pathKey) {
                    findField(pathKey)?.setDataValue(pathVal, markAsSaved = false)
                }
            }
        } finally {
            suppressGlobalReconcile = false
        }
        reconcileConditionsAndRules(
            evaluateAllRules = true,
            refreshAllDynamicSelects = true
        )
    }

    internal fun updateSavedBaseline(saved: Map<String, Any?>) {
        for (node in schema.nodes) {
            if (node is SchemaSeparatorSpec) continue
            val ctrl = controllersByKey[node.key] ?: continue
            val (found, raw) = SchemaValueCodec.resolveRawValue(saved, node)
            if (found) {
                ctrl.savedValue = when (node) {
                    is SchemaFieldSpec -> SchemaValueCodec.coerceValue(node, raw)
                    is SchemaGroupSpec -> SchemaValueCodec.coerceNodesValues(node.children, SchemaValueCodec.coerceToMap(raw))
                    is SchemaAsyncGroupSpec -> SchemaValueCodec.coerceToMap(raw)
                    is SchemaIndexedGroupSpec -> SchemaValueCodec.coerceIndexedGroupValue(node, raw)
                    is SchemaSeparatorSpec -> null
                }
            }
        }
    }

    fun applyPreset(presetId: String): Boolean {
        val preset = schema.presets.firstOrNull { it.id == presetId } ?: return false
        applyPreset(preset)
        return true
    }

    fun applyPreset(preset: SchemaPreset) {
        updateValues(preset.values)
    }

    fun save() {
        for (ctrl in orderedControllers) {
            ctrl.save()
        }
    }

    fun reset() {
        suppressGlobalReconcile = true
        try {
            for (ctrl in orderedControllers) {
                ctrl.reset()
            }
        } finally {
            suppressGlobalReconcile = false
        }
        hideBannerMessage()
        reconcileConditionsAndRules(
            evaluateAllRules = true,
            refreshAllDynamicSelects = true
        )
    }

    fun resetToDefaults() {
        suppressGlobalReconcile = true
        try {
            for (ctrl in orderedControllers) {
                ctrl.resetToDefault()
            }
        } finally {
            suppressGlobalReconcile = false
        }
        hideBannerMessage()
        reconcileConditionsAndRules(
            evaluateAllRules = true,
            refreshAllDynamicSelects = true
        )
    }

    fun validate(): Boolean = validateDetailed().isValid

    fun validateDetailed(): SchemaValidationResult {
        val context = buildEvalContext()
        val errors = LinkedHashMap<String, MutableList<String>>()
        for (ctrl in orderedControllers) {
            if (!ctrl.isVisible || ctrl.spec is SchemaSeparatorSpec) continue
            when (ctrl) {
                is SchemaGroupController -> {
                    val nestedRes = ctrl.childForm.validateDetailed()
                    for ((subKey, subErrors) in nestedRes.fieldErrors) {
                        errors.getOrPut("${ctrl.key}.$subKey") { mutableListOf() }.addAll(subErrors)
                    }
                }
                is SchemaAsyncGroupController -> {
                    val valid = ctrl.validate(context)
                    if (!valid) {
                        errors.getOrPut(ctrl.key) { mutableListOf() }
                            .add("Invalid or failed async group '${ctrl.key}'.")
                    }
                }
                is SchemaIndexedGroupController -> {
                    val valid = ctrl.validate(context)
                    if (!valid) {
                        errors.getOrPut(ctrl.key) { mutableListOf() }
                            .add("Validation failed in '${ctrl.key}'.")
                    }
                }
                else -> {
                    val valid = ctrl.validate(context)
                    if (!valid) {
                        val msg = ctrl.errorLabel?.text?.takeIf { it.isNotBlank() } ?: "Invalid value."
                        errors.getOrPut(ctrl.key) { mutableListOf() }.add(msg)
                    }
                }
            }
        }
        return SchemaValidationResult(
            isValid = errors.isEmpty(),
            fieldErrors = errors
        )
    }

    fun enableAll() {
        allEnabled = true
        val ctx = buildEvalContext()
        for (ctrl in orderedControllers) {
            ctrl.formEnabled = true
            ctrl.conditionEnabled = ctrl.spec.enabledWhen?.evaluate(ctx) ?: true
            ctrl.syncEffectiveStates()
        }
    }

    fun disableAll() {
        allEnabled = false
        for (ctrl in orderedControllers) {
            ctrl.formEnabled = false
            ctrl.syncEffectiveStates()
        }
    }

    fun findField(path: String): SchemaFieldController? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return null
        controllersByKey[trimmed]?.let { return it }

        val dotIdx = trimmed.indexOf('.')
        val bracketIdx = trimmed.indexOf('[')
        val splitIdx = when {
            dotIdx >= 0 && bracketIdx >= 0 -> minOf(dotIdx, bracketIdx)
            dotIdx >= 0 -> dotIdx
            bracketIdx >= 0 -> bracketIdx
            else -> return null
        }
        val head = trimmed.substring(0, splitIdx)
        val tail = trimmed.substring(splitIdx).removePrefix(".")
        val parentCtrl = controllersByKey[head] ?: return null
        return if (tail.isEmpty()) parentCtrl else parentCtrl.findNestedController(tail)
    }

    fun refreshDynamicOptions() {
        val ctx = buildEvalContext()
        for (ctrl in orderedControllers) {
            ctrl.refreshOptions(ctx)
        }
    }

    fun onChange(listener: (key: String, value: Any?) -> Unit): Disposable {
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

    fun <T : Any> bindDataClass(
        descriptor: SchemaDescriptor<T>,
        instance: T,
        markAsSaved: Boolean = true
    ) {
        setData(descriptor.toValueMap(instance), markAsSaved = markAsSaved)
    }

    inline fun <reified T : Any> bindDataClass(instance: T, markAsSaved: Boolean = true) {
        val descriptor = SchemaRegistry.getForClass<T>()
            ?: throw IllegalStateException("No SchemaDescriptor registered for ${T::class.simpleName}.")
        bindDataClass(descriptor, instance, markAsSaved = markAsSaved)
    }

    fun <T : Any> toDataClass(descriptor: SchemaDescriptor<T>): T =
        descriptor.fromValueMap(getData())

    inline fun <reified T : Any> toDataClass(): T {
        val descriptor = SchemaRegistry.getForClass<T>()
            ?: throw IllegalStateException("No SchemaDescriptor registered for ${T::class.simpleName}.")
        return toDataClass(descriptor)
    }

    internal fun buildEvalContext(): SchemaEvalContext {
        val values = LinkedHashMap<String, Any?>(schema.nodes.size)
        val strings = LinkedHashMap<String, String>(schema.nodes.size)
        for (node in schema.nodes) {
            if (node is SchemaSeparatorSpec) continue
            val ctrl = controllersByKey[node.key]
            if (ctrl != null) {
                values[node.key] = ctrl.value
                strings[node.key] = ctrl.stringValue
            } else {
                val (found, raw) = SchemaValueCodec.resolveRawValue(initialDataSnapshot, node)
                val fallbackVal = when {
                    found -> raw
                    node is SchemaFieldSpec -> SchemaValueCodec.defaultFieldValue(node)
                    node is SchemaGroupSpec -> SchemaValueCodec.coerceNodesValues(node.children, emptyMap())
                    node is SchemaIndexedGroupSpec -> SchemaValueCodec.coerceIndexedGroupValue(node, null)
                    else -> null
                }
                values[node.key] = fallbackVal
                strings[node.key] = SchemaValueCodec.toWireString(node, fallbackVal)
            }
        }
        val parentCtx = parentContextProvider?.invoke()
        return SchemaEvalContext(
            values = values,
            parent = parentCtx,
            index = parentCtx?.index,
            stringValues = strings
        )
    }

    internal fun reconcileConditionsAndRules(
        evaluateAllRules: Boolean = false,
        refreshAllDynamicSelects: Boolean = false,
        refreshParentDependentSelects: Boolean = false
    ) {
        val ctx = buildEvalContext()
        applyConditions(ctx)
        if (evaluateAllRules) {
            val allRuleIndices = LinkedHashSet<Int>(schema.reactiveRules.size)
            for (i in schema.reactiveRules.indices) {
                allRuleIndices.add(i)
            }
            evaluateRulesByIndices(allRuleIndices, ctx)
        }
        if (refreshAllDynamicSelects) {
            refreshDynamicOptions()
        } else if (refreshParentDependentSelects) {
            refreshParentReferencedSelects(ctx)
        }
    }

    private fun mountAllNodes() {
        val sectionIds = schema.sections.map { it.id }.toSet()
        if (layoutMode == SchemaLayoutMode.TABBED) {
            val tabs = TabContainer(fadeTransition = false)
            tabContainer = tabs
            if (schema.sections.isEmpty()) {
                val tabBody = VStack(SpacingScale.S2)
                mountSectionNodes(schema.nodes, tabBody)
                tabs.addTab(
                    title = schema.title.ifBlank { "General" },
                    content = tabBody,
                    id = "__general"
                )
            } else {
                val unsectioned = schema.nodes.filter { it.sectionId == null || it.sectionId !in sectionIds }
                if (unsectioned.isNotEmpty()) {
                    val generalBody = VStack(SpacingScale.S2)
                    mountSectionNodes(unsectioned, generalBody)
                    tabs.addTab(
                        title = "General",
                        content = generalBody,
                        id = "__general"
                    )
                }
                for (section in schema.sections) {
                    val secNodes = schema.nodes.filter { it.sectionId == section.id }
                    val secBody = VStack(SpacingScale.S2)
                    if (!section.description.isNullOrBlank()) {
                        secBody.addChild(Text(section.description).apply {
                            addClass("form-text")
                            textColorType = BootstrapType.SECONDARY
                        })
                    }
                    mountSectionNodes(secNodes, secBody)
                    tabs.addTab(
                        title = section.title,
                        content = secBody,
                        id = section.id,
                        icon = section.icon
                    )
                }
            }
            addChild(tabs)
            return
        }

        if (schema.sections.isEmpty()) {
            mountSectionNodes(schema.nodes, this)
            return
        }

        val unsectioned = schema.nodes.filter { it.sectionId == null || it.sectionId !in sectionIds }
        if (unsectioned.isNotEmpty()) {
            mountSectionNodes(unsectioned, this)
        }
        for (section in schema.sections) {
            val secNodes = schema.nodes.filter { it.sectionId == section.id }
            val secContainer = VStack(SpacingScale.S2).apply {
                addClass("gui-schema-section")
                if (section.title.isNotBlank()) {
                    addChild(Heading(section.title, level = 5))
                }
                if (!section.description.isNullOrBlank()) {
                    addChild(Text(section.description).apply {
                        addClass("form-text")
                        textColorType = BootstrapType.SECONDARY
                    })
                }
            }
            mountSectionNodes(secNodes, secContainer)
            addChild(secContainer)
        }
    }

    private fun mountSectionNodes(nodesForSection: List<SchemaNode>, targetContainer: VStack) {
        when (layoutMode) {
            SchemaLayoutMode.RESPONSIVE_GRID -> {
                val gridHost = Container("genericui:row", "row")
                val gridLayout = ColumnLayout(host = gridHost, gutter = SpacingScale.S3)
                for (node in nodesForSection) {
                    val ctrl = createNodeController(node, compactInline = false)
                    val colSpec = when {
                        node.fullWidth ||
                            node is SchemaSeparatorSpec ||
                            node is SchemaGroupSpec ||
                            node is SchemaAsyncGroupSpec ||
                            node is SchemaIndexedGroupSpec -> ColumnSpec(span = 12, md = 12, lg = 12)
                        node is SchemaFieldSpec && node.colSpan != null -> {
                            val clamped = node.colSpan!!.coerceIn(1, 12)
                            ColumnSpec(span = 12, md = clamped, lg = clamped)
                        }
                        else -> ColumnSpec(span = 12, md = 6, lg = 4)
                    }
                    val col = Column(colSpec)
                    col.addChild(ctrl.rootComponent)
                    ctrl.wrapperComponent = col
                    gridLayout.addColumn(col)
                }
                targetContainer.addChild(gridHost)
            }
            SchemaLayoutMode.COMPACT_INLINE -> {
                var inlineRow: HStack? = null
                fun ensureInlineRow(): HStack {
                    val existing = inlineRow
                    if (existing != null) return existing
                    val created = HStack(SpacingScale.S3).apply {
                        flexWrap = FlexWrap.WRAP
                        alignItems = AlignItems.CENTER
                    }
                    inlineRow = created
                    targetContainer.addChild(created)
                    return created
                }
                for (node in nodesForSection) {
                    if (node.fullWidth ||
                        node is SchemaSeparatorSpec ||
                        node is SchemaGroupSpec ||
                        node is SchemaAsyncGroupSpec ||
                        node is SchemaIndexedGroupSpec
                    ) {
                        inlineRow = null
                        val ctrl = createNodeController(node, compactInline = false)
                        targetContainer.addChild(ctrl.rootComponent)
                    } else {
                        val ctrl = createNodeController(node, compactInline = true)
                        ensureInlineRow().addChild(ctrl.rootComponent)
                    }
                }
            }
            SchemaLayoutMode.STACKED,
            SchemaLayoutMode.TABBED -> {
                for (node in nodesForSection) {
                    val ctrl = createNodeController(node, compactInline = false)
                    targetContainer.addChild(ctrl.rootComponent)
                }
            }
        }
    }

    private fun createNodeController(node: SchemaNode, compactInline: Boolean): SchemaFieldController {
        val resolvedLabel = SchemaLabelUtil.resolveLabel(node.label, node.key, labelTranslator)
        val ctrl = when (node) {
            is SchemaSeparatorSpec -> SchemaSeparatorController(node, resolvedLabel)
            is SchemaGroupSpec -> SchemaGroupController(
                groupSpec = node,
                initialLabel = resolvedLabel,
                labelTranslator = labelTranslator,
                customRenderers = customRenderers,
                evalContextProvider = { buildEvalContext() }
            )
            is SchemaAsyncGroupSpec -> SchemaAsyncGroupController(
                asyncSpec = node,
                initialLabel = resolvedLabel,
                labelTranslator = labelTranslator,
                customRenderers = customRenderers,
                evalContextProvider = { buildEvalContext() },
                onAsyncResolvedCallback = {
                    if (!suppressGlobalReconcile) {
                        reconcileConditionsAndRules(evaluateAllRules = true)
                    }
                }
            )
            is SchemaIndexedGroupSpec -> SchemaIndexedGroupController(
                indexedSpec = node,
                initialLabel = resolvedLabel,
                labelTranslator = labelTranslator,
                customRenderers = customRenderers,
                evalContextProvider = { buildEvalContext() }
            )
            is SchemaFieldSpec -> SchemaNodeControllerFactory.createFieldController(
                spec = node,
                labelText = resolvedLabel,
                compactInline = compactInline,
                hostForm = this,
                labelTranslator = labelTranslator,
                customRenderers = customRenderers,
                evalContextProvider = { buildEvalContext() },
                deferInitialOptionsLoad = true
            )
        }
        ctrl.onAnyChangedCallback = { changedKey, newVal ->
            handleFieldChange(changedKey, newVal)
        }
        controllersByKey[node.key] = ctrl
        orderedControllers.add(ctrl)
        return ctrl
    }

    private fun indexReactiveDependencies() {
        for ((ruleIdx, rule) in schema.reactiveRules.withIndex()) {
            val indexed = IndexedRule(ruleIdx, rule)
            if (rule.sourcePaths.isEmpty()) {
                globalRules.add(indexed)
            } else {
                val seenRoots = LinkedHashSet<String>()
                for (srcPath in rule.sourcePaths) {
                    val rootKey = normalizeTopLevelKey(srcPath)
                    if (rootKey.isNotEmpty() && seenRoots.add(rootKey)) {
                        rulesBySourceRoot.getOrPut(rootKey) { mutableListOf() }.add(indexed)
                    }
                }
            }
        }

        for (ctrl in orderedControllers) {
            val refs = when (val spec = ctrl.spec) {
                is SelectFieldSpec -> spec.queryFilterRefs.values
                is OrderedMultiSelectFieldSpec -> spec.queryFilterRefs.values
                else -> emptyList()
            }
            val seenRoots = LinkedHashSet<String>()
            for (ref in refs) {
                if (ref is SchemaFilterValue.FieldRef) {
                    val rootKey = normalizeTopLevelKey(ref.fieldPath)
                    if (rootKey.isNotEmpty() && seenRoots.add(rootKey)) {
                        selectRefreshBySourceRoot.getOrPut(rootKey) { mutableListOf() }.add(ctrl)
                    }
                }
            }
            val hasDynamicSyncProvider = when (val spec = ctrl.spec) {
                is SelectFieldSpec -> spec.optionsProvider != null && spec.queryFilterRefs.isEmpty()
                is OrderedMultiSelectFieldSpec -> spec.optionsProvider != null && spec.queryFilterRefs.isEmpty()
                else -> false
            }
            if (hasDynamicSyncProvider) {
                dynamicProviderSelects.add(ctrl)
            }
        }
    }

    private fun handleFieldChange(changedKey: String, newValue: Any?) {
        for (listener in changeListeners.toList()) {
            listener(changedKey, newValue)
        }
        if (suppressGlobalReconcile) return
        if (reconcileDepth >= 8) return

        reconcileDepth++
        try {
            val changedRoot = normalizeTopLevelKey(changedKey)
            val ctx = buildEvalContext()
            applyConditions(ctx)

            val triggeredRuleIndices = LinkedHashSet<Int>()
            rulesBySourceRoot[changedRoot]?.let { list ->
                for (item in list) {
                    triggeredRuleIndices.add(item.index)
                }
            }
            for (item in globalRules) {
                triggeredRuleIndices.add(item.index)
            }
            if (triggeredRuleIndices.isNotEmpty()) {
                evaluateRulesByIndices(triggeredRuleIndices, ctx)
            }

            val refreshedSelectKeys = LinkedHashSet<String>()
            selectRefreshBySourceRoot[changedRoot]?.let { dependents ->
                for (depCtrl in dependents) {
                    if (refreshedSelectKeys.add(depCtrl.key)) {
                        depCtrl.refreshOptions(ctx)
                    }
                }
            }
            for (dynCtrl in dynamicProviderSelects) {
                if (dynCtrl.key != changedRoot && refreshedSelectKeys.add(dynCtrl.key)) {
                    dynCtrl.refreshOptions(ctx)
                }
            }
        } finally {
            reconcileDepth--
        }
    }

    private fun applyConditions(ctx: SchemaEvalContext) {
        for (ctrl in orderedControllers) {
            ctrl.formEnabled = allEnabled
            ctrl.conditionVisible = ctrl.spec.visibleWhen?.evaluate(ctx) ?: true
            ctrl.conditionEnabled = ctrl.spec.enabledWhen?.evaluate(ctx) ?: true
            ctrl.syncEffectiveStates()
            ctrl.reconcileNestedContext(ctx)
        }
    }

    private fun evaluateRulesByIndices(indices: Collection<Int>, ctx: SchemaEvalContext) {
        for (idx in indices) {
            val rule = schema.reactiveRules[idx]
            val gen = ++ruleGenerations[idx]
            val syncEval = rule.syncEvaluator
            if (syncEval != null) {
                val constraintVal = syncEval.invoke(ctx)
                applyRuleConstraint(rule, constraintVal)
            } else {
                val asyncEval = rule.asyncEvaluator ?: continue
                asyncEval.invoke(ctx).onComplete(
                    onSuccess = { constraintVal ->
                        if (gen == ruleGenerations[idx]) {
                            applyRuleConstraint(rule, constraintVal)
                        }
                    },
                    onFailure = { /* Keep previous valid state on failed reactive async lookup */ }
                )
            }
        }
    }

    private fun applyRuleConstraint(rule: SchemaReactiveRule, constraintValue: Any?) {
        if (rule.targetConstraint == SchemaConstraintTarget.CUSTOM) {
            customConstraintHandler?.invoke(rule.targetPath, rule.customKey, constraintValue)
            findField(rule.targetPath)?.applyConstraint(
                SchemaConstraintTarget.CUSTOM,
                rule.customKey to constraintValue
            )
            return
        }
        findField(rule.targetPath)?.applyConstraint(rule.targetConstraint, constraintValue)
    }

    private fun refreshParentReferencedSelects(ctx: SchemaEvalContext) {
        for (ctrl in orderedControllers) {
            val hasParentRef = when (val spec = ctrl.spec) {
                is SelectFieldSpec -> spec.queryFilterRefs.values.any {
                    it is SchemaFilterValue.FieldRef && it.fieldPath.startsWith(".")
                }
                is OrderedMultiSelectFieldSpec -> spec.queryFilterRefs.values.any {
                    it is SchemaFilterValue.FieldRef && it.fieldPath.startsWith(".")
                }
                else -> false
            }
            if (hasParentRef) {
                ctrl.refreshOptions(ctx)
            }
        }
    }

    private fun normalizeTopLevelKey(path: String): String {
        val trimmed = path.trim().trimStart('.')
        if (trimmed.isEmpty()) return ""
        val dotIdx = trimmed.indexOf('.')
        val bracketIdx = trimmed.indexOf('[')
        val endIdx = when {
            dotIdx >= 0 && bracketIdx >= 0 -> minOf(dotIdx, bracketIdx)
            dotIdx >= 0 -> dotIdx
            bracketIdx >= 0 -> bracketIdx
            else -> trimmed.length
        }
        return trimmed.substring(0, endIdx)
    }
}
