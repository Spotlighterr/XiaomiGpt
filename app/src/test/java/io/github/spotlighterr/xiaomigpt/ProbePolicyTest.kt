package io.github.spotlighterr.xiaomigpt

import org.junit.Assert.*
import org.junit.Test

class ProbePolicyTest {
    @Test fun `only literal private IPv4 hosts are accepted`() {
        listOf("192.168.43.1", "10.1.2.3", "172.16.0.1", "172.31.200.2").forEach { assertTrue(it, ProbePolicy.isPrivateHost(it)) }
        listOf("example.com", "127.0.0.1", "8.8.8.8", "169.254.1.1", "172.15.0.1", "172.32.0.1",
            "http://192.168.43.1", "192.168.43.1:8080", "192.168.43.1/x", "192.168.43.256", "192.168.043.1",
            "192.168.43.0", "192.168.43.255", "::1", "").forEach { assertFalse(it, ProbePolicy.isPrivateHost(it)) }
    }

    @Test fun `host must be on the chosen WiFi subnet`() {
        assertTrue(ProbePolicy.sameSubnet("192.168.43.1", "192.168.43.20", 24))
        assertFalse(ProbePolicy.sameSubnet("192.168.44.1", "192.168.43.20", 24))
        assertTrue(ProbePolicy.sameSubnet("10.1.2.3", "10.50.60.70", 8))
        assertFalse(ProbePolicy.sameSubnet("192.168.43.1", "192.168.43.20", 0))
        assertFalse(ProbePolicy.sameSubnet("192.168.43.1", "::1", 24))
        assertTrue(ProbePolicy.sameSubnet("10.1.2.3", "10.1.2.3", 32))
        assertFalse(ProbePolicy.sameSubnet("10.1.2.3", "10.1.2.4", 32))
    }

    @Test fun `a previous cameras photo cannot be confirmed for another camera`() {
        assertFalse(ProbePolicy.canConfirmPhoto(null, "0"))
        assertFalse(ProbePolicy.canConfirmPhoto("0", "1"))
        assertTrue(ProbePolicy.canConfirmPhoto("0", "0"))
    }
}
