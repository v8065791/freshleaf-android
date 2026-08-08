package dev.freshleaf.reader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val siteUrl: String,
    val categoryIds: String = "",
    val unreadCount: Int = 0,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val title: String,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val title: String,
)

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val feedId: String,
    val title: String,
    val author: String,
    val html: String,
    val url: String,
    val publishedAt: Long,
    val isRead: Boolean,
    val isStarred: Boolean,
    val tagIds: String = "",
)

@Entity(tableName = "local_folders")
data class LocalFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
)

@Entity(
    tableName = "folder_feeds",
    primaryKeys = ["folderId", "feedId"],
)
data class FolderFeedCrossRef(
    val folderId: Long,
    val feedId: String,
)

@Entity(
    tableName = "folder_categories",
    primaryKeys = ["folderId", "categoryId"],
)
data class FolderCategoryCrossRef(
    val folderId: Long,
    val categoryId: String,
)

