package com.mahalaxmi.expiry.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local searchable copy of the published Supabase product_master.
 * Codes stay TEXT so leading zeroes are preserved.
 */
@Entity(
    tableName = "product_master",
    indices = [Index(value = ["skuCode"]), Index(value = ["skuName"])]
)
data class ProductMasterEntity(
    @PrimaryKey
    val scanCode: String,
    val skuCode: String,
    val skuName: String,
    val isActive: Boolean = true
)
