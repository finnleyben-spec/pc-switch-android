package dev.finnley.pcswitch.state

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [30])
@RunWith(RobolectricTestRunner::class)
class SwitchManagerPersistenceTest {

    private lateinit var context: Context
    private lateinit var manager: SwitchManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = SwitchManager(context)
    }

    @After
    fun tearDown() = runTest {
        manager.saveSwitches(emptyList())
    }

    @Test
    fun save_and_load_switches() = runTest {
        val switches = listOf(
            SwitchConfig(
                id = "pc",
                name = "PC",
                type = "dual",
                primaryEntity = "switch.pc_power",
                secondaryEntity = "switch.pc_off"
            ),
            SwitchConfig(
                id = "lamp",
                name = "Lamp",
                type = "toggle",
                primaryEntity = "light.living_room",
                secondaryEntity = ""
            )
        )

        manager.saveSwitches(switches)

        val loaded = manager.getSwitches()
        assertEquals(2, loaded.size)
        assertEquals("pc", loaded[0].id)
        assertEquals("lamp", loaded[1].id)
        assertEquals("dual", loaded[0].type)
        assertEquals("toggle", loaded[1].type)
    }

    @Test
    fun load_returns_empty_when_nothing_saved() = runTest {
        val loaded = manager.getSwitches()
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun add_switch_persists() = runTest {
        val id = manager.addSwitch("PC", "dual", "switch.pc_power", "switch.pc_off")

        val loaded = manager.getSwitches()
        assertEquals(1, loaded.size)
        assertEquals(id, loaded[0].id)
        assertEquals("PC", loaded[0].name)
        assertEquals("dual", loaded[0].type)
    }

    @Test
    fun remove_switch_persists() = runTest {
        val pcId = manager.addSwitch("PC", "dual", "switch.pc_power", "switch.pc_off")
        val lampId = manager.addSwitch("Lamp", "toggle", "light.living_room")

        manager.removeSwitch(lampId)

        val loaded = manager.getSwitches()
        assertEquals(1, loaded.size)
        assertEquals(pcId, loaded[0].id)
    }

    @Test
    fun reorder_switches_persists() = runTest {
        manager.addSwitch("PC", "dual", "switch.pc_power", "switch.pc_off")
        manager.addSwitch("Lamp", "toggle", "light.living_room")

        manager.reorderSwitches(1, 0)

        val loaded = manager.getSwitches()
        assertEquals("Lamp", loaded[0].name)
        assertEquals("PC", loaded[1].name)
    }

    @Test
    fun update_switch_persists() = runTest {
        manager.addSwitch("Old PC", "dual", "switch.old_power", "switch.old_off")

        val switches = manager.getSwitches()
        val updated = switches[0].copy(name = "New PC", primaryEntity = "switch.pc_power")
        manager.updateSwitch(updated)

        val loaded = manager.getSwitches()
        assertEquals(1, loaded.size)
        assertEquals("New PC", loaded[0].name)
        assertEquals("switch.pc_power", loaded[0].primaryEntity)
    }

    @Test
    fun multiple_adds_preserve_order() = runTest {
        manager.addSwitch("First", "toggle", "light.first")
        manager.addSwitch("Second", "toggle", "light.second")
        manager.addSwitch("Third", "toggle", "light.third")

        val loaded = manager.getSwitches()
        assertEquals(3, loaded.size)
        assertEquals("First", loaded[0].name)
        assertEquals("Second", loaded[1].name)
        assertEquals("Third", loaded[2].name)
    }
}