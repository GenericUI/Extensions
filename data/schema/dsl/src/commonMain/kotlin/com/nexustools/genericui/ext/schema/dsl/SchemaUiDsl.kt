package com.nexustools.genericui.ext.schema.dsl

import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.ext.schema.ObjectSchema
import com.nexustools.genericui.ext.schema.SchemaCustomFieldRenderer
import com.nexustools.genericui.ext.schema.SchemaDescriptor
import com.nexustools.genericui.ext.schema.SchemaExtension
import com.nexustools.genericui.ext.schema.SchemaForm
import com.nexustools.genericui.ext.schema.SchemaLayoutMode
import com.nexustools.genericui.ext.schema.SchemaModal

fun AbstractContainer.schemaForm(
    schema: ObjectSchema,
    layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
    initialData: Map<String, Any?>? = null,
    labelTranslator: ((String) -> String)? = null,
    customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
    showHeader: Boolean = false,
    init: SchemaForm.() -> Unit = {}
): SchemaForm = SchemaExtension.createForm(
    schema = schema,
    layoutMode = layoutMode,
    initialData = initialData,
    labelTranslator = labelTranslator,
    customRenderers = customRenderers,
    showHeader = showHeader
).also { form ->
    form.init()
    addChild(form)
}

fun AbstractContainer.schemaForm(
    id: String,
    title: String = "",
    description: String? = null,
    version: Int = 1,
    layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
    initialData: Map<String, Any?>? = null,
    labelTranslator: ((String) -> String)? = null,
    customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
    showHeader: Boolean = false,
    schemaBlock: ObjectSchemaBuilder.() -> Unit
): SchemaForm = schemaForm(
    schema = objectSchema(
        id = id,
        title = title,
        description = description,
        version = version,
        block = schemaBlock
    ),
    layoutMode = layoutMode,
    initialData = initialData,
    labelTranslator = labelTranslator,
    customRenderers = customRenderers,
    showHeader = showHeader
)

fun <T : Any> AbstractContainer.schemaForm(
    descriptor: SchemaDescriptor<T>,
    initialInstance: T = descriptor.defaultInstance(),
    layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
    labelTranslator: ((String) -> String)? = null,
    customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
    showHeader: Boolean = false,
    init: SchemaForm.() -> Unit = {}
): SchemaForm = SchemaExtension.createForm(
    descriptor = descriptor,
    initialInstance = initialInstance,
    layoutMode = layoutMode,
    labelTranslator = labelTranslator,
    customRenderers = customRenderers,
    showHeader = showHeader
).also { form ->
    form.init()
    addChild(form)
}

inline fun <reified T : Any> AbstractContainer.schemaForm(
    initialInstance: T? = null,
    layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
    noinline labelTranslator: ((String) -> String)? = null,
    customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
    showHeader: Boolean = false,
    init: SchemaForm.() -> Unit = {}
): SchemaForm = SchemaExtension.createForm<T>(
    initialInstance = initialInstance,
    layoutMode = layoutMode,
    labelTranslator = labelTranslator,
    customRenderers = customRenderers,
    showHeader = showHeader
).also { form ->
    form.init()
    addChild(form)
}

fun buildSchemaForm(
    schema: ObjectSchema,
    layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
    initialData: Map<String, Any?>? = null,
    labelTranslator: ((String) -> String)? = null,
    customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
    showHeader: Boolean = false,
    init: SchemaForm.() -> Unit = {}
): SchemaForm = SchemaExtension.createForm(
    schema = schema,
    layoutMode = layoutMode,
    initialData = initialData,
    labelTranslator = labelTranslator,
    customRenderers = customRenderers,
    showHeader = showHeader
).apply(init)

fun schemaModal(
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
    onCancel: ((SchemaForm) -> Unit)? = null,
    init: SchemaModal.() -> Unit = {}
): SchemaModal = SchemaExtension.createModal(
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
).apply(init)

fun schemaModal(
    id: String,
    title: String = "",
    description: String? = null,
    version: Int = 1,
    initialData: Map<String, Any?>? = null,
    layoutMode: SchemaLayoutMode = SchemaLayoutMode.STACKED,
    labelTranslator: ((String) -> String)? = null,
    customRenderers: Map<String, SchemaCustomFieldRenderer> = emptyMap(),
    applyButtonText: String = "Apply",
    cancelButtonText: String = "Cancel",
    resetDefaultsButtonText: String = "Reset Defaults",
    showResetDefaultsButton: Boolean = true,
    headerSlot: AbstractComponent? = null,
    previewSlot: AbstractComponent? = null,
    validateOnApply: Boolean = true,
    closeOnApply: Boolean = true,
    onApply: ((SchemaForm, Map<String, Any?>) -> Unit)? = null,
    onCancel: ((SchemaForm) -> Unit)? = null,
    schemaBlock: ObjectSchemaBuilder.() -> Unit
): SchemaModal = schemaModal(
    schema = objectSchema(
        id = id,
        title = title,
        description = description,
        version = version,
        block = schemaBlock
    ),
    initialData = initialData,
    layoutMode = layoutMode,
    labelTranslator = labelTranslator,
    customRenderers = customRenderers,
    modalTitle = title,
    applyButtonText = applyButtonText,
    cancelButtonText = cancelButtonText,
    resetDefaultsButtonText = resetDefaultsButtonText,
    showResetDefaultsButton = showResetDefaultsButton,
    headerSlot = headerSlot,
    previewSlot = previewSlot,
    validateOnApply = validateOnApply,
    closeOnApply = closeOnApply,
    onApply = onApply,
    onCancel = onCancel
)

fun <T : Any> schemaModal(
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
    onCancel: (() -> Unit)? = null,
    init: SchemaModal.() -> Unit = {}
): SchemaModal = SchemaExtension.createModal(
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
).apply(init)

inline fun <reified T : Any> schemaModal(
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
    noinline onCancel: (() -> Unit)? = null,
    init: SchemaModal.() -> Unit = {}
): SchemaModal = SchemaExtension.createModal<T>(
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
).apply(init)
