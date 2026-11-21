package com.nexustools.genericui.ext.schema

import kotlinx.coroutines.CoroutineScope

/**
 * Asynchronously loaded nested sub-schema group.
 *
 * Displays a loading indicator while [loader] resolves, buffers incoming `setData` and
 * `enableAll`/`disableAll` state changes until ready, mounts the resolved [SchemaForm],
 * and displays an inline danger alert if loading fails.
 */
data class SchemaAsyncGroupSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    val bordered: Boolean = true,
    val collapsible: Boolean = false,
    val defaultExpanded: Boolean = true,
    val loadingText: String = "Loading...",
    val loader: () -> SchemaAsyncResult<List<SchemaNode>>,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = true,
    val onMounted: ((SchemaForm) -> Unit)? = null,
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null
) : SchemaNode {
    constructor(
        key: String,
        label: String = "",
        description: String? = null,
        bordered: Boolean = true,
        collapsible: Boolean = false,
        defaultExpanded: Boolean = true,
        loadingText: String = "Loading...",
        scope: CoroutineScope,
        suspendLoader: suspend () -> List<SchemaNode>,
        sectionId: String? = null,
        fullWidth: Boolean = true,
        onMounted: ((SchemaForm) -> Unit)? = null,
        visibleWhen: SchemaCondition? = null,
        enabledWhen: SchemaCondition? = null
    ) : this(
        key = key,
        label = label,
        description = description,
        bordered = bordered,
        collapsible = collapsible,
        defaultExpanded = defaultExpanded,
        loadingText = loadingText,
        loader = { SchemaAsyncResult.fromSuspend(scope, suspendLoader) },
        sectionId = sectionId,
        fullWidth = fullWidth,
        onMounted = onMounted,
        visibleWhen = visibleWhen,
        enabledWhen = enabledWhen
    )
}
