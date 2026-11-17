package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.engine.math.Vec3f
import com.nexustools.genericui.ext.engine.scene.EntityBehavior
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.threejs.cameras.PerspectiveCamera
import kotlin.math.exp

/**
 * Smooth third-person / chase camera module following a [target] entity with configurable [offset],
 * [lookAtOffset], and exponential [damping].
 */
class FollowCamera3DModule(
    val camera: PerspectiveCamera = PerspectiveCamera(50f, 800f / 600f, 0.1f, 2000f),
    var target: SceneEntity? = null,
    var offset: Vec3f = Vec3f(0f, 3f, 6f),
    var lookAtOffset: Vec3f = Vec3f(0f, 1f, 0f),
    var damping: Float = 8f
) : EntityBehavior() {

    override fun onAttach(owner: SceneEntity) {
        snapToTarget()
        owner.scene?.getSubsystem<SceneGraph3DSubsystem>()?.attachObject(camera)
    }

    fun snapToTarget() {
        val owner = entity ?: return
        val t = target ?: return
        val desired = t.transform.position + offset
        owner.transform.position.copyFrom(desired)
        syncCameraPose(t.transform.position + lookAtOffset)
    }

    override fun onLateTick(deltaSeconds: Float) {
        updateFollow(deltaSeconds)
    }

    override fun onRenderVBlank(frameNumber: Long, deltaSeconds: Float, timestampMs: Double) {
        updateFollow(deltaSeconds)
    }

    fun updateFollow(deltaSeconds: Float) {
        val owner = entity ?: return
        val t = target ?: return
        val desired = t.transform.position + offset
        val alpha = if (damping <= 0f) 1f else (1f - exp(-damping * deltaSeconds.coerceAtLeast(0f)))
        val next = owner.transform.position.lerp(desired, alpha)
        owner.transform.position.copyFrom(next)
        syncCameraPose(t.transform.position + lookAtOffset)
    }

    private fun syncCameraPose(lookTarget: Vec3f) {
        val pos = entity?.transform?.position ?: return
        camera.position.set(pos.x, pos.y, pos.z)
        camera.lookAt(lookTarget.x, lookTarget.y, lookTarget.z)
        camera.updateMatrixWorld()
    }
}
