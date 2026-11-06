package com.nexustools.genericui.web

/**
 * Internal bridge connecting HTML5 `<audio>` / `<video>` elements (`crossorigin="anonymous"`)
 * to the browser's Web Audio API (`AudioContext` + `MediaElementAudioSourceNode` + `AnalyserNode`).
 */
internal expect object WebAudioContextBridge {
    fun ensureCrossOriginAnonymous(mediaPeer: Any?)
    fun connectMediaElement(mediaPeer: Any?, fftSize: Int): Boolean
    fun resumeAudioContext(): Boolean
    fun hasActiveAnalyser(): Boolean
    fun readByteTimeDomainData(fftSize: Int, destination: ByteArray): Boolean
    fun readFloatTimeDomainData(fftSize: Int, destination: FloatArray): Boolean
    fun readByteFrequencyData(fftSize: Int, destination: ByteArray): Boolean
    fun readFloatFrequencyData(fftSize: Int, destination: FloatArray): Boolean
}
