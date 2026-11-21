package com.nexustools.genericui.ext.schema

/**
 * Result of validating a [SchemaForm] or [ObjectSchema] value map.
 */
data class SchemaValidationResult(
    val isValid: Boolean,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val globalErrors: List<String> = emptyList()
) {
    val allErrors: List<String>
        get() = globalErrors + fieldErrors.values.flatten()

    companion object {
        val VALID: SchemaValidationResult = SchemaValidationResult(isValid = true)
    }
}
