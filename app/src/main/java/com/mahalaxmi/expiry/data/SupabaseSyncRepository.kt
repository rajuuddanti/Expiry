package com.mahalaxmi.expiry.data

import android.content.Context
import com.mahalaxmi.expiry.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Imports public.MASTER only. Expiry entries remain local and are exported by the user. */
class SupabaseSyncRepository(context: Context, private val dao: ExpiryDao) {
    private val appContext = context.applicationContext
    private val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()

    data class SyncResult(val productsUpdated: Int, val message: String)

    data class ReturnRule(
        val ruleType: String,
        val matchValue: String,
        val returnDays: Int,
        val priority: Int,
        val note: String?
    )

    /** Reads active category and brand return rules. Does not modify MASTER or expiry entries. */
    suspend fun fetchReturnRules(): List<ReturnRule> = withContext(Dispatchers.IO) {
        check(baseUrl.startsWith("https://")) { "Supabase URL is not configured." }
        check(apiKey.isNotBlank()) { "Supabase publishable key is not configured." }
        val rows = requestArray(
            "GET",
            "/rest/v1/expiry_return_rules?select=rule_type,match_value,return_days_before_expiry,priority,note&is_active=eq.true&order=priority.desc,match_value.asc",
            apiKey,
            "0-999"
        )
        buildList {
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val type = row.optString("rule_type").trim().lowercase()
                val value = row.optString("match_value").trim()
                val days = row.optInt("return_days_before_expiry", -1)
                if ((type == "category" || type == "brand") && value.isNotBlank() && days >= 0) {
                    add(ReturnRule(type, value, days, row.optInt("priority", 0), row.optString("note").takeIf { it.isNotBlank() && it != "null" }))
                }
            }
        }
    }

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO) {
        check(baseUrl.startsWith("https://")) { "Supabase URL is not configured. Set SUPABASE_URL in local.properties." }
        check(apiKey.isNotBlank()) { "Supabase publishable key is not configured. Set SUPABASE_PUBLISHABLE_KEY in local.properties." }
        val count = downloadProductMaster(apiKey)
        SyncResult(count, "Import complete: $count master rows refreshed. Expiry entries remain on this device only.")
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
        return mapped.size
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
