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

    @Test
    fun subscriptionCategoryChangesPreserveSharedAssignments() {
        val changes = subscriptionCategoryChanges(
            currentIds = setOf("user/u/label/Tech", "user/u/label/News"),
            selectedIds = setOf("user/u/label/News", "user/u/label/Android", "user/u/label/Video"),
        )
        assertEquals(setOf("user/u/label/Android", "user/u/label/Video"), changes.add)
        assertEquals(setOf("user/u/label/Tech"), changes.remove)
    }

    @Test
    fun feedReferencesCanRepresentMultipleFoldersAndLocalLabels() {
        val feedId = "feed/https://example.test/rss"
        assertEquals(
            setOf(FolderFeedCrossRef(1, feedId), FolderFeedCrossRef(2, feedId)),
            listOf(FolderFeedCrossRef(1, feedId), FolderFeedCrossRef(2, feedId), FolderFeedCrossRef(1, feedId)).toSet(),
        )
        assertEquals(
            setOf(FeedLocalTagCrossRef(feedId, 3), FeedLocalTagCrossRef(feedId, 4)),
            listOf(FeedLocalTagCrossRef(feedId, 3), FeedLocalTagCrossRef(feedId, 4), FeedLocalTagCrossRef(feedId, 3)).toSet(),
        )
    }
}
