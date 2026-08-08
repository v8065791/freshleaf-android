package dev.freshleaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class TailscaleNetworkTest {
    @Test fun classifiesMagicDnsHostsCaseInsensitively() {
        assertTrue(isTailscaleHostname("helios.tail5460cf.ts.net"))
        assertTrue(isTailscaleHostname("HELIOS.TAIL5460CF.TS.NET."))
        assertFalse(isTailscaleHostname("rss.example.net"))
    }

    @Test fun normalHostsDoNotUseTailscaleHandling() {
        assertFalse(isTailscaleEndpoint("https://rss.example.net"))
    }

    @Test fun turnsAnActualMagicDnsFailureIntoActionableTailscaleGuidance() {
        val failure = connectionFailure("helios.tail5460cf.ts.net", IOException("host not found"))
        assertTrue(failure is TailscaleConnectionException)
        assertTrue(failure.message!!.contains("app-based split tunneling"))
    }

    @Test fun keepsNormalHostNetworkFailuresGeneric() {
        val failure = connectionFailure("rss.example.net", IOException("host not found"))
        assertFalse(failure is TailscaleConnectionException)
        assertTrue(failure.message!!.startsWith("Unable to reach FreshRSS:"))
    }

    @Test fun keepsClientLoginSeparateFromAuthenticatedApiRoutes() {
        val endpoint = normalizeFreshRssEndpoint("https://helios.tail5460cf.ts.net")
        assertEquals("https://helios.tail5460cf.ts.net/api/greader.php/accounts/ClientLogin", freshRssClientLoginUrl(endpoint).toString())
        assertEquals("https://helios.tail5460cf.ts.net/api/greader.php/reader/api/0/tag/list", freshRssApiUrl(endpoint, "tag/list").toString())
    }

    @Test(expected = FreshRssException::class)
    fun rejectsHttpEndpointsBeforeLogin() { normalizeFreshRssEndpoint("http://rss.example.net") }
}
