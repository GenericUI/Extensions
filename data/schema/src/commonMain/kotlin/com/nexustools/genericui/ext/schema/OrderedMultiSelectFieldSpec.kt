package com.nexustools.genericui.ext.schema

/**
 * Ordered multi-select list field specification (`List<String>` / JSON string array) with
 * picker + `Add`, per-item `⮝` up / `⮟` down / `X` delete buttons, duplicate & deletion
 * controls, dynamic capacity badge (`maxItems`), and milestone dividers (`itemSeparatorProvider`).
 */
data class OrderedMultiSelectFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    override val defaultValue: List<String> = emptyList(),
    val staticOptions: List<SchemaOption> = emptyList(),
    val optionsProvider: ((SchemaEvalContext) -> List<SchemaOption>)? = null,
    val asyncOptionsLoader: ((SchemaEvalContext, Map<String, String>?) -> SchemaAsyncResult<List<SchemaOption>>)? = null,
    val optionTransformer: ((List<SchemaOption>, SchemaEvalContext) -> List<SchemaOption>)? = null,
    val searchable: Boolean = false,
    val allowDuplicates: Boolean = false,
    val allowDeletion: Boolean = true,
    val maxItems: Int? = null,
    val serializeAsJsonString: Boolean = false,
    val queryFilterRefs: Map<String, SchemaFilterValue> = emptyMap(),
    val itemSeparatorProvider: ((oneBasedIndex: Int) -> String?)? = null,
    val addButtonText: String = "Add",
    override val placeholder: String? = null,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = true,
    override val required: Boolean = false,
    override val validators: List<SchemaValidator> = emptyList(),
    override val legacyKeys: List<String> = emptyList(),
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null,
    override val widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
    override val colSpan: Int? = null
) : SchemaFieldSpec
