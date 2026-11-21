package com.nexustools.genericui.ext.schema

/**
 * Consumer-implemented widget adapter for [CustomFieldSpec] fields.
 */
interface SchemaCustomFieldRenderer {
    val rendererId: String

    fun createFieldController(
        spec: CustomFieldSpec,
        initialContext: SchemaEvalContext,
        labelText: String,
        hostForm: SchemaForm
    ): SchemaFieldController
}
