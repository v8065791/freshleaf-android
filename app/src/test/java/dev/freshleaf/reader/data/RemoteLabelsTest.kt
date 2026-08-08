package dev.freshleaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteLabelsTest {
    @Test
    fun endpointWithoutApiPathGetsReaderApiPath() {
        assertEquals("https://rss.example/api/greader.php", normalizeFreshRssEndpoint("https://rss.example/"))
    }

    @Test
    fun existingApiPathIsNotDuplicated() {
        assertEquals("https://rss.example/api/greader.php", normalizeFreshRssEndpoint("https://rss.example/api/greader.php"))
    }

    @Test
    fun serializedLabelsAreMatchedAsCompleteIds() {
        val labels = "user/u/label/tech\u001fuser/u/label/android"
        assertTrue(containsRemoteLabel(labels, "user/u/label/android"))
        assertFalse(containsRemoteLabel(labels, "user/u/label/and"))
    }
}

