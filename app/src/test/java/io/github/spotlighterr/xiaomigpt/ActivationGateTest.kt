package io.github.spotlighterr.xiaomigpt

import org.junit.Assert.*
import org.junit.Test

class ActivationGateTest {
    @Test fun `duplicate deliveries cannot toggle the same turn twice`() {
        val gate = ActivationGate()
        assertTrue(gate.accept(1000))
        assertFalse(gate.accept(1001))
        assertFalse(gate.accept(1300))
        assertFalse(gate.accept(1749))
        assertTrue(gate.accept(1750))
    }
    @Test fun `independent later presses are accepted`() {
        val gate = ActivationGate()
        assertTrue(gate.accept(0))
        assertTrue(gate.accept(2000))
        assertTrue(gate.accept(4000))
    }
}
