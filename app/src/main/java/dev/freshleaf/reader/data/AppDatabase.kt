package dev.freshleaf.reader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FeedEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        LocalFeedTagEntity::class,
        ArticleEntity::class,
        LocalFolderEntity::class,
        FolderFeedCrossRef::class,
        FolderCategoryCrossRef::class,
        FeedLocalTagCrossRef::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feeds(): FeedDao
    abstract fun categories(): CategoryDao
    abstract fun tags(): TagDao
    abstract fun localFeedTags(): LocalFeedTagDao
    abstract fun articles(): ArticleDao
    abstract fun folders(): LocalFolderDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "freshleaf.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `local_feed_tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS `feed_local_tags` (`feedId` TEXT NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`feedId`, `tagId`))")
            }
        }
    }
}
