package dev.finnley.pcswitch.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für PcSwitchViewModel.shouldNotify (Notification Templates).
 * Reine Funktion -> testbar auf der JVM ohne Android-Runtime.
 */
class NotificationTemplatesTest {

    @Test
    fun disabled_never_notifies() {
        assertFalse(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = false,
                notificationMode = "all",
                isPcSwitch = true,
                pcToggleState = "booting",
                newState = "on"
            )
        )
        assertFalse(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = false,
                notificationMode = "all",
                isPcSwitch = false,
                pcToggleState = "idle",
                newState = "on"
            )
        )
    }

    @Test
    fun non_pc_switch_always_notifies() {
        assertTrue(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "boot_only",
                isPcSwitch = false,
                pcToggleState = "idle",
                newState = "on"
            )
        )
        assertTrue(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "shutdown_only",
                isPcSwitch = false,
                pcToggleState = "idle",
                newState = "off"
            )
        )
    }

    @Test
    fun mode_all_notifies_everything() {
        assertTrue(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "all",
                isPcSwitch = true,
                pcToggleState = "booting",
                newState = "on"
            )
        )
        assertTrue(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "all",
                isPcSwitch = true,
                pcToggleState = "shutting_down",
                newState = "off"
            )
        )
        assertTrue(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "all",
                isPcSwitch = true,
                pcToggleState = "idle",
                newState = "on"
            )
        )
    }

    @Test
    fun mode_boot_only() {
        // Only booting PC switches notify
        assertTrue(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "boot_only",
                isPcSwitch = true,
                pcToggleState = "booting",
                newState = "on"
            )
        )
        // Shutting down PC switch: no notification
        assertFalse(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "boot_only",
                isPcSwitch = true,
                pcToggleState = "shutting_down",
                newState = "off"
            )
        )
        // PC switch during idle: no notification
        assertFalse(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "boot_only",
                isPcSwitch = true,
                pcToggleState = "idle",
                newState = "on"
            )
        )
        // Booting but state is off: no notification
        assertFalse(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "boot_only",
                isPcSwitch = true,
                pcToggleState = "booting",
                newState = "off"
            )
        )
    }

    @Test
    fun mode_shutdown_only() {
        // Only shutting down PC switches notify
        assertTrue(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "shutdown_only",
                isPcSwitch = true,
                pcToggleState = "shutting_down",
                newState = "off"
            )
        )
        // Booting PC switch: no notification
        assertFalse(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "shutdown_only",
                isPcSwitch = true,
                pcToggleState = "booting",
                newState = "on"
            )
        )
        // PC switch during idle: no notification
        assertFalse(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "shutdown_only",
                isPcSwitch = true,
                pcToggleState = "idle",
                newState = "off"
            )
        )
        // Shutting down but state is on: no notification
        assertFalse(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "shutdown_only",
                isPcSwitch = true,
                pcToggleState = "shutting_down",
                newState = "on"
            )
        )
    }

    @Test
    fun unknown_mode_treated_as_all() {
        assertTrue(
            PcSwitchViewModel.shouldNotify(
                notificationEnabled = true,
                notificationMode = "unknown",
                isPcSwitch = true,
                pcToggleState = "booting",
                newState = "on"
            )
        )
    }
}
