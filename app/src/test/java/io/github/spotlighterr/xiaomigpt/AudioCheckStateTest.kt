package io.github.spotlighterr.xiaomigpt

import org.junit.Assert.*
import org.junit.Test

class AudioCheckStateTest {
    private fun completed() = AudioCheckState()
        .on(AudioProbeEvent.Connecting)
        .on(AudioProbeEvent.Recording(7))
        .on(AudioProbeEvent.Playback(1234))
        .on(AudioProbeEvent.Completed(7, 7, 1234))

    @Test fun `Bluetooth setup alone cannot confirm microphone success`() {
        val recording = AudioCheckState().on(AudioProbeEvent.Connecting).on(AudioProbeEvent.Recording(7))
        assertFalse(recording.routeVerified)
        assertFalse(recording.confirmHeard(true).heardPlayback)
    }

    @Test fun `route verification and hearing the playback are separate evidence`() {
        val result = completed()
        assertTrue(result.routeVerified)
        assertFalse(result.heardPlayback)
        assertTrue(result.confirmHeard(true).heardPlayback)
    }

    @Test fun `a new or failed attempt does not reuse a previous confirmation`() {
        val previous = completed().confirmHeard(true)
        assertFalse(previous.on(AudioProbeEvent.Connecting).heardPlayback)
        val failed = previous.on(AudioProbeEvent.Failed("Disconnected"))
        assertFalse(failed.routeVerified)
        assertFalse(failed.heardPlayback)
    }

    @Test fun `completion received after cancellation cannot turn the test green`() {
        val cancelled = AudioCheckState().on(AudioProbeEvent.Playback(1234))
            .on(AudioProbeEvent.Cancelled).on(AudioProbeEvent.Completed(7, 7, 1234))
        assertEquals(AudioCheckState.Phase.CANCELLED, cancelled.phase)
        assertFalse(cancelled.routeVerified)
    }
}
