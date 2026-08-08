package dev.freshleaf.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteLabelsTest {
    @Test
    fun folderDescendantsIncludeNestedSubfolders() {
        val folders = listOf(
            LocalFolderEntity(id = 1, name = "Root"),
            LocalFolderEntity(id = 2, name = "Child", parentId = 1),
            LocalFolderEntity(id = 3, name = "Grandchild", parentId = 2),
            LocalFolderEntity(id = 4, name = "Elsewhere"),
        )
        assertEquals(listOf(1L, 2L, 3L), dev.freshleaf.reader.ui.descendantFolderIds(1, folders))
    }

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
