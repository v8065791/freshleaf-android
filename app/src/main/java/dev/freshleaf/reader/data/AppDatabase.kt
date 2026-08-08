package dev.freshleaf.reader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FeedEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        ArticleEntity::class,
        LocalFolderEntity::class,
        FolderFeedCrossRef::class,
        FolderCategoryCrossRef::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feeds(): FeedDao
    abstract fun categories(): CategoryDao
    abstract fun tags(): TagDao
    abstract fun articles(): ArticleDao
    abstract fun folders(): LocalFolderDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "freshleaf.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}

