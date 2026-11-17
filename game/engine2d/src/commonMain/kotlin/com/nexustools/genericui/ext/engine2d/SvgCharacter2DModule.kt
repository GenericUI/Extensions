package com.nexustools.genericui.ext.engine2d

import com.nexustools.genericui.ext.engine.scene.EntityBehavior
import com.nexustools.genericui.ext.engine.svg.HumanoidSkeleton2DFactory
import com.nexustools.genericui.ext.engine.svg.SkeletalAnimator2D
import com.nexustools.genericui.ext.engine.svg.Skeleton2D
import com.nexustools.genericui.ext.imageprocessing.context2d.ImageBuffer2DContext

/**
 * Attaches an animated 2D SVG bone hierarchy ([skeleton] + [animator]) to a [SceneEntity],
 * evaluating poses on VBlank and drawing via [SvgCharacter2DRenderer].
 */
class SvgCharacter2DModule(
    val skeleton: Skeleton2D = HumanoidSkeleton2DFactory.create(),
    var zOrder: Int = 0,
    var drawBoneOverlay: Boolean = false
) : EntityBehavior() {

    val animator: SkeletalAnimator2D = SkeletalAnimator2D(skeleton)
    var driveWithScheduler: Boolean = false

    override fun onRenderVBlank(frameNumber: Long, deltaSeconds: Float, timestampMs: Double) {
        if (!driveWithScheduler && animator.activeClip != null) {
            animator.stepTime(deltaSeconds)
        }
    }

    fun render(ctx: ImageBuffer2DContext) {
        if (!isEnabled) return
        val m = entity?.worldAffine2D() ?: return
        ctx.save()
        ctx.transform(m.a, m.b, m.c, m.d, m.tx, m.ty)
        SvgCharacter2DRenderer.render(skeleton, ctx, drawBoneOverlay = drawBoneOverlay)
        ctx.restore()
    }
}
