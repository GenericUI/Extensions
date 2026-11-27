package com.nexustools.genericui.ext.engine.ui

import com.nexustools.genericui.component.content.VectorGraphicView
import com.nexustools.genericui.component.layout.styled.StyledContainer
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.ext.engine.animation.EasingFunction
import com.nexustools.genericui.ext.engine.math.Vec2f
import com.nexustools.genericui.ext.engine.scene.EntityBehavior
import com.nexustools.genericui.ext.engine.svg.HumanoidSkeleton2DFactory
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiStageHostDirectBindingTest {

    @Test
    fun pureEngineDrivesCommonUiComponentsAndSvgCharacterWithZeroCanvas() {
        val host = UiStageHost()
        val playerBox = StyledContainer().apply {
            widthPx = 48
            heightPx = 48
        }
        val scoreLabel = Label("Score: 0")
        val heroSvg = VectorGraphicView()
        val skeleton = HumanoidSkeleton2DFactory.create(rootOffsetX = 64f, rootOffsetY = 80f)

        val playerEntity = host.attachComponentEntity("PlayerBox", playerBox, x = 20f, y = 40f, widthPx = 48, heightPx = 48)
        playerEntity.getModule<UiElementBindingModule>()?.backgroundColor = RgbaColor(59, 130, 246, 255)
        playerEntity.addModule(object : EntityBehavior() {
            override fun onTick(deltaSeconds: Float) {
                entity?.transform?.translate2D(100f * deltaSeconds, 0f)
            }
        })

        val labelEntity = host.attachComponentEntity("HudLabel", scoreLabel, x = 10f, y = 10f)
        val svgEntity = host.attachComponentEntity("HeroSvg", heroSvg, x = 100f, y = 60f, widthPx = 128, heightPx = 128)
        svgEntity.getModule<UiElementBindingModule>()?.skeleton2D = skeleton

        var animatedScale = 1f
        host.scene.animations.playLambda(
            durationSeconds = 1.0f,
            curve = EasingFunction.QUAD_OUT
        ) { progress ->
            animatedScale = 1f + progress
            playerEntity.transform.setScale2D(animatedScale, animatedScale)
        }

        // Simulate Game Logic ticks + VBlank render evaluation directly without any canvas extension
        for (i in 0 until 5) {
            host.scheduler.stepManualTick(0.1f)
        }
        assertEquals(70f, playerEntity.transform.position.x, 1e-3f)
        assertEquals(70f, playerBox.getStyle().left?.value ?: -1f, 1e-3f)
        assertNotNull(labelEntity)
        assertTrue(heroSvg.sourceUrl?.startsWith("data:image/svg+xml;utf8,") == true)

        host.stepVBlankFrame(1L, 0.0)
        host.stepVBlankFrame(2L, 500.0)
        assertTrue(animatedScale > 1.5f)
        assertEquals(animatedScale, playerBox.getStyle().transform?.scaleX ?: 0f, 1e-3f)

        // Verify pure-UI 2D collision detection
        assertTrue(UiCollision2DHelper.aabbOverlapEntities(playerEntity, Vec2f(48f, 48f), svgEntity, Vec2f(64f, 64f)))
        assertFalse(UiCollision2DHelper.aabbOverlap(0f, 0f, 10f, 10f, 100f, 100f, 10f, 10f))
        val mtv = UiCollision2DHelper.computeAabbPenetration(0f, 0f, 20f, 20f, 15f, 0f, 20f, 20f)
        assertNotNull(mtv)
        assertEquals(-5f, mtv.x, 1e-4f)

        host.dispose()
    }
}
