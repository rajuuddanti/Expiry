package com.mahalaxmi.expiry

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val mandatory: Boolean,
    val releaseNotes: String,
    val apkUrl: String,
    val sha256: String
)

object AppUpdateManager {
    private const val RELEASE_API =
        "https://api.github.com/repos/rajuuddanti/Expiry/releases/latest"
    private const val MANIFEST_ASSET = "update.json"

    suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        val release = readJson(RELEASE_API)
        val assets = release.optJSONArray("assets")
            ?: throw IllegalStateException("The latest release has no assets.")
        var manifestUrl: String? = null
        var apkUrl: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            when (asset.optString("name")) {
                MANIFEST_ASSET -> manifestUrl = asset.optString("browser_download_url")
                else -> if (asset.optString("name").endsWith(".apk", true)) {
                    apkUrl = asset.optString("browser_download_url")
                }
            }
        }
        val url = manifestUrl ?: throw IllegalStateException("Release is missing update.json.")
        val manifest = readJson(url)
        val targetCode = manifest.optInt("versionCode", -1)
        if (targetCode <= BuildConfig.VERSION_CODE) return@withContext null
        val assetName = manifest.optString("apkAssetName")
        val resolvedApk = if (assetName.isNotBlank()) {
            var selected: String? = null
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                if (asset.optString("name") == assetName) {
                    selected = asset.optString("browser_download_url")
                    break
                }
            }
            selected
        } else apkUrl
        AppUpdate(
            versionCode = targetCode,
            versionName = manifest.optString("versionName", release.optString("tag_name")),
            mandatory = manifest.optBoolean("mandatory", false),
            releaseNotes = manifest.optString("releaseNotes", release.optString("body")),
            apkUrl = resolvedApk ?: throw IllegalStateException("Release is missing the APK asset."),
            sha256 = manifest.optString("sha256")
        )
    }

    suspend fun download(
        update: AppUpdate,
        context: Context,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MahaMart-Expiry-Manager")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("APK download failed: HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val apk = File(directory, "mahamart-expiry-update.apk")
            connection.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0) onProgress(((downloaded * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            if (update.sha256.isNotBlank()) {
                val digest = MessageDigest.getInstance("SHA-256")
                val actual = apk.inputStream().use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                if (!actual.equals(update.sha256, true)) {
                    apk.delete()
                    throw IllegalStateException("Downloaded APK failed SHA-256 verification.")
                }
            }
            apk
        } finally {
            connection.disconnect()
        }
    }

    fun canInstallPackages(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun readJson(address: String): JSONObject {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "MahaMart-Expiry-Manager")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Update check failed: HTTP ${connection.responseCode}")
            }
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
