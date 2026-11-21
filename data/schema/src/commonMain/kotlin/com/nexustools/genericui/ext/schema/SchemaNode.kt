package com.nexustools.genericui.ext.schema

/**
 * Base contract for all structural and field nodes inside an [ObjectSchema].
 */
sealed interface SchemaNode {
    val key: String
    val label: String
    val description: String?
    val sectionId: String?
    val fullWidth: Boolean
    val visibleWhen: SchemaCondition?
    val enabledWhen: SchemaCondition?
}
