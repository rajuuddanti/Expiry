package com.mahalaxmi.expiry.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpiryDao {
    @Query("SELECT * FROM expiry_entries WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeEntries(): Flow<List<ExpiryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: ExpiryEntryEntity)

    @Query("UPDATE expiry_entries SET deletedAt = :deletedAt, syncStatus = 'PENDING' WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteEntry(id: String, deletedAt: String)

    @Query("SELECT * FROM expiry_entries WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingEntries(): List<ExpiryEntryEntity>

    @Query("SELECT * FROM product_master WHERE scanCode = :code AND isActive = 1 LIMIT 1")
    suspend fun findProductByScanCode(code: String): ProductMasterEntity?

    @Query("SELECT * FROM product_master WHERE skuCode = :code AND isActive = 1 LIMIT 1")
    suspend fun findProductBySku(code: String): ProductMasterEntity?

    @Query("SELECT COUNT(*) FROM product_master WHERE isActive = 1")
    suspend fun activeProductCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<ProductMasterEntity>)

    @Query("DELETE FROM product_master")
    suspend fun clearProductMaster()

    /** Atomically replace the cached product master without touching expiry entries. */
    @Transaction
    suspend fun replaceProductMaster(products: List<ProductMasterEntity>) {
        require(products.isNotEmpty()) { "Refusing to replace product master with an empty result." }
        clearProductMaster()
        products.chunked(400).forEach { upsertProducts(it) }
    }

    @Query("UPDATE expiry_entries SET syncStatus = 'SYNCED' WHERE id IN (:ids)")
    suspend fun markEntriesSynced(ids: List<String>)
}
