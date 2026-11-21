package com.nexustools.genericui.ext.schema

/**
 * Declarative filter value entry for [SelectFieldSpec.queryFilterRefs] and
 * [OrderedMultiSelectFieldSpec.queryFilterRefs], resolving either a static [Literal]
 * or a live sibling/parent [FieldRef].
 */
sealed interface SchemaFilterValue {
    data class Literal(val value: String) : SchemaFilterValue

    data class FieldRef(
        val fieldPath: String,
        val required: Boolean = true,
        val fallbackValue: String? = null
    ) : SchemaFilterValue
}
