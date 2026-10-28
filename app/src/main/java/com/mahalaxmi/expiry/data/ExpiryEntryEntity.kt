package com.mahalaxmi.expiry.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline-first expiry capture. Rows are retained locally until sync succeeds.
 * Deletion is soft so history/audit data is not physically discarded.
 */
@Entity(
    tableName = "expiry_entries",
    indices = [
        Index(value = ["store", "sessionStart"]),
        Index(value = ["syncStatus"]),
        Index(value = ["skuCode"])
    ]
)
data class ExpiryEntryEntity(
    @PrimaryKey
    val id: String,
    val store: String,
    val skuCode: String,
    val productName: String,
    val scannedCode: String,
    val category: String = "",
    val quantity: Int,
    val expiryDate: String,
    val sessionStart: String,
    val createdAt: String,
    val deletedAt: String? = null,
    val syncStatus: String = "PENDING"
)
