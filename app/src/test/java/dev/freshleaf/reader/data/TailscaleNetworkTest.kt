package dev.freshleaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TailscaleNetworkTest {
    @Test fun classifiesMagicDnsHostsCaseInsensitively() {
        assertTrue(isTailscaleHostname("helios.tail5460cf.ts.net"))
        assertTrue(isTailscaleHostname("HELIOS.TAIL5460CF.TS.NET."))
        assertFalse(isTailscaleHostname("rss.example.net"))
    }

    @Test fun normalHostsKeepTheSystemClient() {
        assertEquals(EndpointNetworkMode.SYSTEM, endpointNetworkMode("rss.example.net"))
        assertFalse(isTailscaleEndpoint("https://rss.example.net"))
    }

    @Test fun selectsTheFirstActiveVpnThatCanResolveTheMagicDnsHost() {
        val selected = selectResolvingVpn("helios.tail5460cf.ts.net", listOf(
            VpnNetworkCandidate("wifi", false), VpnNetworkCandidate("unrelated-vpn", true), VpnNetworkCandidate("tailscale", true),
        )) { network, _ -> if (network == "tailscale") listOf("100.64.0.10") else emptyList() }
        assertEquals("tailscale", selected)
    }

    @Test fun noResolvingVpnProducesNoCandidate() {
        val selected = selectResolvingVpn("helios.tail5460cf.ts.net", listOf(VpnNetworkCandidate("wifi", false))) { _, _ -> emptyList<String>() }
        assertNull(selected)
        assertTrue(TailscaleConnectionException().message!!.contains("app-based split tunneling"))
    }

    @Test fun keepsClientLoginSeparateFromAuthenticatedApiRoutes() {
        val endpoint = normalizeFreshRssEndpoint("https://helios.tail5460cf.ts.net")
        assertEquals("https://helios.tail5460cf.ts.net/api/greader.php/accounts/ClientLogin", freshRssClientLoginUrl(endpoint).toString())
        assertEquals("https://helios.tail5460cf.ts.net/api/greader.php/reader/api/0/tag/list", freshRssApiUrl(endpoint, "tag/list").toString())
    }

    @Test(expected = FreshRssException::class)
    fun rejectsHttpEndpointsBeforeLogin() { normalizeFreshRssEndpoint("http://rss.example.net") }
}
