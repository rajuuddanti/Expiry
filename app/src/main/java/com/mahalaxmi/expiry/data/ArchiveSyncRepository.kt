package com.mahalaxmi.expiry.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Explicit, foreground-only archive recovery. Never touches active expiry_entries. */
class ArchiveSyncRepository(context: Context, private val dao: ExpiryDao) {
    private val access = StoreAccessClient(context.applicationContext)

    data class Result(val downloaded: Int, val inserted: Int, val message: String)
    data class Progress(val downloaded: Int, val inserted: Int, val phase: String)

    suspend fun sync(
        onProgress: (Progress) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        val start = access.startArchiveSync()
        val syncId = start.getString("sync_id")
        var offset = 0
        var downloaded = 0
        var inserted = 0
        try {
            while (true) {
                val page = access.fetchArchivePage(syncId, offset)
                downloaded += page.rows.size
                if (page.rows.isNotEmpty()) {
                    inserted += dao.insertArchiveEntries(page.rows).count { it != -1L }
                }
                onProgress(Progress(downloaded, inserted, "Recovering archive"))
                if (!page.hasMore) break
                check(page.nextOffset > offset) { "Archive paging did not advance. Sync cancelled safely." }
                offset = page.nextOffset
            }
            // Prune only after every page was retrieved and stored successfully.
            dao.pruneArchiveBefore(LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(89).toString())
            access.completeArchiveSync(syncId)
            Result(downloaded, inserted, "Archive sync complete: $inserted new record(s), $downloaded received.")
        } catch (error: Exception) {
            runCatching { access.cancelArchiveSync(syncId) }
            throw error
        }
    }
}
