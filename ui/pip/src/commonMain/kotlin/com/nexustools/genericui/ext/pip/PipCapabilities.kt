package com.nexustools.genericui.ext.pip

data class PipCapabilities(
    val isSupported: Boolean = false,
    val supportedContentTypes: Set<PipContentType> = emptySet(),
    val supportsCustomControlOverlay: Boolean = false,
    val supportsInPlaceWindowPip: Boolean = false,
    val supportsAutoEnterOnMinimize: Boolean = false,
    val supportsAutoEnterOnBackground: Boolean = false,
    val supportsAutoEnterOnTabSwitch: Boolean = false
) {
    companion object {
        val UNSUPPORTED = PipCapabilities()
    }
}
