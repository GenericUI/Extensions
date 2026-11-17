package com.nexustools.genericui.ext.threejs.core

import com.nexustools.genericui.ext.threejs.math.Euler
import com.nexustools.genericui.ext.threejs.math.MathUtils
import com.nexustools.genericui.ext.threejs.math.Matrix3
import com.nexustools.genericui.ext.threejs.math.Matrix4
import com.nexustools.genericui.ext.threejs.math.Quaternion
import com.nexustools.genericui.ext.threejs.math.Vector3

/**
 * Base class for all 3D scene-graph nodes (`THREE.Object3D`).
 */
open class Object3D : EventDispatcher() {
    val id: Long = MathUtils.nextObjectId()
    val uuid: String = MathUtils.generateUUID()
    var name: String = ""
    open val type: String = "Object3D"

    var parent: Object3D? = null
        internal set

    private val _children = mutableListOf<Object3D>()
    val children: List<Object3D>
        get() = _children

    val up: Vector3 = Vector3(0f, 1f, 0f)
    val position: Vector3 = Vector3()
    val rotation: Euler = Euler()
    val quaternion: Quaternion = Quaternion()
    val scale: Vector3 = Vector3(1f, 1f, 1f)

    val modelViewMatrix: Matrix4 = Matrix4()
    val normalMatrix: Matrix3 = Matrix3()
    val matrix: Matrix4 = Matrix4()
    val matrixWorld: Matrix4 = Matrix4()

    var matrixAutoUpdate: Boolean = true
    var matrixWorldNeedsUpdate: Boolean = false

    val layers: Layers = Layers()
    var visible: Boolean = true
    var castShadow: Boolean = false
    var receiveShadow: Boolean = false
    var frustumCulled: Boolean = true
    var renderOrder: Int = 0
    val userData: MutableMap<String, Any?> = mutableMapOf()

    fun add(vararg objects: Object3D): Object3D {
        for (obj in objects) {
            if (obj === this) continue
            obj.parent?.remove(obj)
            obj.parent = this
            _children.add(obj)
            obj.dispatchEvent("added")
        }
        return this
    }

    fun remove(vararg objects: Object3D): Object3D {
        for (obj in objects) {
            if (_children.remove(obj)) {
                obj.parent = null
                obj.dispatchEvent("removed")
            }
        }
        return this
    }

    fun clear(): Object3D {
        for (child in _children) {
            child.parent = null
            child.dispatchEvent("removed")
        }
        _children.clear()
        return this
    }

    fun getObjectByName(targetName: String): Object3D? {
        if (name == targetName) return this
        for (child in _children) {
            val found = child.getObjectByName(targetName)
            if (found != null) return found
        }
        return null
    }

    fun syncQuaternionFromEuler() {
        quaternion.setFromEuler(rotation)
    }

    fun syncEulerFromQuaternion() {
        rotation.setFromQuaternion(quaternion)
    }

    fun rotateX(angle: Float): Object3D {
        rotation.x += angle
        syncQuaternionFromEuler()
        return this
    }

    fun rotateY(angle: Float): Object3D {
        rotation.y += angle
        syncQuaternionFromEuler()
        return this
    }

    fun rotateZ(angle: Float): Object3D {
        rotation.z += angle
        syncQuaternionFromEuler()
        return this
    }

    fun translateX(distance: Float): Object3D {
        position.x += distance
        return this
    }

    fun translateY(distance: Float): Object3D {
        position.y += distance
        return this
    }

    fun translateZ(distance: Float): Object3D {
        position.z += distance
        return this
    }

    open fun lookAt(target: Vector3) {
        val m1 = Matrix4().lookAt(position, target, up)
        quaternion.setFromRotationMatrix(m1)
        syncEulerFromQuaternion()
    }

    fun lookAt(x: Float, y: Float, z: Float) = lookAt(Vector3(x, y, z))

    open fun updateMatrix() {
        matrix.compose(position, quaternion, scale)
        matrixWorldNeedsUpdate = true
    }

    open fun updateMatrixWorld(force: Boolean = false) {
        if (matrixAutoUpdate) updateMatrix()
        if (matrixWorldNeedsUpdate || force) {
            val p = parent
            if (p == null) {
                matrixWorld.copy(matrix)
            } else {
                matrixWorld.multiplyMatrices(p.matrixWorld, matrix)
            }
            matrixWorldNeedsUpdate = false
        }
        for (child in _children) {
            child.updateMatrixWorld(force = true)
        }
    }

    fun getWorldPosition(target: Vector3 = Vector3()): Vector3 {
        updateMatrixWorld(true)
        return target.setFromMatrixPosition(matrixWorld)
    }

    fun traverse(callback: (Object3D) -> Unit) {
        callback(this)
        for (child in _children) {
            child.traverse(callback)
        }
    }

    fun traverseVisible(callback: (Object3D) -> Unit) {
        if (!visible) return
        callback(this)
        for (child in _children) {
            child.traverseVisible(callback)
        }
    }
}
