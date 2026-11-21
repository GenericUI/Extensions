package com.nexustools.genericui.ext.schema

/**
 * Pluggable field validator evaluated during [SchemaForm.validate] and [ObjectSchema.validateValues].
 * Returns `null` when valid, or a non-blank error message string when invalid.
 */
sealed interface SchemaValidator {
    fun validate(value: Any?, context: SchemaEvalContext, fieldLabel: String): String?

    data class Required(val message: String? = null) : SchemaValidator {
        override fun validate(value: Any?, context: SchemaEvalContext, fieldLabel: String): String? {
            val empty = when (value) {
                null -> true
                is String -> value.isBlank()
                is Collection<*> -> value.isEmpty()
                is Map<*, *> -> value.isEmpty()
                else -> false
            }
            return if (empty) message ?: "$fieldLabel is required." else null
        }
    }

    data class NumericRange(
        val min: Double? = null,
        val max: Double? = null,
        val message: String? = null
    ) : SchemaValidator {
        override fun validate(value: Any?, context: SchemaEvalContext, fieldLabel: String): String? {
            if (value == null || (value is String && value.isBlank())) return null
            val num = when (value) {
                is Number -> value.toDouble()
                is String -> value.trim().toDoubleOrNull()
                else -> value.toString().trim().toDoubleOrNull()
            } ?: return message ?: "$fieldLabel must be a valid number."
            if (min != null && num < min) {
                return message ?: "$fieldLabel must be at least ${formatBound(min)}."
            }
            if (max != null && num > max) {
                return message ?: "$fieldLabel must be at most ${formatBound(max)}."
            }
            return null
        }

        private fun formatBound(bound: Double): String {
            val asLong = bound.toLong()
            return if (bound == asLong.toDouble()) asLong.toString() else bound.toString()
        }
    }

    data class MaxLength(
        val maxLength: Int,
        val message: String? = null
    ) : SchemaValidator {
        override fun validate(value: Any?, context: SchemaEvalContext, fieldLabel: String): String? {
            val str = value?.toString() ?: return null
            return if (str.length > maxLength) {
                message ?: "$fieldLabel must be at most $maxLength characters."
            } else {
                null
            }
        }
    }

    data class MinLength(
        val minLength: Int,
        val message: String? = null
    ) : SchemaValidator {
        override fun validate(value: Any?, context: SchemaEvalContext, fieldLabel: String): String? {
            val str = value?.toString() ?: ""
            return if (str.length < minLength) {
                message ?: "$fieldLabel must be at least $minLength characters."
            } else {
                null
            }
        }
    }

    data class RegexMatch(
        val pattern: String,
        val message: String? = null
    ) : SchemaValidator {
        private val regex = Regex(pattern)

        override fun validate(value: Any?, context: SchemaEvalContext, fieldLabel: String): String? {
            if (value == null) return null
            val str = value.toString()
            if (str.isEmpty()) return null
            return if (!regex.matches(str)) {
                message ?: "$fieldLabel does not match the required format."
            } else {
                null
            }
        }
    }

    class Custom(
        val message: String = "Invalid value",
        val predicate: (Any?, SchemaEvalContext) -> Boolean
    ) : SchemaValidator {
        override fun validate(value: Any?, context: SchemaEvalContext, fieldLabel: String): String? =
            if (predicate(value, context)) null else message
    }
}
