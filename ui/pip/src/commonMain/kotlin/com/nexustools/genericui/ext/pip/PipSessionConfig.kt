package com.nexustools.genericui.ext.pip

data class PipSessionConfig(
    val enabled: Boolean = true,
    val aspectRatioNumerator: Int = 16,
    val aspectRatioDenominator: Int = 9,
    val autoEnterOnMinimize: Boolean = true,
    val autoEnterOnBackground: Boolean = true,
    val autoEnterOnTabSwitch: Boolean = true,
    val pauseOnCloseButton: Boolean = true,
    val preferredWidthPx: Int = 420,
    val preferredHeightPx: Int = 236,
    val screenMarginPx: Int = 24,
    val title: String = ""
)
