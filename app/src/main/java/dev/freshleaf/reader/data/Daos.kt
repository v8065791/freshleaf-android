package dev.freshleaf.reader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM feeds WHERE id = :id")
    suspend fun get(id: String): FeedEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FeedEntity>)

    @Query("DELETE FROM feeds WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<String>)

    @Query("DELETE FROM feeds WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<String>)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TagEntity>)

    @Query("DELETE FROM tags WHERE id NOT IN (:ids)")
    suspend fun deleteMissing(ids: List<String>)
}

@Dao
interface LocalFeedTagDao {
    @Query("SELECT * FROM local_feed_tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LocalFeedTagEntity>>

    @Insert
    suspend fun insert(tag: LocalFeedTagEntity): Long

    @Query("UPDATE local_feed_tags SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM local_feed_tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT tagId FROM feed_local_tags WHERE feedId = :feedId")
    fun observeTagIdsForFeed(feedId: String): Flow<List<Long>>

    @Query("SELECT feedId FROM feed_local_tags WHERE tagId = :tagId")
    fun observeFeedIdsForTag(tagId: Long): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFeedTag(ref: FeedLocalTagCrossRef)

    @Query("DELETE FROM feed_local_tags WHERE feedId = :feedId")
    suspend fun clearFeedTags(feedId: String)

    @Transaction
    suspend fun replaceFeedTags(feedId: String, tagIds: List<Long>) {
        clearFeedTags(feedId)
        tagIds.distinct().forEach { addFeedTag(FeedLocalTagCrossRef(feedId, it)) }
    }
}

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun observeAllCached(): Flow<List<ArticleEntity>>
    @Query(
        """
        SELECT * FROM articles
        WHERE (:filter = 'all' OR (:filter = 'unread' AND isRead = 0) OR (:filter = 'starred' AND isStarred = 1))
          AND (:feedId IS NULL OR feedId = :feedId)
          AND (:categoryId IS NULL OR feedId IN (
              SELECT id FROM feeds WHERE categoryIds = :categoryId
                OR categoryIds LIKE :categoryId || char(31) || '%'
                OR categoryIds LIKE '%' || char(31) || :categoryId
                OR categoryIds LIKE '%' || char(31) || :categoryId || char(31) || '%'
          ))
          AND (:tagId IS NULL OR tagIds LIKE '%' || :tagId || '%')
          AND (:localFeedTagId IS NULL OR feedId IN (SELECT feedId FROM feed_local_tags WHERE tagId = :localFeedTagId))
        ORDER BY publishedAt DESC
        """,
    )
    fun observe(filter: String, feedId: String? = null, categoryId: String? = null, tagId: String? = null, localFeedTagId: Long? = null): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    fun observeOne(id: String): Flow<ArticleEntity?>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun get(id: String): ArticleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ArticleEntity>)

    @Query("UPDATE articles SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: String, read: Boolean)

    @Query("UPDATE articles SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("DELETE FROM articles WHERE feedId = :feedId")
    suspend fun deleteForFeed(feedId: String)
}

@Dao
interface LocalFolderDao {
    @Query("SELECT * FROM local_folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LocalFolderEntity>>

    @Insert
    suspend fun insert(folder: LocalFolderEntity): Long

    @Delete
    suspend fun delete(folder: LocalFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFeed(ref: FolderFeedCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addCategory(ref: FolderCategoryCrossRef)

    @Query("SELECT feedId FROM folder_feeds WHERE folderId = :folderId")
    fun observeFeedIds(folderId: Long): Flow<List<String>>

    @Query("SELECT feedId FROM folder_feeds WHERE folderId IN (:folderIds)")
    fun observeFeedIds(folderIds: List<Long>): Flow<List<String>>

    @Query("SELECT * FROM folder_feeds")
    fun observeAllFeedAssignments(): Flow<List<FolderFeedCrossRef>>

    @Query("SELECT folderId FROM folder_feeds WHERE feedId = :feedId")
    fun observeFolderIdsForFeed(feedId: String): Flow<List<Long>>

    @Query("SELECT categoryId FROM folder_categories WHERE folderId = :folderId")
    fun observeCategoryIds(folderId: Long): Flow<List<String>>

    @Query("SELECT categoryId FROM folder_categories WHERE folderId IN (:folderIds)")
    fun observeCategoryIds(folderIds: List<Long>): Flow<List<String>>

    @Query("DELETE FROM folder_feeds WHERE folderId = :folderId")
    suspend fun clearFeeds(folderId: Long)

    @Query("DELETE FROM folder_feeds WHERE feedId = :feedId")
    suspend fun clearFoldersForFeed(feedId: String)

    @Query("DELETE FROM folder_categories WHERE folderId = :folderId")
    suspend fun clearCategories(folderId: Long)

    @Transaction
    suspend fun replaceSources(folderId: Long, feedIds: List<String>, categoryIds: List<String>) {
        clearFeeds(folderId)
        clearCategories(folderId)
        feedIds.forEach { addFeed(FolderFeedCrossRef(folderId, it)) }
        categoryIds.forEach { addCategory(FolderCategoryCrossRef(folderId, it)) }
    }

    @Transaction
    suspend fun replaceFoldersForFeed(feedId: String, folderIds: List<Long>) {
        clearFoldersForFeed(feedId)
        folderIds.distinct().forEach { addFeed(FolderFeedCrossRef(it, feedId)) }
    }
}
