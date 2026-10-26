package com.mahalaxmi.expiry.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Local public.MASTER cache. SKU and EAN codes share SkuCode; duplicate rows are retained. */
@Entity(tableName = "product_master", indices = [Index(value = ["skuCode"]), Index(value = ["skuName"]), Index(value = ["rowOrder"])])
data class ProductMasterEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val scanCode: String,
    val skuCode: String,
    val skuName: String,
    val category: String?,
    val rowOrder: Int,
    val isActive: Boolean = true
)
