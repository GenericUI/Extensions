package com.nexustools.genericui.ext.schema

import com.nexustools.genericui.core.AbstractComponent

/**
 * Primary programmatic entry point for creating schema-driven forms and modal dialogs.
 */
object SchemaExtension {

    fun createForm(
        schema: ObjectSchema,
        layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
        initialData: Map<String, Any?>? = null,
        labelTranslator: ((String) -> String)? = null,
        customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
        showHeader: Boolean = false
    ): SchemaForm = SchemaForm(
        schema = schema,
        layoutMode = layoutMode,
        initialData = initialData,
        labelTranslator = labelTranslator,
        customRenderers = customRenderers,
        showHeader = showHeader
    )

    fun <T : Any> createForm(
        descriptor: SchemaDescriptor<T>,
        initialInstance: T = descriptor.defaultInstance(),
        layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
        labelTranslator: ((String) -> String)? = null,
        customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
        showHeader: Boolean = false
    ): SchemaForm = SchemaForm(
        schema = descriptor.schema,
        layoutMode = layoutMode,
        initialData = descriptor.toValueMap(initialInstance),
        labelTranslator = labelTranslator,
        customRenderers = customRenderers,
        showHeader = showHeader
    )

    inline fun <reified T : Any> createForm(
        initialInstance: T? = null,
        layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
        noinline labelTranslator: ((String) -> String)? = null,
        customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
        showHeader: Boolean = false
    ): SchemaForm {
        val descriptor = SchemaRegistry.getForClass<T>()
            ?: throw IllegalStateException("No SchemaDescriptor registered for ${T::class.simpleName}.")
        return createForm(
            descriptor = descriptor,
            initialInstance = initialInstance ?: descriptor.defaultInstance(),
            layoutMode = layoutMode,
            labelTranslator = labelTranslator,
            customRenderers = customRenderers,
            showHeader = showHeader
        )
    }

    fun createModal(
        schema: ObjectSchema,
        initialData: Map<String, Any?>? = null,
        layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
        labelTranslator: ((String) -> String)? = null,
        customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
        modalTitle: String = schema.title,
        applyButtonText: String = "Apply",
        cancelButtonText: String = "Cancel",
        resetDefaultsButtonText: String = "Reset Defaults",
        showPresetSelector: Boolean = schema.presets.isNotEmpty(),
        showResetDefaultsButton: Boolean = true,
        headerSlot: AbstractComponent? = null,
        previewSlot: AbstractComponent? = null,
        validateOnApply: Boolean = true,
        closeOnApply: Boolean = true,
        onApply: ((SchemaForm, Map<String, Any?>) -> Unit)? = null,
        onCancel: ((SchemaForm) -> Unit)? = null
    ): SchemaModal = SchemaModal(
        schema = schema,
        initialData = initialData,
        layoutMode = layoutMode,
        labelTranslator = labelTranslator,
        customRenderers = customRenderers,
        modalTitle = modalTitle,
        applyButtonText = applyButtonText,
        cancelButtonText = cancelButtonText,
        resetDefaultsButtonText = resetDefaultsButtonText,
        showPresetSelector = showPresetSelector,
        showResetDefaultsButton = showResetDefaultsButton,
        headerSlot = headerSlot,
        previewSlot = previewSlot,
        validateOnApply = validateOnApply,
        closeOnApply = closeOnApply,
        onApply = onApply,
        onCancel = onCancel
    )

    fun <T : Any> createModal(
        descriptor: SchemaDescriptor<T>,
        initialInstance: T = descriptor.defaultInstance(),
        layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
        labelTranslator: ((String) -> String)? = null,
        customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
        modalTitle: String = descriptor.schema.title,
        applyButtonText: String = "Apply",
        cancelButtonText: String = "Cancel",
        resetDefaultsButtonText: String = "Reset Defaults",
        headerSlot: AbstractComponent? = null,
        previewSlot: AbstractComponent? = null,
        validateOnApply: Boolean = true,
        closeOnApply: Boolean = true,
        onApply: ((T) -> Unit)? = null,
        onCancel: (() -> Unit)? = null
    ): SchemaModal = SchemaModal.forData(
        descriptor = descriptor,
        initialInstance = initialInstance,
        layoutMode = layoutMode,
        labelTranslator = labelTranslator,
        customRenderers = customRenderers,
        modalTitle = modalTitle,
        applyButtonText = applyButtonText,
        cancelButtonText = cancelButtonText,
        resetDefaultsButtonText = resetDefaultsButtonText,
        headerSlot = headerSlot,
        previewSlot = previewSlot,
        validateOnApply = validateOnApply,
        closeOnApply = closeOnApply,
        onApply = onApply,
        onCancel = onCancel
    )

    inline fun <reified T : Any> createModal(
        initialInstance: T? = null,
        layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
        noinline labelTranslator: ((String) -> String)? = null,
        customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
        modalTitle: String? = null,
        applyButtonText: String = "Apply",
        cancelButtonText: String = "Cancel",
        resetDefaultsButtonText: String = "Reset Defaults",
        headerSlot: AbstractComponent? = null,
        previewSlot: AbstractComponent? = null,
        validateOnApply: Boolean = true,
        closeOnApply: Boolean = true,
        noinline onApply: ((T) -> Unit)? = null,
        noinline onCancel: (() -> Unit)? = null
    ): SchemaModal = SchemaModal.forData<T>(
        initialInstance = initialInstance,
        layoutMode = layoutMode,
        labelTranslator = labelTranslator,
        customRenderers = customRenderers,
        modalTitle = modalTitle,
        applyButtonText = applyButtonText,
        cancelButtonText = cancelButtonText,
        resetDefaultsButtonText = resetDefaultsButtonText,
        headerSlot = headerSlot,
        previewSlot = previewSlot,
        validateOnApply = validateOnApply,
        closeOnApply = closeOnApply,
        onApply = onApply,
        onCancel = onCancel
    )
}
