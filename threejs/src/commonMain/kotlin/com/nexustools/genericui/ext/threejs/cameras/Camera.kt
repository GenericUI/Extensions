package com.nexustools.genericui.ext.threejs.cameras

import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.math.Matrix4
import com.nexustools.genericui.ext.threejs.math.Vector3

/**
 * Abstract base class for 3D cameras (`THREE.Camera`).
 */
open class Camera : Object3D() {
    override val type: String = "Camera"

    val matrixWorldInverse: Matrix4 = Matrix4()
    val projectionMatrix: Matrix4 = Matrix4()
    val projectionMatrixInverse: Matrix4 = Matrix4()

    override fun lookAt(target: Vector3) {
        val m1 = Matrix4().lookAt(position, target, up)
        quaternion.setFromRotationMatrix(m1)
        syncEulerFromQuaternion()
    }

    override fun updateMatrixWorld(force: Boolean) {
        super.updateMatrixWorld(force)
        matrixWorldInverse.copy(matrixWorld).invert()
    }

    open fun updateProjectionMatrix() {
        projectionMatrixInverse.copy(projectionMatrix).invert()
    }
}
