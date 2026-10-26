package com.mahalaxmi.expiry.data

import android.content.Context
import com.mahalaxmi.expiry.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** Offline-first sync against the live public.MASTER and public.expiry_scan_uploads tables. */
class SupabaseSyncRepository(context: Context, private val dao: ExpiryDao) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("expiry_supabase_sync", Context.MODE_PRIVATE)
    private val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

    data class SyncResult(val productsUpdated: Int, val entriesUploaded: Int, val message: String)

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        check(baseUrl.startsWith("https://")) { "Supabase URL is not configured. Set SUPABASE_URL in local.properties." }
        check(apiKey.isNotBlank()) { "Supabase publishable key is not configured. Set SUPABASE_PUBLISHABLE_KEY in local.properties." }
        val session = ensureSession()
        val count = downloadProductMaster(session.accessToken)
        val uploaded = uploadPendingEntries(session.accessToken, session.userId)
        SyncResult(count, uploaded, "Sync complete: $count master rows refreshed; $uploaded entries uploaded.")
    }

    private data class AuthSession(val accessToken: String, val refreshToken: String, val userId: String)

    private fun ensureSession(): AuthSession {
        val refresh = prefs.getString("refresh_token", null)
        if (!refresh.isNullOrBlank()) {
            runCatching {
                return persistSession(requestJson("POST", "/auth/v1/token?grant_type=refresh_token",
                    JSONObject().put("refresh_token", refresh), apiKey))
            }
        }
        return persistSession(requestJson("POST", "/auth/v1/signup", JSONObject(), apiKey))
    }

    private fun persistSession(json: JSONObject): AuthSession {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val userId = json.optJSONObject("user")?.optString("id").orEmpty()
        check(access.isNotBlank() && refresh.isNotBlank() && userId.isNotBlank()) {
            "Supabase did not return an Auth session. Enable anonymous sign-in in Supabase Auth."
        }
        prefs.edit().putString("access_token", access).putString("refresh_token", refresh).putString("user_id", userId).apply()
        return AuthSession(access, refresh, userId)
    }

    private suspend fun downloadProductMaster(accessToken: String): Int {
        val raw = mutableListOf<ProductMasterEntity>()
        var offset = 0
        val pageSize = 1000
        while (true) {
            // Do not sort by SkuCode: FCFS depends on the stacked source order.
            val rows = requestArray("GET", "/rest/v1/MASTER?select=SkuCode,SkuLongName,Catg",
                accessToken, "$offset-${offset + pageSize - 1}")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val code = row.optString("SkuCode").trim()
                val name = row.optString("SkuLongName").trim()
                val category = row.optString("Catg").trim().takeIf { it.isNotBlank() && it != "null" }
                if (code.isNotBlank() && name.isNotBlank()) {
                    raw += ProductMasterEntity(scanCode = code, skuCode = code, skuName = name,
                        category = category, rowOrder = raw.size, isActive = true)
                }
            }
            if (rows.length() < pageSize) break
            offset += pageSize
        }
        check(raw.isNotEmpty()) {
            "Supabase returned no rows from public.MASTER. Check SELECT access; local master cache was preserved."
        }

        // The first row for a product name is the FCFS SKU; following same-name rows are EAN/barcode rows.
        // Keep all rows, including repeated names and codes, rather than deduplicating the master.
        val firstSkuByName = LinkedHashMap<String, String>()
        raw.forEach { row ->
            val key = row.skuName.trim().lowercase()
            if (key.isNotEmpty() && key !in firstSkuByName) firstSkuByName[key] = row.scanCode
        }
        val mapped = raw.map { row ->
            row.copy(skuCode = firstSkuByName[row.skuName.trim().lowercase()] ?: row.scanCode)
        }
        dao.replaceProductMaster(mapped)
        prefs.edit().putString("product_master_last_sync", Instant.now().toString()).apply()
        return mapped.size
    }

    private suspend fun uploadPendingEntries(accessToken: String, userId: String): Int {
        val pending = dao.getPendingEntries()
        var uploaded = 0
        for (entry in pending) {
            val body = JSONObject()
                .put("id", entry.id)
                .put("owner_id", userId)
                .put("store_name", entry.store)
                .put("skucode", entry.skuCode)
                .put("skuname", entry.productName)
                .put("ean", entry.scannedCode.ifBlank { entry.skuCode })
                .put("qty", entry.quantity)
                .put("expirydate", entry.expiryDate)
                .put("is_deleted", entry.deletedAt != null)
                .put("device_id", android.provider.Settings.Secure.getString(appContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID))
            requestJson("POST", "/rest/v1/expiry_scan_uploads?on_conflict=id", body, accessToken,
                prefer = "resolution=merge-duplicates,return=minimal")
            dao.markEntriesSynced(listOf(entry.id))
            uploaded++
        }
        return uploaded
    }

    private fun requestJson(method: String, path: String, body: JSONObject?, bearer: String, prefer: String? = null): JSONObject {
        val response = execute(method, path, body?.toString(), bearer, prefer, null)
        return if (response.isBlank()) JSONObject() else JSONObject(response)
    }

    private fun requestArray(method: String, path: String, bearer: String, range: String): JSONArray =
        JSONArray(execute(method, path, null, bearer, null, range))

    private fun execute(method: String, path: String, body: String?, bearer: String, prefer: String?, range: String?): String {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer $bearer")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            if (prefer != null) setRequestProperty("Prefer", prefer)
            if (range != null) {
                setRequestProperty("Range-Unit", "items")
                setRequestProperty("Range", range)
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IllegalStateException("Supabase request failed ($status): ${response.take(500)}")
            return response
        } finally {
            connection.disconnect()
        }
    }
}
