package dev.finnley.pcswitch.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für [NetworkWatchdog] (Design status-reading-design.md §7.1).
 *
 * Hand-rolled Fake-Source (kein MockK); Debounce-Zeit via kotlinx-coroutines-test
 * virtual time (Abhängigkeit bereits deklariert, kein neues Dependency).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkWatchdogTest {

    /**
     * Hand-rolled Fake: ruft den Listener synchron auf und zählt Register/Unregister.
     * Simuliert "Fake-Clock" durch runTest-Virtual-Time (advanceTimeBy).
     */
    private class FakeNetworkEventSource : NetworkEventSource {
        var listener: NetworkEventSource.Listener? = null
        var registerCount = 0
        var unregisterCount = 0

        override fun register(listener: NetworkEventSource.Listener) {
            registerCount++
            this.listener = listener
        }

        override fun unregister() {
            unregisterCount++
            listener = null
        }

        fun fireAvailable() = listener?.onNetworkAvailable()
        fun fireLost() = listener?.onLost()
        fun fireVpn(active: Boolean) = listener?.onVpnTransportChanged(active)
    }

    @Test
    fun vpn_capability_sets_transport_flag() = runTest {
        val fake = FakeNetworkEventSource()
        val wd = NetworkWatchdog(fake, backgroundScope)
        assertFalse(wd.vpnTransportActive.value)
        wd.start { }
        fake.fireVpn(true)
        assertTrue(wd.vpnTransportActive.value)
        wd.stop()
    }

    @Test
    fun vpn_capability_removed_clears_flag() = runTest {
        val fake = FakeNetworkEventSource()
        val wd = NetworkWatchdog(fake, backgroundScope)
        wd.start { }
        fake.fireVpn(true)
        assertTrue(wd.vpnTransportActive.value)
        fake.fireVpn(false)
        assertFalse(wd.vpnTransportActive.value)
        wd.stop()
    }

    @Test
    fun available_triggers_debounced_probe() = runTest {
        val fake = FakeNetworkEventSource()
        var probes = 0
        val wd = NetworkWatchdog(fake, backgroundScope)
        wd.start { probes++ }
        assertEquals(1, probes) // Initial-Probe bei start
        probes = 0
        fake.fireAvailable()
        advanceTimeBy(800) // > 750ms Debounce
        assertEquals(1, probes) // genau 1 debounced Probe
        wd.stop()
    }

    @Test
    fun burst_of_events_debounced_to_one() = runTest {
        val fake = FakeNetworkEventSource()
        var probes = 0
        val wd = NetworkWatchdog(fake, backgroundScope)
        wd.start { probes++ }
        probes = 0
        repeat(5) {
            fake.fireAvailable()
            advanceTimeBy(100) // 5 Events in 500 ms
        }
        advanceTimeBy(750) // Debounce-Frist nach letztem Event
        assertEquals(1, probes) // Burst -> genau 1 Probe
        wd.stop()
    }

    @Test
    fun stop_unregisters_source_and_cancels_pending() = runTest {
        val fake = FakeNetworkEventSource()
        var probes = 0
        val wd = NetworkWatchdog(fake, backgroundScope)
        wd.start { probes++ }
        probes = 0
        fake.fireAvailable() // pending Debounce-Job läuft
        wd.stop()
        advanceTimeBy(2_000)
        assertEquals(0, probes) // kein Probe-Call mehr nach stop
        assertEquals(1, fake.registerCount)
        assertEquals(1, fake.unregisterCount) // Source deregistriert
    }

    @Test
    fun start_fires_initial_probe() = runTest {
        val fake = FakeNetworkEventSource()
        var probes = 0
        val wd = NetworkWatchdog(fake, backgroundScope)
        wd.start { probes++ }
        assertEquals(1, probes) // Initial-Probe sofort, kein Warten auf Event
        assertEquals(1, fake.registerCount)
        wd.stop()
    }
}
