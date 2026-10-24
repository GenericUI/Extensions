package com.nexustools.genericui.ext.threejs.controls

import com.nexustools.genericui.component.media.AbstractCanvas
import com.nexustools.genericui.ext.threejs.cameras.Camera
import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.math.Vector2

/**
 * Interactive 3D gizmo controller (`THREE.TransformControls`) for translating, rotating,
 * and scaling an attached [Object3D] inside a viewport.
 */
class TransformControls(
    val camera: Camera,
    canvas: AbstractCanvas<*>? = null
) : Object3D() {
    override val type: String = "TransformControls"

    var controlledObject: Object3D? = null
        private set

    var mode: String = "translate" // "translate" | "rotate" | "scale"
    var space: String = "world"    // "world" | "local"
    var axis: String? = "XYZ"
    var translationSnap: Float? = null
    var rotationSnap: Float? = null
    var scaleSnap: Float? = null
    var enabled: Boolean = true
    var dragging: Boolean = false
        private set

    private val lastPointer = Vector2()

    init {
        if (canvas != null) {
            attachToCanvas(canvas)
        }
    }

    fun attach(object3D: Object3D): TransformControls {
        controlledObject = object3D
        visible = true
        return this
    }

    fun detach(): TransformControls {
        controlledObject = null
        visible = false
        return this
    }

    fun attachToCanvas(canvas: AbstractCanvas<*>) {
        canvas.setOnPointerDown { event ->
            if (!enabled || controlledObject == null) return@setOnPointerDown
            lastPointer.set(event.x, event.y)
            dragging = true
            dispatchEvent("mouseDown", mapOf("mode" to mode))
        }

        canvas.setOnPointerMove { event ->
            val obj = controlledObject ?: return@setOnPointerMove
            if (!enabled || !dragging || event.buttonsMask == 0) return@setOnPointerMove
            val dx = (event.x - lastPointer.x) * 0.01f
            val dy = -(event.y - lastPointer.y) * 0.01f
            lastPointer.set(event.x, event.y)

            when (mode) {
                "translate" -> {
                    obj.position.x += dx
                    obj.position.y += dy
                }
                "rotate" -> {
                    obj.rotateY(dx)
                    obj.rotateX(dy)
                }
                "scale" -> {
                    val factor = (1f + dx + dy).coerceAtLeast(0.05f)
                    obj.scale.multiplyScalar(factor)
                }
            }
            obj.updateMatrixWorld(true)
            dispatchEvent("objectChange")
            dispatchEvent("change")
        }

        canvas.setOnPointerUp {
            if (dragging) {
                dragging = false
                dispatchEvent("mouseUp", mapOf("mode" to mode))
            }
        }
    }
}
