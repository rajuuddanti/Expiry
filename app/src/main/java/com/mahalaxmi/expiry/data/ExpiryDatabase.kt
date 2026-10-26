package com.mahalaxmi.expiry.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ProductMasterEntity::class, ExpiryEntryEntity::class], version = 2, exportSchema = false)
abstract class ExpiryDatabase : RoomDatabase() {
    abstract fun expiryDao(): ExpiryDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS product_master_new (rowId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, scanCode TEXT NOT NULL, skuCode TEXT NOT NULL, skuName TEXT NOT NULL, category TEXT, rowOrder INTEGER NOT NULL, isActive INTEGER NOT NULL)")
                db.execSQL("INSERT INTO product_master_new (scanCode, skuCode, skuName, category, rowOrder, isActive) SELECT scanCode, skuCode, skuName, NULL, ROW_NUMBER() OVER (ORDER BY rowid), isActive FROM product_master")
                db.execSQL("DROP TABLE product_master")
                db.execSQL("ALTER TABLE product_master_new RENAME TO product_master")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_master_skuCode ON product_master(skuCode)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_master_skuName ON product_master(skuName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_product_master_rowOrder ON product_master(rowOrder)")
            }
        }
        @Volatile private var INSTANCE: ExpiryDatabase? = null
        fun getInstance(context: Context): ExpiryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, ExpiryDatabase::class.java, "mahalaxmi_expiry.db")
                    .addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
