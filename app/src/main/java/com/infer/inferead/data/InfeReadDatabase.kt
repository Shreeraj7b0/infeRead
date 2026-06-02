package com.infer.inferead.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [User::class, LibraryFile::class, Checklist::class, ChecklistItem::class, ReadingSession::class, Annotation::class, Bookshelf::class, BookshelfItem::class], version = 8, exportSchema = false)
abstract class InfeReadDatabase : RoomDatabase() {
    abstract fun infeReadDao(): InfeReadDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE checklist_items ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE checklist_items ADD COLUMN indentLevel INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE checklist_items ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `annotations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fileId` INTEGER NOT NULL, `cfiRange` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `textComment` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE annotations ADD COLUMN selectedText TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `bookshelves` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `isMinimised` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `bookshelf_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bookshelfId` INTEGER NOT NULL, `fileId` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)")
            }
        }

        @Volatile
        private var INSTANCE: InfeReadDatabase? = null

        fun getDatabase(context: Context): InfeReadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InfeReadDatabase::class.java,
                    "infer_read_database"
                )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
