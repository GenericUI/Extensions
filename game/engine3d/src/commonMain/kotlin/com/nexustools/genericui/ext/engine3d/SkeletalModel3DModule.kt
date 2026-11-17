package com.nexustools.genericui.ext.engine3d

import com.nexustools.genericui.ext.engine.animation.AnimationCurve
import com.nexustools.genericui.ext.engine.animation.AnimationScheduler
import com.nexustools.genericui.ext.engine.animation.AnimationWrapMode
import com.nexustools.genericui.ext.engine.animation.EasingFunction
import com.nexustools.genericui.ext.engine.animation.LambdaAnimationClip
import com.nexustools.genericui.ext.engine.scene.EntityBehavior
import com.nexustools.genericui.ext.engine.scene.SceneEntity
import com.nexustools.genericui.ext.threejs.animation.AnimationClip
import com.nexustools.genericui.ext.threejs.animation.AnimationMixer
import com.nexustools.genericui.ext.threejs.core.Object3D
import com.nexustools.genericui.ext.threejs.objects.Bone
import com.nexustools.genericui.ext.threejs.objects.Skeleton
import com.nexustools.genericui.ext.threejs.objects.SkinnedMesh

/**
 * Drives a 3D rigged [SkinnedMesh] / [Skeleton] / [Object3D] model using both Three.js
 * [AnimationClip] / [AnimationMixer] tracks and VBlank-synchronized `0.0f..1.0f` lambda bone
 * animations registered on [AnimationScheduler].
 */
class SkeletalModel3DModule(
    val rootObject: Object3D,
    val skeleton: Skeleton? = (rootObject as? SkinnedMesh)?.skeleton,
    val clips: Map<String, AnimationClip> = emptyMap(),
    val proceduralRig: ProceduralHumanoidRig3D? = null
) : EntityBehavior() {

    constructor(rig: ProceduralHumanoidRig3D) : this(
        rootObject = rig.skinnedMesh,
        skeleton = rig.skeleton,
        clips = rig.clips,
        proceduralRig = rig
    )

    val mixer: AnimationMixer = AnimationMixer(rootObject)

    private val nodeBinding: Node3DBindingModule = Node3DBindingModule(rootObject)
    private val pendingBoneOverrides = mutableListOf<() -> Unit>()

    var activeClip: AnimationClip? = null
        private set
    var activeClipLoop: Boolean = true
    private var clipElapsedSeconds: Float = 0f

    override fun onAttach(owner: SceneEntity) {
        if (rootObject.name.isEmpty()) {
            rootObject.name = owner.name
        }
        nodeBinding.entity = owner
        nodeBinding.onAttach(owner)
    }

    override fun onDetach(owner: SceneEntity) {
        pendingBoneOverrides.clear()
        nodeBinding.onDetach(owner)
        nodeBinding.entity = null
    }

    fun getBone(name: String): Bone? =
        skeleton?.getBoneByName(name) ?: (rootObject.getObjectByName(name) as? Bone)

    fun playClip(name: String, loop: Boolean = true): Boolean {
        val clip = clips[name] ?: return false
        playClip(clip, loop)
        return true
    }

    fun playClip(clip: AnimationClip, loop: Boolean = true) {
        activeClip = clip
        activeClipLoop = loop
        clipElapsedSeconds = 0f
    }

    fun stopClip() {
        activeClip = null
        clipElapsedSeconds = 0f
        mixer.stopAllAction()
    }

    /**
     * Schedules a `0.0f..1.0f` lambda animation targeting [boneName] directly on [scheduler] so it
     * evaluates inside the VBlank rendering pass before GPU presentation.
     */
    fun animateBoneLambda(
        boneName: String,
        durationSeconds: Float,
        curve: AnimationCurve = EasingFunction.LINEAR,
        wrapMode: AnimationWrapMode = AnimationWrapMode.LOOP,
        scheduler: AnimationScheduler? = entity?.scene?.animations,
        onComplete: (() -> Unit)? = null,
        onProgress: (bone: Bone, progress: Float) -> Unit
    ): LambdaAnimationClip {
        val clip = LambdaAnimationClip(
            durationSeconds = durationSeconds,
            curve = curve,
            wrapMode = wrapMode,
            onComplete = onComplete
        ) { progress ->
            val attachedEntity = entity
            if (attachedEntity != null && attachedEntity.scene != null && attachedEntity.isActive && isEnabled) {
                pendingBoneOverrides.add {
                    val bone = getBone(boneName) ?: return@add
                    onProgress(bone, progress)
                    bone.syncQuaternionFromEuler()
                }
            } else {
                val bone = getBone(boneName) ?: return@LambdaAnimationClip
                onProgress(bone, progress)
                bone.syncQuaternionFromEuler()
                refreshSkeletonMatrices()
            }
        }
        scheduler?.play(clip)
        return clip
    }

    override fun onRenderVBlank(frameNumber: Long, deltaSeconds: Float, timestampMs: Double) {
        nodeBinding.syncFromEntity()
        val clip = activeClip
        if (clip != null && clip.duration > 0f) {
            clipElapsedSeconds += deltaSeconds.coerceAtLeast(0f)
            val sampleTime = if (activeClipLoop) {
                clipElapsedSeconds % clip.duration
            } else {
                clipElapsedSeconds.coerceAtMost(clip.duration)
            }
            evaluateBoneTracksAt(clip, sampleTime)
        }
        mixer.update(deltaSeconds)
        if (pendingBoneOverrides.isNotEmpty()) {
            val overrides = pendingBoneOverrides.toList()
            pendingBoneOverrides.clear()
            for (applyOverride in overrides) {
                applyOverride()
            }
        }
        refreshSkeletonMatrices()
    }

    fun evaluateBoneTracksAt(clip: AnimationClip, sampleTimeSeconds: Float) {
        for (track in clip.tracks) {
            val dotIdx = track.name.lastIndexOf('.')
            if (dotIdx <= 0) continue
            val targetName = track.name.substring(0, dotIdx)
            val propName = track.name.substring(dotIdx + 1)
            val node = getBone(targetName) ?: rootObject.getObjectByName(targetName) ?: continue
            val values = track.evaluateAt(sampleTimeSeconds)
            when (propName) {
                "quaternion" -> if (values.size >= 4) {
                    node.quaternion.set(values[0], values[1], values[2], values[3]).normalize()
                    node.syncEulerFromQuaternion()
                }
                "rotation" -> if (values.size >= 3) {
                    node.rotation.set(values[0], values[1], values[2])
                    node.syncQuaternionFromEuler()
                }
                "position" -> if (values.size >= 3) {
                    node.position.set(values[0], values[1], values[2])
                }
                "scale" -> if (values.size >= 3) {
                    node.scale.set(values[0], values[1], values[2])
                }
            }
        }
        refreshSkeletonMatrices()
    }

    fun refreshSkeletonMatrices() {
        rootObject.updateMatrixWorld(true)
        skeleton?.update()
    }
}
