package com.mahalaxmi.expiry.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Server archive cache. Separate from active Expiry Manager history and never uploaded. */
@Entity(
    tableName = "archive_entries",
    indices = [
        Index(value = ["date"]),
        Index(value = ["category"]),
        Index(value = ["location", "date"])
    ]
)
data class ArchiveEntryEntity(
    @PrimaryKey val id: String,
    val location: String,
    val skuCode: String,
    val skuName: String,
    val scannedCode: String,
    val quantity: Int,
    val expiryDate: String,
    val category: String,
    val date: String,
    val time: String,
    val createdAt: String,
    val expiry: String,
    val noExpiryCategory: String,
    val sessionStart: String
)
