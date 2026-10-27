package com.nexustools.genericui.ext.alien.dsl

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.ext.alien.AlienContainer
import com.nexustools.genericui.ext.alien.UnalienContainer
import com.nexustools.genericui.ext.dsl.ColorRgba
import com.nexustools.genericui.ext.dsl.bootstrap
import com.nexustools.genericui.ext.dsl.button
import com.nexustools.genericui.ext.dsl.h2
import com.nexustools.genericui.ext.dsl.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class AlienDslTest {

    @Test
    fun testAlienAndUnalienDslBuilders() {
        val root = Container()
        val alien = root.alienContainer(classes = "custom-alien", id = "main-alien") {
            style {
                backgroundColor = ColorRgba(18, 24, 38, 1.0)
            }
            bootstrap {
                padding = SpacingScale.THREE
                borderRadius = BorderRadiusStyle.LARGE
            }
            h2("Alien Interface")
            button("Alien Action", type = BootstrapType.PRIMARY)
            unalienContainer(classes = "custom-island", id = "native-island") {
                bootstrap {
                    marginTop = SpacingScale.TWO
                }
                button("Native OS Button", type = BootstrapType.SECONDARY)
            }
        }

        assertIs<AlienContainer>(alien)
        assertEquals("main-alien", alien.id)
        assertEquals(3, alien.children.size)
        assertEquals(ColorRgba(18, 24, 38, 1.0), alien.getStyle().getBackgroundColor())
        assertEquals(SpacingScale.THREE, alien.getBootstrap().getPadding())
        assertEquals(BorderRadiusStyle.LARGE, alien.getBootstrap().getBorderRadius())
        assertFalse(alien.hasClass("p-3"))

        val unalien = assertIs<UnalienContainer>(alien.children[2])
        assertEquals("native-island", unalien.id)
        assertEquals(1, unalien.children.size)
        assertEquals(SpacingScale.TWO, unalien.getBootstrap().getMarginTop())
        assertFalse(unalien.hasClass("mt-2"))
    }
}
