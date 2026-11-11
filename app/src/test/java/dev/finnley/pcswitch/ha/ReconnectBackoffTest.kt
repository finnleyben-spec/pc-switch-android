package dev.finnley.pcswitch.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectBackoffTest {

    @Test
    fun first_delay_between_1s_and_1_5s() {
        for (i in 0 until 20) {
            val b = ReconnectBackoff()
            val d = b.nextDelayMs()
            assertTrue("attempt=$i delay=$d", d in 1_000L..1_500L)
        }
    }

    @Test
    fun sequence_with_fixed_random_0_5() {
        val b = ReconnectBackoff(random = { 0.5 })
        val seq = (0..4).map { b.nextDelayMs() }
        // Formel: delay = scaled + (scaled/2)·random → bei random=0.5: 1.25·scaled
        assertEquals(listOf(1_250L, 2_500L, 5_000L, 10_000L, 20_000L), seq)
    }

    @Test
    fun never_exceeds_cap() {
        val b = ReconnectBackoff(random = { 0.99 })
        repeat(30) {
            assertTrue(b.nextDelayMs() <= 30_000L)
        }
    }

    @Test
    fun reset_returns_to_base_range() {
        val b = ReconnectBackoff(random = { 0.5 })
        repeat(5) { b.nextDelayMs() }   // → bereits 48s-Territorium (gecappt bei 30s)
        b.reset()
        assertTrue(b.nextDelayMs() in 1_000L..1_500L)
    }

    @Test
    fun overflow_attempt_stays_capped() {
        val b = ReconnectBackoff(random = { 0.99 })
        repeat(40) { b.nextDelayMs() }
        assertTrue(b.nextDelayMs() <= 30_000L)
    }
}
