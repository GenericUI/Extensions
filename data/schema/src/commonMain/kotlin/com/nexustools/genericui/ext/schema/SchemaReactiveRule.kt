package com.nexustools.genericui.ext.schema

/**
 * Cross-field reactive rule observing 1..N [sourcePaths] (or whole form when [sourcePaths] is empty),
 * evaluating synchronously via [syncEvaluator] or asynchronously via [asyncEvaluator], and applying
 * the resulting value to [targetConstraint] on [targetPath].
 */
data class SchemaReactiveRule(
    val targetPath: String,
    val targetConstraint: SchemaConstraintTarget,
    val sourcePaths: List<String> = emptyList(),
    val syncEvaluator: ((SchemaEvalContext) -> Any?)? = null,
    val asyncEvaluator: ((SchemaEvalContext) -> SchemaAsyncResult<Any?>)? = null,
    val customKey: String? = null
) {
    init {
        require(syncEvaluator != null || asyncEvaluator != null) {
            "SchemaReactiveRule for '$targetPath' ($targetConstraint) must provide syncEvaluator or asyncEvaluator."
        }
    }
}
