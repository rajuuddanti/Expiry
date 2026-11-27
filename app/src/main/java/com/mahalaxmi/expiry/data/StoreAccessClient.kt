package com.mahalaxmi.expiry.data

import android.content.Context
import com.mahalaxmi.expiry.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Calls the store-access Edge Function. No password or service-role key is stored in Android. */
class StoreAccessClient(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("expiry_manager", Context.MODE_PRIVATE)
    private val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    private val apiKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
    private val functionUrl = "$baseUrl/functions/v1/store-access"

    data class StoreSession(val token: String, val expiresAt: String, val storeName: String, val mobileNumber: String)
    data class ArchivePage(val rows: List<ArchiveEntryEntity>, val hasMore: Boolean, val nextOffset: Int)

    fun hasSession(): Boolean = !sessionToken().isNullOrBlank()

    /** Session bearer token is encrypted with an Android Keystore AES-GCM key. */
    fun sessionToken(): String? = runCatching {
        val stored = prefs.getString("store_session_token_encrypted", null) ?: return null
        val packed = Base64.decode(stored, Base64.NO_WRAP)
        require(packed.size > 12) { "Stored session is invalid." }
        val iv = packed.copyOfRange(0, 12)
        val ciphertext = packed.copyOfRange(12, packed.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()

    fun saveSession(session: StoreSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(session.token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("store_session_token_encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .remove("store_session_token")
            .putString("store_session_expires_at", session.expiresAt)
            .putString("store_session_mobile", session.mobileNumber)
            .putString("store", session.storeName).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey("mahamart_store_session_v1", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                "mahamart_store_session_v1",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    suspend fun login(mobile: String, password: String): StoreSession = withContext(Dispatchers.IO) {
        val result = post(JSONObject().put("action", "login").put("mobile_number", mobile).put("password", password), null)
        StoreSession(result.getString("token"), result.getString("expires_at"),
            result.getString("store_name"), result.getString("mobile_number")).also(::saveSession)
    }

    suspend fun startArchiveSync(): JSONObject = withContext(Dispatchers.IO) {
        post(JSONObject().put("action", "archive-start"), requireToken())
    }

    suspend fun fetchArchivePage(
        syncId: String, offset: Int, date: String? = null,
        categories: List<String> = emptyList(), search: String = ""
    ): ArchivePage = withContext(Dispatchers.IO) {
        val body = JSONObject().put("action", "archive-page").put("sync_id", syncId).put("offset", offset)
            .put("categories", JSONArray(categories)).put("search", search)
        if (!date.isNullOrBlank()) body.put("date", date)
        val result = post(body, requireToken())
        val array = result.optJSONArray("rows") ?: JSONArray()
        val parsed = buildList {
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                add(ArchiveEntryEntity(
                    id = row.optString("id"), location = row.optString("location"),
                    skuCode = row.optString("skucode"), skuName = row.optString("skuname"),
                    scannedCode = row.optString("ean_sku_scanned"), quantity = row.optInt("qty"),
                    expiryDate = row.optString("expiry_date"), category = row.optString("category"),
                    date = row.optString("date"), time = row.optString("time"),
                    createdAt = row.optString("created_at"), expiry = row.optString("expiry"),
                    noExpiryCategory = row.optString("no_expiry_category"), sessionStart = row.optString("session_start")
                ))
            }
        }
        ArchivePage(parsed, result.optBoolean("has_more"), result.optInt("next_offset"))
    }

    suspend fun completeArchiveSync(syncId: String): JSONObject = withContext(Dispatchers.IO) {
        post(JSONObject().put("action", "archive-complete").put("sync_id", syncId), requireToken())
    }

    suspend fun cancelArchiveSync(syncId: String) = withContext(Dispatchers.IO) {
        post(JSONObject().put("action", "archive-cancel").put("sync_id", syncId), requireToken())
    }

    private fun requireToken(): String = sessionToken()?.takeIf { it.isNotBlank() }
        ?: throw IllegalStateException("Please sign in to your store account.")

    private fun post(body: JSONObject, token: String?): JSONObject {
        check(baseUrl.startsWith("https://") && apiKey.isNotBlank()) { "Supabase configuration is missing." }
        val connection = (URL(functionUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 45000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Authorization", "Bearer ${token ?: apiKey}")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val response = if (raw.isBlank()) JSONObject() else JSONObject(raw)
            if (status !in 200..299) throw IllegalStateException(response.optString("error").ifBlank { "Store service failed ($status)." })
            return response
        } finally { connection.disconnect() }
    }
}
