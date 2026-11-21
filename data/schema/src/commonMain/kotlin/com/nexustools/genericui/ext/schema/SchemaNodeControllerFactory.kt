package com.nexustools.genericui.ext.schema

internal object SchemaNodeControllerFactory {

    fun createFieldController(
        spec: SchemaFieldSpec,
        labelText: String,
        compactInline: Boolean,
        hostForm: SchemaForm?,
        labelTranslator: ((String) -> String)?,
        customRenderers: Map<String, SchemaCustomFieldRenderer>,
        evalContextProvider: () -> SchemaEvalContext,
        deferInitialOptionsLoad: Boolean = false
    ): SchemaFieldController = when (spec) {
        is BooleanFieldSpec -> BooleanSchemaFieldController(spec, labelText, compactInline)
        is IntFieldSpec -> IntSchemaFieldController(spec, labelText, compactInline)
        is DoubleFieldSpec -> DoubleSchemaFieldController(spec, labelText, compactInline)
        is CompoundNumberFieldSpec -> CompoundNumberSchemaFieldController(spec, labelText, compactInline)
        is StringFieldSpec -> StringSchemaFieldController(spec, labelText, compactInline)
        is ColorFieldSpec -> ColorSchemaFieldController(spec, labelText, compactInline)
        is SelectFieldSpec -> SelectSchemaFieldController(
            spec,
            labelText,
            compactInline,
            evalContextProvider(),
            deferInitialOptionsLoad = deferInitialOptionsLoad
        )
        is OrderedMultiSelectFieldSpec -> OrderedMultiSelectSchemaFieldController(
            spec,
            labelText,
            compactInline,
            evalContextProvider(),
            deferInitialOptionsLoad = deferInitialOptionsLoad
        )
        is PolymorphicRowListFieldSpec -> PolymorphicRowListSchemaFieldController(
            polySpec = spec,
            initialLabel = labelText,
            compactInline = compactInline,
            labelTranslator = labelTranslator,
            evalContextProvider = evalContextProvider,
            controllerFactory = { slotSpec, slotLabel, slotCompact ->
                createFieldController(
                    spec = slotSpec,
                    labelText = slotLabel,
                    compactInline = slotCompact,
                    hostForm = hostForm,
                    labelTranslator = labelTranslator,
                    customRenderers = customRenderers,
                    evalContextProvider = evalContextProvider
                )
            }
        )
        is CubicBezierFieldSpec -> CubicBezierSchemaFieldController(spec, labelText, compactInline)
        is ObjectListFieldSpec -> ObjectListSchemaFieldController(
            listSpec = spec,
            initialLabel = labelText,
            compactInline = compactInline,
            labelTranslator = labelTranslator,
            customRenderers = customRenderers,
            evalContextProvider = evalContextProvider
        )
        is CustomFieldSpec -> {
            val renderer = spec.inlineRenderer
                ?: customRenderers[spec.rendererId]
                ?: SchemaRegistry.getCustomRenderer(spec.rendererId)
                ?: throw IllegalStateException(
                    "No SchemaCustomFieldRenderer registered for rendererId='${spec.rendererId}' (field '${spec.key}')."
                )
            val resolvedForm = hostForm
                ?: throw IllegalStateException(
                    "CustomFieldSpec '${spec.key}' requires a mounted host SchemaForm."
                )
            renderer.createFieldController(
                spec = spec,
                initialContext = evalContextProvider(),
                labelText = labelText,
                hostForm = resolvedForm
            )
        }
    }
}
