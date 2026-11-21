package com.nexustools.genericui.ext.schema

/**
 * Single-choice select/combobox field specification supporting static options, synchronous
 * [optionsProvider], asynchronous [asyncOptionsLoader], declarative [queryFilterRefs]
 * dependency suspension, [optionTransformer], searchable combo boxes, and [allowUnlistedSavedValue].
 */
data class SelectFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    override val defaultValue: String? = null,
    val staticOptions: List<SchemaOption> = emptyList(),
    val optionsProvider: ((SchemaEvalContext) -> List<SchemaOption>)? = null,
    val asyncOptionsLoader: ((SchemaEvalContext, Map<String, String>?) -> SchemaAsyncResult<List<SchemaOption>>)? = null,
    val optionTransformer: ((List<SchemaOption>, SchemaEvalContext) -> List<SchemaOption>)? = null,
    val searchable: Boolean = false,
    val allowUnlistedSavedValue: Boolean = false,
    val prependNullOption: SchemaOption? = null,
    val queryFilterRefs: Map<String, SchemaFilterValue> = emptyMap(),
    override val placeholder: String? = null,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = false,
    override val required: Boolean = false,
    override val validators: List<SchemaValidator> = emptyList(),
    override val legacyKeys: List<String> = emptyList(),
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null,
    override val widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
    override val colSpan: Int? = null
) : SchemaFieldSpec
