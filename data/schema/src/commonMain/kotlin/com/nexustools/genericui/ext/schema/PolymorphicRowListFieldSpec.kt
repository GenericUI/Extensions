package com.nexustools.genericui.ext.schema

/**
 * Dynamic full-width list of discriminated multi-slot rows (`List<List<String?>>` / 2D JSON matrix)
 * backed by [variants], with a header variant picker + `Add` button and per-row `X` delete button.
 */
data class PolymorphicRowListFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    val variants: List<SchemaRowVariantSpec>,
    override val defaultValue: List<List<String?>> = emptyList(),
    val serializeAsJsonString: Boolean = false,
    val includeVariantIdInRow: Boolean = (variants.size > 1),
    val allowReordering: Boolean = false,
    val allowDeletion: Boolean = true,
    val maxRows: Int? = null,
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
