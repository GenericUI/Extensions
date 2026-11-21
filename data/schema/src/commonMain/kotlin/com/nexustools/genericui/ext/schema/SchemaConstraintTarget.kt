package com.nexustools.genericui.ext.schema

/**
 * Target property/constraint updated dynamically by a [SchemaReactiveRule].
 */
enum class SchemaConstraintTarget {
    MIN,
    MAX,
    STEP,
    DEFAULT_VALUE,
    OPTIONS,
    QUERY_FILTER,
    ITEM_SEPARATOR,
    VISIBLE,
    ENABLED,
    LABEL,
    PLACEHOLDER,
    DESCRIPTION,
    CUSTOM
}
