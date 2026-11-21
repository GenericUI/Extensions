package com.nexustools.genericui.ext.schema.dsl

import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.ext.dsl.GenericUiDslMarker
import com.nexustools.genericui.ext.schema.BooleanFieldSpec
import com.nexustools.genericui.ext.schema.ColorFieldSpec
import com.nexustools.genericui.ext.schema.CompoundNumberFieldSpec
import com.nexustools.genericui.ext.schema.CubicBezierFieldSpec
import com.nexustools.genericui.ext.schema.CustomFieldSpec
import com.nexustools.genericui.ext.schema.DoubleFieldSpec
import com.nexustools.genericui.ext.schema.IntFieldSpec
import com.nexustools.genericui.ext.schema.ObjectListFieldSpec
import com.nexustools.genericui.ext.schema.ObjectSchema
import com.nexustools.genericui.ext.schema.OrderedMultiSelectFieldSpec
import com.nexustools.genericui.ext.schema.PolymorphicRowListFieldSpec
import com.nexustools.genericui.ext.schema.SchemaAsyncGroupSpec
import com.nexustools.genericui.ext.schema.SchemaAsyncResult
import com.nexustools.genericui.ext.schema.SchemaCondition
import com.nexustools.genericui.ext.schema.SchemaConstraintTarget
import com.nexustools.genericui.ext.schema.SchemaCustomFieldRenderer
import com.nexustools.genericui.ext.schema.SchemaEvalContext
import com.nexustools.genericui.ext.schema.SchemaFilterValue
import com.nexustools.genericui.ext.schema.SchemaForm
import com.nexustools.genericui.ext.schema.SchemaGroupSpec
import com.nexustools.genericui.ext.schema.SchemaIndexedGroupSpec
import com.nexustools.genericui.ext.schema.SchemaNode
import com.nexustools.genericui.ext.schema.SchemaOption
import com.nexustools.genericui.ext.schema.SchemaReactiveRule
import com.nexustools.genericui.ext.schema.SchemaRowVariantSpec
import com.nexustools.genericui.ext.schema.SchemaSeparatorSpec
import com.nexustools.genericui.ext.schema.SchemaValidator
import com.nexustools.genericui.ext.schema.SchemaWidgetHint
import com.nexustools.genericui.ext.schema.SelectFieldSpec
import com.nexustools.genericui.ext.schema.StringFieldSpec
import kotlinx.coroutines.CoroutineScope

/**
 * Shared DSL scope providing builders for all 12 schema field specifications, structural groups,
 * separators, and cross-field reactive rules.
 */
@GenericUiDslMarker
open class SchemaBuilderNodeScope {
    protected val collectedNodes = mutableListOf<SchemaNode>()
    protected val collectedRules = mutableListOf<SchemaReactiveRule>()

    protected open fun addNode(node: SchemaNode) {
        collectedNodes.add(node)
    }

    internal fun buildNodes(): List<SchemaNode> = collectedNodes.toList()

    fun booleanField(
        key: String,
        label: String = "",
        description: String? = null,
        defaultValue: Boolean = false,
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.SWITCH,
        falseLabel: String = "No",
        trueLabel: String = "Yes",
        wireZeroOneStrings: Boolean = (widgetHint == SchemaWidgetHint.SELECT_ZERO_ONE),
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = false,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        colSpan: Int? = null
    ) {
        addNode(
            BooleanFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = defaultValue,
                widgetHint = widgetHint,
                falseLabel = falseLabel,
                trueLabel = trueLabel,
                wireZeroOneStrings = wireZeroOneStrings,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                colSpan = colSpan
            )
        )
    }

    fun intField(
        key: String,
        label: String = "",
        description: String? = null,
        defaultValue: Int = 0,
        min: Int? = null,
        max: Int? = null,
        step: Int = 1,
        unit: String? = null,
        quickChips: List<Int> = emptyList(),
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = false,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        colSpan: Int? = null
    ) {
        addNode(
            IntFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = defaultValue,
                min = min,
                max = max,
                step = step,
                unit = unit,
                quickChips = quickChips,
                isLong = false,
                defaultLongValue = defaultValue.toLong(),
                widgetHint = widgetHint,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                colSpan = colSpan
            )
        )
    }

    fun longField(
        key: String,
        label: String = "",
        description: String? = null,
        defaultValue: Long = 0L,
        min: Int? = null,
        max: Int? = null,
        step: Int = 1,
        unit: String? = null,
        quickChips: List<Int> = emptyList(),
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.NUMBER_FIELD,
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = false,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        colSpan: Int? = null
    ) {
        addNode(
            IntFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = defaultValue.toInt(),
                min = min,
                max = max,
                step = step,
                unit = unit,
                quickChips = quickChips,
                isLong = true,
                defaultLongValue = defaultValue,
                widgetHint = widgetHint,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                colSpan = colSpan
            )
        )
    }

    fun doubleField(
        key: String,
        label: String = "",
        description: String? = null,
        defaultValue: Double = 0.0,
        min: Double? = null,
        max: Double? = null,
        step: Double = 0.1,
        precision: Int? = null,
        unit: String? = null,
        quickChips: List<Double> = emptyList(),
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = false,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        colSpan: Int? = null
    ) {
        addNode(
            DoubleFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = defaultValue,
                min = min,
                max = max,
                step = step,
                precision = precision,
                unit = unit,
                quickChips = quickChips,
                isFloat = false,
                widgetHint = widgetHint,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                colSpan = colSpan
            )
        )
    }

    fun floatField(
        key: String,
        label: String = "",
        description: String? = null,
        defaultValue: Float = 0f,
        min: Float? = null,
        max: Float? = null,
        step: Double = 0.1,
        precision: Int? = null,
        unit: String? = null,
        quickChips: List<Float> = emptyList(),
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = false,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        colSpan: Int? = null
    ) {
        addNode(
            DoubleFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = defaultValue.toDouble(),
                min = min?.toDouble(),
                max = max?.toDouble(),
                step = step,
                precision = precision,
                unit = unit,
                quickChips = quickChips.map { it.toDouble() },
                isFloat = true,
                widgetHint = widgetHint,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                colSpan = colSpan
            )
        )
    }

    fun compoundNumberField(
        key: String,
        label: String = "",
        description: String? = null,
        unitLabels: List<String>,
        radices: List<Double> = emptyList(),
        radixStep: Double = 10.0,
        tupleMode: Boolean = false,
        defaultValue: Any? = if (tupleMode) List(unitLabels.size) { 0.0 } else 0.0,
        min: Double? = null,
        max: Double? = null,
        precision: Int? = null,
        slotSteps: List<Double> = emptyList(),
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = false,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
        colSpan: Int? = null
    ) {
        addNode(
            CompoundNumberFieldSpec(
                key = key,
                label = label,
                description = description,
                unitLabels = unitLabels,
                radices = radices,
                radixStep = radixStep,
                tupleMode = tupleMode,
                defaultValue = defaultValue,
                min = min,
                max = max,
                precision = precision,
                slotSteps = slotSteps,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                widgetHint = widgetHint,
                colSpan = colSpan
            )
        )
    }

    fun stringField(
        key: String,
        label: String = "",
        description: String? = null,
        defaultValue: String = "",
        placeholder: String? = null,
        maxLength: Int? = null,
        multiline: Boolean = false,
        rows: Int = 4,
        secret: Boolean = false,
        suggestions: List<String> = emptyList(),
        suggestionsProvider: ((SchemaEvalContext) -> List<String>)? = null,
        fileFilters: List<FileTypeFilter> = emptyList(),
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
        sectionId: String? = null,
        fullWidth: Boolean = false,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        colSpan: Int? = null
    ) {
        addNode(
            StringFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = defaultValue,
                placeholder = placeholder,
                maxLength = maxLength,
                multiline = multiline,
                rows = rows,
                secret = secret,
                suggestions = suggestions,
                suggestionsProvider = suggestionsProvider,
                fileFilters = fileFilters,
                widgetHint = widgetHint,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                colSpan = colSpan
            )
        )
    }

    fun colorField(
        key: String,
        label: String = "",
        description: String? = null,
        defaultValue: String = "#000000",
        allowAlpha: Boolean = true,
        quickChips: List<String> = emptyList(),
        returnRgbaColorValue: Boolean = false,
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = false,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.COLOR,
        colSpan: Int? = null
    ) {
        addNode(
            ColorFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = defaultValue,
                allowAlpha = allowAlpha,
                quickChips = quickChips,
                returnRgbaColorValue = returnRgbaColorValue,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                widgetHint = widgetHint,
                colSpan = colSpan
            )
        )
    }

    fun selectField(
        key: String,
        label: String = "",
        description: String? = null,
        defaultValue: String? = null,
        staticOptions: List<SchemaOption> = emptyList(),
        optionsProvider: ((SchemaEvalContext) -> List<SchemaOption>)? = null,
        asyncOptionsLoader: ((SchemaEvalContext, Map<String, String>?) -> SchemaAsyncResult<List<SchemaOption>>)? = null,
        optionTransformer: ((List<SchemaOption>, SchemaEvalContext) -> List<SchemaOption>)? = null,
        searchable: Boolean = false,
        allowUnlistedSavedValue: Boolean = false,
        prependNullOption: SchemaOption? = null,
        queryFilterRefs: Map<String, SchemaFilterValue> = emptyMap(),
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = false,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
        colSpan: Int? = null
    ) {
        addNode(
            SelectFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = defaultValue,
                staticOptions = staticOptions,
                optionsProvider = optionsProvider,
                asyncOptionsLoader = asyncOptionsLoader,
                optionTransformer = optionTransformer,
                searchable = searchable,
                allowUnlistedSavedValue = allowUnlistedSavedValue,
                prependNullOption = prependNullOption,
                queryFilterRefs = queryFilterRefs,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                widgetHint = widgetHint,
                colSpan = colSpan
            )
        )
    }

    fun orderedMultiSelectField(
        key: String,
        label: String = "",
        description: String? = null,
        defaultValue: List<String> = emptyList(),
        staticOptions: List<SchemaOption> = emptyList(),
        optionsProvider: ((SchemaEvalContext) -> List<SchemaOption>)? = null,
        asyncOptionsLoader: ((SchemaEvalContext, Map<String, String>?) -> SchemaAsyncResult<List<SchemaOption>>)? = null,
        optionTransformer: ((List<SchemaOption>, SchemaEvalContext) -> List<SchemaOption>)? = null,
        searchable: Boolean = false,
        allowDuplicates: Boolean = false,
        allowDeletion: Boolean = true,
        maxItems: Int? = null,
        serializeAsJsonString: Boolean = false,
        queryFilterRefs: Map<String, SchemaFilterValue> = emptyMap(),
        itemSeparatorProvider: ((oneBasedIndex: Int) -> String?)? = null,
        addButtonText: String = "Add",
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = true,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
        colSpan: Int? = null
    ) {
        addNode(
            OrderedMultiSelectFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = defaultValue,
                staticOptions = staticOptions,
                optionsProvider = optionsProvider,
                asyncOptionsLoader = asyncOptionsLoader,
                optionTransformer = optionTransformer,
                searchable = searchable,
                allowDuplicates = allowDuplicates,
                allowDeletion = allowDeletion,
                maxItems = maxItems,
                serializeAsJsonString = serializeAsJsonString,
                queryFilterRefs = queryFilterRefs,
                itemSeparatorProvider = itemSeparatorProvider,
                addButtonText = addButtonText,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                widgetHint = widgetHint,
                colSpan = colSpan
            )
        )
    }

    fun polymorphicRowListField(
        key: String,
        label: String = "",
        description: String? = null,
        variants: List<SchemaRowVariantSpec> = emptyList(),
        defaultValue: List<List<String?>> = emptyList(),
        serializeAsJsonString: Boolean = false,
        includeVariantIdInRow: Boolean? = null,
        allowReordering: Boolean = false,
        allowDeletion: Boolean = true,
        maxRows: Int? = null,
        addButtonText: String = "Add",
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = true,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
        colSpan: Int? = null,
        variantsBlock: (SchemaRowVariantsScope.() -> Unit)? = null
    ) {
        val resolvedVariants = if (variantsBlock != null) {
            variants + SchemaRowVariantsScope().apply(variantsBlock).buildVariants()
        } else {
            variants
        }
        addNode(
            PolymorphicRowListFieldSpec(
                key = key,
                label = label,
                description = description,
                variants = resolvedVariants,
                defaultValue = defaultValue,
                serializeAsJsonString = serializeAsJsonString,
                includeVariantIdInRow = includeVariantIdInRow ?: (resolvedVariants.size > 1),
                allowReordering = allowReordering,
                allowDeletion = allowDeletion,
                maxRows = maxRows,
                addButtonText = addButtonText,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                widgetHint = widgetHint,
                colSpan = colSpan
            )
        )
    }

    fun cubicBezierField(
        key: String,
        label: String = "",
        description: String? = null,
        defaultValue: List<Float> = listOf(0.25f, 0.1f, 0.25f, 1.0f),
        previewFactory: ((values: List<Float>, onUpdate: (List<Float>) -> Unit) -> AbstractComponent)? = null,
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = true,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
        colSpan: Int? = null
    ) {
        addNode(
            CubicBezierFieldSpec(
                key = key,
                label = label,
                description = description,
                defaultValue = defaultValue,
                previewFactory = previewFactory,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                widgetHint = widgetHint,
                colSpan = colSpan
            )
        )
    }

    fun objectListField(
        key: String,
        label: String = "",
        description: String? = null,
        itemSchema: ObjectSchema? = null,
        defaultValue: List<Map<String, Any?>> = emptyList(),
        addButtonText: String = "Add Item",
        allowReordering: Boolean = true,
        allowDeletion: Boolean = true,
        maxItems: Int? = null,
        serializeAsJsonString: Boolean = false,
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = true,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
        colSpan: Int? = null,
        itemSchemaBlock: (ObjectSchemaBuilder.() -> Unit)? = null
    ) {
        val resolvedItemSchema = when {
            itemSchema != null -> itemSchema
            itemSchemaBlock != null -> ObjectSchemaBuilder(id = "${key}_item").apply(itemSchemaBlock).build()
            else -> error("objectListField '$key' requires either itemSchema or itemSchemaBlock.")
        }
        addNode(
            ObjectListFieldSpec(
                key = key,
                label = label,
                description = description,
                itemSchema = resolvedItemSchema,
                defaultValue = defaultValue,
                addButtonText = addButtonText,
                allowReordering = allowReordering,
                allowDeletion = allowDeletion,
                maxItems = maxItems,
                serializeAsJsonString = serializeAsJsonString,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                widgetHint = widgetHint,
                colSpan = colSpan
            )
        )
    }

    fun customField(
        key: String,
        label: String = "",
        rendererId: String,
        description: String? = null,
        defaultValue: Any? = null,
        config: Map<String, Any?> = emptyMap(),
        inlineRenderer: SchemaCustomFieldRenderer? = null,
        placeholder: String? = null,
        sectionId: String? = null,
        fullWidth: Boolean = false,
        required: Boolean = false,
        validators: List<SchemaValidator> = emptyList(),
        legacyKeys: List<String> = emptyList(),
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
        colSpan: Int? = null
    ) {
        addNode(
            CustomFieldSpec(
                key = key,
                label = label,
                description = description,
                rendererId = rendererId,
                defaultValue = defaultValue,
                config = config,
                inlineRenderer = inlineRenderer,
                placeholder = placeholder,
                sectionId = sectionId,
                fullWidth = fullWidth,
                required = required,
                validators = validators,
                legacyKeys = legacyKeys,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen,
                widgetHint = widgetHint,
                colSpan = colSpan
            )
        )
    }

    fun group(
        key: String,
        label: String = "",
        description: String? = null,
        bordered: Boolean = true,
        collapsible: Boolean = false,
        defaultExpanded: Boolean = true,
        sectionId: String? = null,
        fullWidth: Boolean = true,
        onMounted: ((SchemaForm) -> Unit)? = null,
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        block: SchemaBuilderNodeScope.() -> Unit
    ) {
        val childScope = SchemaBuilderNodeScope().apply(block)
        addNode(
            SchemaGroupSpec(
                key = key,
                label = label,
                description = description,
                bordered = bordered,
                collapsible = collapsible,
                defaultExpanded = defaultExpanded,
                children = childScope.buildNodes(),
                sectionId = sectionId,
                fullWidth = fullWidth,
                onMounted = onMounted,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen
            )
        )
    }

    fun asyncGroup(
        key: String,
        label: String = "",
        description: String? = null,
        bordered: Boolean = true,
        collapsible: Boolean = false,
        defaultExpanded: Boolean = true,
        loadingText: String = "Loading...",
        sectionId: String? = null,
        fullWidth: Boolean = true,
        onMounted: ((SchemaForm) -> Unit)? = null,
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        loader: () -> SchemaAsyncResult<List<SchemaNode>>
    ) {
        addNode(
            SchemaAsyncGroupSpec(
                key = key,
                label = label,
                description = description,
                bordered = bordered,
                collapsible = collapsible,
                defaultExpanded = defaultExpanded,
                loadingText = loadingText,
                loader = loader,
                sectionId = sectionId,
                fullWidth = fullWidth,
                onMounted = onMounted,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen
            )
        )
    }

    fun asyncGroup(
        key: String,
        label: String = "",
        description: String? = null,
        bordered: Boolean = true,
        collapsible: Boolean = false,
        defaultExpanded: Boolean = true,
        loadingText: String = "Loading...",
        scope: CoroutineScope,
        sectionId: String? = null,
        fullWidth: Boolean = true,
        onMounted: ((SchemaForm) -> Unit)? = null,
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        suspendLoader: suspend () -> List<SchemaNode>
    ) {
        addNode(
            SchemaAsyncGroupSpec(
                key = key,
                label = label,
                description = description,
                bordered = bordered,
                collapsible = collapsible,
                defaultExpanded = defaultExpanded,
                loadingText = loadingText,
                scope = scope,
                suspendLoader = suspendLoader,
                sectionId = sectionId,
                fullWidth = fullWidth,
                onMounted = onMounted,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen
            )
        )
    }

    fun indexedGroup(
        key: String,
        label: String = "",
        description: String? = null,
        count: Int = 20,
        indexLabelPrefix: String = "Level",
        serializeAsJsonString: Boolean = false,
        bordered: Boolean = true,
        sectionId: String? = null,
        fullWidth: Boolean = true,
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null,
        block: SchemaBuilderNodeScope.() -> Unit
    ) {
        val childScope = SchemaBuilderNodeScope().apply(block)
        addNode(
            SchemaIndexedGroupSpec(
                key = key,
                label = label,
                description = description,
                count = count,
                indexLabelPrefix = indexLabelPrefix,
                children = childScope.buildNodes(),
                serializeAsJsonString = serializeAsJsonString,
                bordered = bordered,
                sectionId = sectionId,
                fullWidth = fullWidth,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen
            )
        )
    }

    fun separator(
        key: String,
        label: String = "",
        description: String? = null,
        showLine: Boolean = true,
        sectionId: String? = null,
        fullWidth: Boolean = true,
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null
    ) {
        addNode(
            SchemaSeparatorSpec(
                key = key,
                label = label,
                description = description,
                showLine = showLine,
                sectionId = sectionId,
                fullWidth = fullWidth,
                visibleWhen = visibleWhen,
                enabledWhen = enabledWhen
            )
        )
    }

    fun reactiveRule(
        targetPath: String,
        targetConstraint: SchemaConstraintTarget,
        sourcePaths: List<String> = emptyList(),
        customKey: String? = null,
        syncEvaluator: ((SchemaEvalContext) -> Any?)? = null,
        asyncEvaluator: ((SchemaEvalContext) -> SchemaAsyncResult<Any?>)? = null
    ) {
        collectedRules.add(
            SchemaReactiveRule(
                targetPath = targetPath,
                targetConstraint = targetConstraint,
                sourcePaths = sourcePaths,
                syncEvaluator = syncEvaluator,
                asyncEvaluator = asyncEvaluator,
                customKey = customKey
            )
        )
    }
}
