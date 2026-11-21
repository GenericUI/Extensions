package com.nexustools.genericui.ext.schema

/**
 * Fixed-count (`1..count`) indexed sub-schema group with a header index switcher, serializing
 * to and from `List<Map<String, Any?>>` or a JSON array string.
 */
data class SchemaIndexedGroupSpec(
    override val key: String,
    override val label: String = "",
    override val description: String? = null,
    val count: Int = 20,
    val indexLabelPrefix: String = "Level",
    val children: List<SchemaNode> = emptyList(),
    val serializeAsJsonString: Boolean = false,
    val bordered: Boolean = true,
    override val sectionId: String? = null,
    override val fullWidth: Boolean = true,
    override val visibleWhen: SchemaCondition? = null,
    override val enabledWhen: SchemaCondition? = null
) : SchemaNode {
    init {
        require(count >= 1) { "SchemaIndexedGroupSpec '$key' requires count >= 1, got $count." }
    }
}
