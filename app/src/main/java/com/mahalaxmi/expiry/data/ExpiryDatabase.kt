package com.mahalaxmi.expiry.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProductMasterEntity::class, ExpiryEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ExpiryDatabase : RoomDatabase() {
    abstract fun expiryDao(): ExpiryDao

    companion object {
        @Volatile
        private var INSTANCE: ExpiryDatabase? = null

        fun getInstance(context: Context): ExpiryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ExpiryDatabase::class.java,
                    "mahalaxmi_expiry.db"
                ).build().also { INSTANCE = it }
            }
    }
}
