package com.nexustools.genericui.ext.threejs.controls

import com.nexustools.genericui.component.media.AbstractCanvas
import com.nexustools.genericui.ext.threejs.cameras.Camera
import com.nexustools.genericui.ext.threejs.core.EventDispatcher
import com.nexustools.genericui.ext.threejs.math.MathUtils
import com.nexustools.genericui.ext.threejs.math.Vector2
import com.nexustools.genericui.ext.threejs.math.Vector3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Interactive spherical camera orbit, pan, and zoom controller (`THREE.OrbitControls`)
 * that attaches directly to any GenericUI [AbstractCanvas] (`GLCanvas` or `VKCanvas`).
 */
class OrbitControls(
    val camera: Camera,
    canvas: AbstractCanvas<*>? = null
) : EventDispatcher() {

    var enabled: Boolean = true
    val target: Vector3 = Vector3()

    var minDistance: Float = 0.05f
    var maxDistance: Float = Float.POSITIVE_INFINITY

    var minPolarAngle: Float = 0.01f
    var maxPolarAngle: Float = (PI - 0.01).toFloat()

    var enableDamping: Boolean = false
    var dampingFactor: Float = 0.05f

    var enableZoom: Boolean = true
    var zoomSpeed: Float = 1.0f

    var enableRotate: Boolean = true
    var rotateSpeed: Float = 1.0f

    var enablePan: Boolean = true
    var panSpeed: Float = 1.0f

    var autoRotate: Boolean = false
    var autoRotateSpeed: Float = 2.0f

    private var sphericalRadius: Float = 5f
    private var sphericalPhi: Float = (PI * 0.35).toFloat()
    private var sphericalTheta: Float = 0f

    private var isDraggingRotate: Boolean = false
    private var isDraggingPan: Boolean = false
    private val lastPointer = Vector2()

    init {
        syncSphericalFromCamera()
        if (canvas != null) {
            attachToCanvas(canvas)
        }
    }

    fun attachToCanvas(canvas: AbstractCanvas<*>) {
        canvas.setOnPointerDown { event ->
            if (!enabled) return@setOnPointerDown
            lastPointer.set(event.x, event.y)
            if (event.button == 2 || event.shiftKey) {
                isDraggingPan = enablePan
            } else {
                isDraggingRotate = enableRotate
            }
        }

        canvas.setOnPointerMove { event ->
            if (!enabled) return@setOnPointerMove
            val dx = event.x - lastPointer.x
            val dy = event.y - lastPointer.y
            lastPointer.set(event.x, event.y)

            val w = canvas.surfaceWidth.coerceAtLeast(200).toFloat()
            val h = canvas.surfaceHeight.coerceAtLeast(200).toFloat()

            if (isDraggingRotate && event.buttonsMask != 0) {
                rotateLeft((2f * PI.toFloat() * dx / w) * rotateSpeed)
                rotateUp((2f * PI.toFloat() * dy / h) * rotateSpeed)
                update()
            } else if (isDraggingPan && event.buttonsMask != 0) {
                pan(dx * 0.01f * panSpeed, dy * 0.01f * panSpeed)
                update()
            }
        }

        canvas.setOnPointerUp {
            isDraggingRotate = false
            isDraggingPan = false
        }

        canvas.setOnWheel { wheel ->
            if (!enabled || !enableZoom) return@setOnWheel
            val scale = if (wheel.deltaY > 0f) 1f + 0.1f * zoomSpeed else 1f / (1f + 0.1f * zoomSpeed)
            dolly(scale)
            update()
        }
    }

    fun rotateLeft(angleRadians: Float) {
        sphericalTheta -= angleRadians
    }

    fun rotateUp(angleRadians: Float) {
        sphericalPhi = MathUtils.clamp(sphericalPhi - angleRadians, minPolarAngle, maxPolarAngle)
    }

    fun dolly(scale: Float) {
        sphericalRadius = MathUtils.clamp(sphericalRadius * scale, minDistance, maxDistance)
    }

    fun pan(deltaX: Float, deltaY: Float) {
        target.x -= deltaX
        target.y += deltaY
    }

    fun update(deltaSeconds: Float = 0.016f): Boolean {
        if (!enabled) return false
        if (autoRotate && !isDraggingRotate) {
            rotateLeft(((2.0 * PI) / 60.0 * autoRotateSpeed * deltaSeconds).toFloat())
        }

        val sinPhiRadius = sin(sphericalPhi) * sphericalRadius
        camera.position.set(
            target.x + sinPhiRadius * sin(sphericalTheta),
            target.y + cos(sphericalPhi) * sphericalRadius,
            target.z + sinPhiRadius * cos(sphericalTheta)
        )
        camera.lookAt(target)
        camera.updateMatrixWorld()
        dispatchEvent("change")
        return true
    }

    private fun syncSphericalFromCamera() {
        val offset = Vector3().subVectors(camera.position, target)
        sphericalRadius = offset.length().coerceAtLeast(minDistance)
        sphericalTheta = atan2(offset.x, offset.z)
        sphericalPhi = MathUtils.clamp(
            kotlin.math.acos((offset.y / sphericalRadius).coerceIn(-1f, 1f)),
            minPolarAngle,
            maxPolarAngle
        )
    }
}
