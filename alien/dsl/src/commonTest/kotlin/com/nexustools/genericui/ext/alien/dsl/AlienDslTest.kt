package com.nexustools.genericui.ext.alien.dsl

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.ext.alien.AlienContainer
import com.nexustools.genericui.ext.alien.UnalienContainer
import com.nexustools.genericui.ext.dsl.button
import com.nexustools.genericui.ext.dsl.h2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AlienDslTest {

    @Test
    fun testAlienAndUnalienDslBuilders() {
        val root = Container()
        val alien = root.alienContainer(classes = "p-3", id = "main-alien") {
            h2("Alien Interface")
            button("Alien Action", type = BootstrapType.PRIMARY)
            unalienContainer(classes = "mt-2", id = "native-island") {
                button("Native OS Button", type = BootstrapType.SECONDARY)
            }
        }

        assertIs<AlienContainer>(alien)
        assertEquals("main-alien", alien.id)
        assertEquals(3, alien.children.size)

        val unalien = assertIs<UnalienContainer>(alien.children[2])
        assertEquals("native-island", unalien.id)
        assertEquals(1, unalien.children.size)
    }
}
