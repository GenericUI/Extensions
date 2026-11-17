package com.nexustools.genericui.ext.threejs.geometries

import kotlin.math.PI

/**
 * Conical mesh geometry (`THREE.ConeGeometry`).
 */
class ConeGeometry(
    radius: Float = 1f,
    height: Float = 1f,
    radialSegments: Int = 32,
    heightSegments: Int = 1,
    openEnded: Boolean = false,
    thetaStart: Float = 0f,
    thetaLength: Float = (PI * 2.0).toFloat()
) : CylinderGeometry(
    radiusTop = 0f,
    radiusBottom = radius,
    height = height,
    radialSegments = radialSegments,
    heightSegments = heightSegments,
    openEnded = openEnded,
    thetaStart = thetaStart,
    thetaLength = thetaLength
) {
    override val type: String = "ConeGeometry"
}
