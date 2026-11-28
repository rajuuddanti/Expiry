package com.mahalaxmi.expiry.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProductMasterEntity::class, ExpiryEntryEntity::class, ArchiveEntryEntity::class], version = 7, exportSchema = false)
abstract class ExpiryDatabase : RoomDatabase() {
    abstract fun expiryDao(): ExpiryDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS product_master_new (rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scanCode TEXT NOT NULL, skuCode TEXT NOT NULL, skuName TEXT NOT NULL, category TEXT, rowOrder INTEGER NOT NULL, isActive INTEGER NOT NULL)")
                db.execSQL("INSERT INTO product_master_new (scanCode, skuCode, skuName, category, rowOrder, isActive) SELECT scanCode, skuCode, skuName, NULL, rowid, isActive FROM product_master")
                db.execSQL("DROP TABLE product_master")
                db.execSQL("ALTER TABLE product_master_new RENAME TO product_master")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_master_skuCode ON product_master(skuCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_master_skuName ON product_master(skuName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_master_rowOrder ON product_master(rowOrder)")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expiry_entries ADD COLUMN category TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expiry_entries ADD COLUMN expiry TEXT NOT NULL DEFAULT 'YES'")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expiry_entries ADD COLUMN noExpiryCategory TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS archive_entries (id TEXT NOT NULL PRIMARY KEY, location TEXT NOT NULL, skuCode TEXT NOT NULL, skuName TEXT NOT NULL, scannedCode TEXT NOT NULL, quantity INTEGER NOT NULL, expiryDate TEXT NOT NULL, category TEXT NOT NULL, date TEXT NOT NULL, time TEXT NOT NULL, createdAt TEXT NOT NULL, expiry TEXT NOT NULL, noExpiryCategory TEXT NOT NULL, sessionStart TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_archive_entries_date ON archive_entries(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_archive_entries_category ON archive_entries(category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_archive_entries_location_date ON archive_entries(location, date)")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expiry_entries ADD COLUMN lastSyncAttemptAt TEXT")
                db.execSQL("ALTER TABLE expiry_entries ADD COLUMN lastSyncError TEXT")
            }
        }
        @Volatile private var INSTANCE: ExpiryDatabase? = null
        fun getInstance(context: Context): ExpiryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, ExpiryDatabase::class.java, "mahalaxmi_expiry.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build().also { INSTANCE = it }
            }
    }
}
