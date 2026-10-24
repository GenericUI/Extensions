package com.nexustools.genericui.ext.threejs.renderers

/**
 * Frame & GPU resource telemetry (`THREE.WebGLInfo`) tracked by [WebGLRenderer].
 */
class WebGLRenderInfo {
    var geometriesCount: Int = 0
    var texturesCount: Int = 0
    var programsCount: Int = 0

    var geometries: Int
        get() = geometriesCount
        set(value) { geometriesCount = value }

    var textures: Int
        get() = texturesCount
        set(value) { texturesCount = value }

    var programs: Int
        get() = programsCount
        set(value) { programsCount = value }

    var frame: Long = 0L
    var calls: Int = 0
    var triangles: Int = 0
    var points: Int = 0
    var lines: Int = 0

    var autoReset: Boolean = true

    fun resetFrameCounters() {
        calls = 0
        triangles = 0
        points = 0
        lines = 0
    }

    fun resetFrameStats() = resetFrameCounters()
}
