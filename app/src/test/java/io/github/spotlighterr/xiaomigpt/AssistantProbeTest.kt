package io.github.spotlighterr.xiaomigpt

import org.junit.Assert.*
import org.junit.Test

class AssistantProbeTest {
    @Test fun `service accepting request is not session success`() {
        val started = AssistantProbe().start()
        val requested = started.serviceResult(started.request, AssistantProbe.REQUESTED)
        assertTrue(requested.pending)
        assertEquals(AssistantProbe.Phase.SESSION_REQUESTED, requested.phase)
        assertEquals(AssistantProbe.Phase.TIMED_OUT, requested.expire(started.request).phase)
    }
    @Test fun `late replies and timers from earlier test cannot affect newer test`() {
        val first = AssistantProbe().start()
        val second = first.start()
        assertEquals(second, second.serviceResult(first.request, AssistantProbe.FAILED))
        assertEquals(second, second.expire(first.request))
    }
    @Test fun `opened session cannot be overwritten by late service failure`() {
        val started = AssistantProbe().start()
        val opened = started.opened()
        assertEquals(AssistantProbe.Phase.SESSION_OPENED, opened.phase)
        assertEquals(opened, opened.serviceResult(started.request, AssistantProbe.FAILED))
        assertEquals(opened, opened.expire(started.request))
    }
    @Test fun `failed or timed out tests cannot revive from stale acknowledgements`() {
        val started = AssistantProbe().start()
        for (failed in listOf(started.expire(started.request), started.serviceResult(started.request, AssistantProbe.NOT_ACTIVE))) {
            assertFalse(failed.pending)
            assertEquals(failed, failed.serviceResult(started.request, AssistantProbe.REQUESTED))
            assertEquals(failed, failed.opened())
        }
    }
}
