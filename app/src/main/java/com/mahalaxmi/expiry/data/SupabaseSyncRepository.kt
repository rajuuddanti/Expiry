package com.mahalaxmi.expiry.data

import android.content.Context
import com.mahalaxmi.expiry.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/**
 * Offline-first Supabase synchronization.
 *
 * Uses only the Supabase publishable/anon key plus an anonymous Auth session.
 * Never put a service-role key in the Android app. Server-side RLS policies
 * must be installed before synchronization can succeed.
 */
class SupabaseSyncRepository(
    context: Context,
    private val dao: ExpiryDao
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("expiry_supabase_sync", Context.MODE_PRIVATE)
    private val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

    data class SyncResult(
        val productsUpdated: Int,
        val entriesUploaded: Int,
        val message: String
    )

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        check(baseUrl.startsWith("https://")) {
            "Supabase URL is not configured. Set SUPABASE_URL in local.properties."
        }
        check(apiKey.isNotBlank()) {
            "Supabase publishable key is not configured. Set SUPABASE_PUBLISHABLE_KEY in local.properties."
        }

        val session = ensureSession()
        val productCount = downloadProductMaster(session.accessToken)
        val uploadedCount = uploadPendingEntries(session.accessToken, session.userId)
        SyncResult(
            productsUpdated = productCount,
            entriesUploaded = uploadedCount,
            message = "Sync complete: $productCount product rows refreshed; $uploadedCount entries uploaded."
        )
    }

    private data class AuthSession(val accessToken: String, val refreshToken: String, val userId: String)

    private fun ensureSession(): AuthSession {
        val savedAccess = prefs.getString("access_token", null)
        val savedRefresh = prefs.getString("refresh_token", null)
        if (!savedAccess.isNullOrBlank() && !savedRefresh.isNullOrBlank()) {
            runCatching {
                val refreshed = requestJson(
                    "POST",
                    "/auth/v1/token?grant_type=refresh_token",
                    JSONObject().put("refresh_token", savedRefresh),
                    bearer = apiKey
                )
                return persistSession(refreshed)
            }
        }
        // Anonymous sign-in must be enabled in Supabase Auth settings.
        val signedIn = requestJson("POST", "/auth/v1/signup", JSONObject(), bearer = apiKey)
        return persistSession(signedIn)
    }

    private fun persistSession(json: JSONObject): AuthSession {
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val userId = json.optJSONObject("user")?.optString("id").orEmpty()
        check(access.isNotBlank() && refresh.isNotBlank() && userId.isNotBlank()) {
            "Supabase did not return an Auth session. Enable anonymous sign-in in Supabase Auth."
        }
        prefs.edit()
            .putString("access_token", access)
            .putString("refresh_token", refresh)
            .putString("user_id", userId)
            .apply()
        return AuthSession(access, refresh, userId)
    }

    private suspend fun downloadProductMaster(accessToken: String): Int {
        // In this project product_master_staging IS the master list.
        // It contains the scanned SKU/EAN code in sku_code and its lookup name in sku_name.
        // Do not expect a separate product_master table or updated_at column.
        val allRows = mutableListOf<ProductMasterEntity>()
        var offset = 0
        val pageSize = 1000
        while (true) {
            val path = "/rest/v1/product_master_staging?select=sku_code,sku_name&order=sku_code.asc"
            val rows = requestArray(
                "GET",
                path,
                body = null,
                bearer = accessToken,
                range = "$offset-${offset + pageSize - 1}"
            )
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val code = row.optString("sku_code").trim()
                val name = row.optString("sku_name").trim()
                if (code.isNotBlank() && name.isNotBlank()) {
                    allRows += ProductMasterEntity(
                        scanCode = code,
                        skuCode = code,
                        skuName = name,
                        isActive = true
                    )
                }
            }
            if (rows.length() < pageSize) break
            offset += pageSize
        }

        check(allRows.isNotEmpty()) {
            "Supabase returned no rows from product_master_staging. Check the master data and SELECT policy; local cache was preserved."
        }
        // This staging table is the authoritative master; refresh local lookup cache from it.
        dao.replaceProductMaster(allRows)
        prefs.edit().putString("product_master_last_sync", Instant.now().toString()).apply()
        return allRows.size
    }

    private suspend fun uploadPendingEntries(accessToken: String, userId: String): Int {
        val pending = dao.getPendingEntries()
        var uploaded = 0
        for (entry in pending) {
            val body = JSONObject()
                .put("client_record_id", entry.id)
                .put("owner_id", userId)
                .put("store_name", entry.store)
                .put("sku_code", entry.skuCode)
                .put("sku_name", entry.productName)
                .put("ean", entry.scannedCode)
                .put("qty", entry.quantity)
                .put("expiry_date", entry.expiryDate)
                .put("session_start", entry.sessionStart)
                .put("device_created_at", entry.createdAt)
                .put("is_deleted", entry.deletedAt != null)
                .put("deleted_at", entry.deletedAt)

            requestJson(
                "POST",
                "/rest/v1/expiry_entries?on_conflict=client_record_id",
                body,
                bearer = accessToken,
                prefer = "resolution=merge-duplicates,return=minimal"
            )
            dao.markEntriesSynced(listOf(entry.id))
            uploaded++
        }
        return uploaded
    }

    private fun requestJson(
        method: String,
        path: String,
        body: JSONObject?,
        bearer: String,
        prefer: String? = null
    ): JSONObject {
        val response = execute(method, path, body?.toString(), bearer, prefer, null)
        return if (response.isBlank()) JSONObject() else JSONObject(response)
    }

    private fun requestArray(
        method: String,
        path: String,
        body: JSONObject?,
        bearer: String,
        range: String
    ): JSONArray {
        val response = execute(method, path, body?.toString(), bearer, null, range)
        return JSONArray(response)
    }

    private fun execute(
        method: String,
        path: String,
        body: String?,
        bearer: String,
        prefer: String?,
        range: String?
    ): String {
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
            if (range != null) setRequestProperty("Range", range)
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("Supabase request failed ($status): ${response.take(500)}")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
