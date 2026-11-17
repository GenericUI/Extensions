package com.nexustools.genericui.ext.engine2d.dsl

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.ext.box2d.BodyType2D
import com.nexustools.genericui.ext.engine.scene.FixedStepAccumulator
import com.nexustools.genericui.ext.gamepad.GamepadButton
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Game2DDslTest {
    @Test
    fun game2DCanvasDslComposesPhysicsSpritesSvgCharactersParticlesTilemapAndBindings() {
        val root = Container()
        var progressObserved = 0f

        val host = root.game2DCanvas(width = 320, height = 240, autoStart = false) {
            clearColor = RgbaColor(20, 20, 30, 255)
            physics2D(debugDraw = true)
            gamepadBindings {
                button("dash", GamepadButton.B)
            }
            animateLambda(durationSeconds = 0.5f) { p ->
                progressObserved = p
            }
            entity("Cam") {
                camera2D(viewportWidth = 320f, viewportHeight = 240f, zoom = 1.5f)
            }
            entity("Player") {
                position2D(100f, 120f)
                rigidBody2D(BodyType2D.DYNAMIC)
                boxCollider2D(width = 24f, height = 40f)
                circleCollider2D(radius = 8f, isSensor = true)
                svgCharacter2D(drawBoneOverlay = true)
                particles2D {
                    emitBurst(4)
                }
                tilemap2D(columns = 2, rows = 2) {
                    setTile(0, 1, 7)
                }
            }
        }

        host.scene.stepLogicTick(1f / 60f, FixedStepAccumulator(1f / 60f))
        host.stepVBlank(0.0)
        host.stepVBlank(250.0)

        val player = host.scene.findEntityByName("Player")
        assertNotNull(player)
        assertNotNull(player.getModule<com.nexustools.genericui.ext.engine2d.SvgCharacter2DModule>())
        assertEquals(7, player.getModule<com.nexustools.genericui.ext.engine2d.Tilemap2DModule>()?.getTile(0, 1))
        assertEquals(4, player.getModule<com.nexustools.genericui.ext.engine2d.ParticleEmitter2DModule>()?.activeParticleCount)
        assertTrue(progressObserved > 0.4f)
        host.dispose()
    }
}
