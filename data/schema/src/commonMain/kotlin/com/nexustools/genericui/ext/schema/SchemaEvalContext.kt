package com.nexustools.genericui.ext.schema

/**
 * Hierarchical evaluation context for [SchemaCondition]s, [SchemaReactiveRule]s, dynamic option
 * loaders, and validators.
 *
 * Supports nested dot paths (`"group.field"`) as well as relative parent-scope prefixes
 * (`".ParentField"`, `"..GrandparentField"`).
 */
class SchemaEvalContext(
    val values: Map<String, Any?> = emptyMap(),
    val parent: SchemaEvalContext? = null,
    val index: Int? = null,
    val stringValues: Map<String, String>? = null
) {
    fun child(
        childValues: Map<String, Any?>,
        index: Int? = null,
        childStringValues: Map<String, String>? = null
    ): SchemaEvalContext = SchemaEvalContext(
        values = childValues,
        parent = this,
        index = index,
        stringValues = childStringValues
    )

    fun root(): SchemaEvalContext {
        var cur: SchemaEvalContext = this
        while (cur.parent != null) {
            cur = cur.parent
        }
        return cur
    }

    fun getValue(path: String): Any? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return null

        var leadingDots = 0
        while (leadingDots < trimmed.length && trimmed[leadingDots] == '.') {
            leadingDots++
        }

        var targetContext: SchemaEvalContext? = this
        if (leadingDots > 0) {
            repeat(leadingDots) {
                targetContext = targetContext?.parent
            }
        }
        val activeContext = targetContext ?: return null
        val remainingPath = trimmed.substring(leadingDots)
        if (remainingPath.isEmpty()) return activeContext.values

        return resolveInValues(activeContext.values, remainingPath)
    }

    fun getString(path: String, fallback: String = ""): String {
        val value = getValue(path) ?: return fallback
        return value.toString()
    }

    fun getDouble(path: String): Double? {
        val value = getValue(path) ?: return null
        return when (value) {
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            is String -> value.trim().toDoubleOrNull()
            else -> value.toString().trim().toDoubleOrNull()
        }
    }

    fun getBoolean(path: String): Boolean? {
        val value = getValue(path) ?: return null
        return when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes" -> true
                "false", "0", "no" -> false
                else -> null
            }
            else -> null
        }
    }

    private fun resolveInValues(rootMap: Map<String, Any?>, path: String): Any? {
        if (path in rootMap) return rootMap[path]
        if ('.' !in path) return null

        val segments = path.split('.')
        var current: Any? = rootMap
        for (segment in segments) {
            if (segment.isEmpty()) return null
            current = when (current) {
                is Map<*, *> -> current[segment]
                is List<*> -> {
                    val idx = segment.toIntOrNull() ?: return null
                    current.getOrNull(idx)
                }
                else -> return null
            }
        }
        return current
    }
}
