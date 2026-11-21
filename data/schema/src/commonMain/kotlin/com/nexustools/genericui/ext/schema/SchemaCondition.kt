package com.nexustools.genericui.ext.schema

/**
 * Serializable AST for conditional visibility (`visibleWhen`) and enablement (`enabledWhen`)
 * evaluated against a [SchemaEvalContext].
 */
sealed interface SchemaCondition {
    fun evaluate(context: SchemaEvalContext): Boolean

    fun referencedPaths(): Set<String>

    data class Equals(
        val fieldPath: String,
        val value: Any?
    ) : SchemaCondition {
        override fun evaluate(context: SchemaEvalContext): Boolean =
            valuesMatch(context.getValue(fieldPath), value)

        override fun referencedPaths(): Set<String> = setOf(fieldPath)
    }

    data class NotEquals(
        val fieldPath: String,
        val value: Any?
    ) : SchemaCondition {
        override fun evaluate(context: SchemaEvalContext): Boolean =
            !valuesMatch(context.getValue(fieldPath), value)

        override fun referencedPaths(): Set<String> = setOf(fieldPath)
    }

    data class In(
        val fieldPath: String,
        val values: Collection<Any?>
    ) : SchemaCondition {
        override fun evaluate(context: SchemaEvalContext): Boolean {
            val actual = context.getValue(fieldPath)
            return values.any { candidate -> valuesMatch(actual, candidate) }
        }

        override fun referencedPaths(): Set<String> = setOf(fieldPath)
    }

    data class IsTrue(val fieldPath: String) : SchemaCondition {
        override fun evaluate(context: SchemaEvalContext): Boolean =
            isTruthy(context.getValue(fieldPath))

        override fun referencedPaths(): Set<String> = setOf(fieldPath)
    }

    data class IsFalse(val fieldPath: String) : SchemaCondition {
        override fun evaluate(context: SchemaEvalContext): Boolean =
            !isTruthy(context.getValue(fieldPath))

        override fun referencedPaths(): Set<String> = setOf(fieldPath)
    }

    data class GreaterThan(
        val fieldPath: String,
        val threshold: Double,
        val inclusive: Boolean = false
    ) : SchemaCondition {
        override fun evaluate(context: SchemaEvalContext): Boolean {
            val num = context.getDouble(fieldPath) ?: return false
            return if (inclusive) num >= threshold else num > threshold
        }

        override fun referencedPaths(): Set<String> = setOf(fieldPath)
    }

    data class LessThan(
        val fieldPath: String,
        val threshold: Double,
        val inclusive: Boolean = false
    ) : SchemaCondition {
        override fun evaluate(context: SchemaEvalContext): Boolean {
            val num = context.getDouble(fieldPath) ?: return false
            return if (inclusive) num <= threshold else num < threshold
        }

        override fun referencedPaths(): Set<String> = setOf(fieldPath)
    }

    data class IsNotBlank(val fieldPath: String) : SchemaCondition {
        override fun evaluate(context: SchemaEvalContext): Boolean {
            val actual = context.getValue(fieldPath) ?: return false
            return when (actual) {
                is Collection<*> -> actual.isNotEmpty()
                is Map<*, *> -> actual.isNotEmpty()
                else -> actual.toString().isNotBlank()
            }
        }

        override fun referencedPaths(): Set<String> = setOf(fieldPath)
    }

    data class And(val conditions: List<SchemaCondition>) : SchemaCondition {
        constructor(vararg conditions: SchemaCondition) : this(conditions.toList())

        override fun evaluate(context: SchemaEvalContext): Boolean =
            conditions.all { it.evaluate(context) }

        override fun referencedPaths(): Set<String> =
            conditions.flatMapTo(linkedSetOf()) { it.referencedPaths() }
    }

    data class Or(val conditions: List<SchemaCondition>) : SchemaCondition {
        constructor(vararg conditions: SchemaCondition) : this(conditions.toList())

        override fun evaluate(context: SchemaEvalContext): Boolean =
            conditions.any { it.evaluate(context) }

        override fun referencedPaths(): Set<String> =
            conditions.flatMapTo(linkedSetOf()) { it.referencedPaths() }
    }

    data class Not(val condition: SchemaCondition) : SchemaCondition {
        override fun evaluate(context: SchemaEvalContext): Boolean =
            !condition.evaluate(context)

        override fun referencedPaths(): Set<String> = condition.referencedPaths()
    }

    companion object {
        internal fun isTruthy(value: Any?): Boolean = when (value) {
            null -> false
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is Collection<*> -> value.isNotEmpty()
            is Map<*, *> -> value.isNotEmpty()
            else -> when (value.toString().trim().lowercase()) {
                "", "false", "0", "no", "null", "off" -> false
                else -> true
            }
        }

        internal fun valuesMatch(actual: Any?, expected: Any?): Boolean {
            if (actual == null && expected == null) return true
            if (actual == null || expected == null) {
                return (actual == null && expected is String && expected.isEmpty()) ||
                    (expected == null && actual is String && actual.isEmpty())
            }
            if (actual == expected) return true

            if (actual is Boolean) {
                val expectedBool = parseBooleanOrNull(expected)
                if (expectedBool != null) return actual == expectedBool
            }
            if (expected is Boolean) {
                val actualBool = parseBooleanOrNull(actual)
                if (actualBool != null) return actualBool == expected
            }

            if (actual is Number && expected is Number) {
                return actual.toDouble() == expected.toDouble()
            }
            if (actual is Number && expected is String) {
                val expNum = expected.trim().toDoubleOrNull()
                if (expNum != null) return actual.toDouble() == expNum
            }
            if (expected is Number && actual is String) {
                val actNum = actual.trim().toDoubleOrNull()
                if (actNum != null) return actNum == expected.toDouble()
            }

            if (actual is Enum<*> && expected is String) {
                return actual.name == expected
            }
            if (expected is Enum<*> && actual is String) {
                return actual == expected.name
            }

            return actual.toString() == expected.toString()
        }

        private fun parseBooleanOrNull(value: Any?): Boolean? = when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> when (value.trim().lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> null
            }
            else -> null
        }
    }
}
