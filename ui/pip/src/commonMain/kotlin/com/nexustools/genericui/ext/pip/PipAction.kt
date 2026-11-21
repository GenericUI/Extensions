package com.nexustools.genericui.ext.pip

data class PipAction(
    val id: String,
    val label: String,
    val icon: PipActionIcon,
    val enabled: Boolean = true,
    val customResourceUri: String? = null,
    val onTrigger: () -> Unit = {}
)
