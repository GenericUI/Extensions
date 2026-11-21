package com.nexustools.genericui.ext.schema

/**
 * Integer (`Int` / `Long`) numeric field specification supporting slider+field, number field,
 * and `-` / `+` stepper presentations with optional [unit] suffix and [quickChips].
 */
data class IntFieldSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    override val defaultValue: Int = 0,
    val min: Int? = null,
    val max: Int? = null,
    val step: Int = 1,
    val unit: String? = null,
    val quickChips: List<Int> = emptyList(),
    val isLong: Boolean = false,
    val defaultLongValue: Long = defaultValue.toLong(),
    override val widgetHint: SchemaWidgetHint = SchemaWidgetHint.AUTO,
    override val placeholder: String? = null,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = false,
    override val required: Boolean = false,
    override val validators: List<SchemaValidator> = emptyList(),
    override val legacyKeys: List<String> = emptyList(),
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null,
    override val colSpan: Int? = null
) : SchemaFieldSpec
