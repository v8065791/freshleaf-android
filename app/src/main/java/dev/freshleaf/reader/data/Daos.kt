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
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY publishedAt DESC")
    fun observeAllCached(): Flow<List<ArticleEntity>>
    @Query(
        """
        SELECT * FROM articles
        WHERE (:filter = 'all' OR (:filter = 'unread' AND isRead = 0) OR (:filter = 'starred' AND isStarred = 1))
          AND (:feedId IS NULL OR feedId = :feedId)
          AND (:categoryId IS NULL OR feedId IN (SELECT id FROM feeds WHERE categoryIds LIKE '%' || :categoryId || '%'))
          AND (:tagId IS NULL OR tagIds LIKE '%' || :tagId || '%')
        ORDER BY publishedAt DESC
        """,
    )
    fun observe(filter: String, feedId: String? = null, categoryId: String? = null, tagId: String? = null): Flow<List<ArticleEntity>>

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

    @Query("SELECT categoryId FROM folder_categories WHERE folderId = :folderId")
    fun observeCategoryIds(folderId: Long): Flow<List<String>>

    @Query("SELECT categoryId FROM folder_categories WHERE folderId IN (:folderIds)")
    fun observeCategoryIds(folderIds: List<Long>): Flow<List<String>>

    @Query("DELETE FROM folder_feeds WHERE folderId = :folderId")
    suspend fun clearFeeds(folderId: Long)

    @Query("DELETE FROM folder_categories WHERE folderId = :folderId")
    suspend fun clearCategories(folderId: Long)

    @Transaction
    suspend fun replaceSources(folderId: Long, feedIds: List<String>, categoryIds: List<String>) {
        clearFeeds(folderId)
        clearCategories(folderId)
        feedIds.forEach { addFeed(FolderFeedCrossRef(folderId, it)) }
        categoryIds.forEach { addCategory(FolderCategoryCrossRef(folderId, it)) }
    }
}
