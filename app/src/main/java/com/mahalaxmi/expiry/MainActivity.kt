package com.mahalaxmi.expiry

import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.media.AudioManager
import android.media.ToneGenerator
import android.widget.Toast
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.mahalaxmi.expiry.data.ExpiryDatabase
import com.mahalaxmi.expiry.data.ExpiryEntryEntity
import com.mahalaxmi.expiry.data.SupabaseSyncRepository
import com.mahalaxmi.expiry.data.ExpirySyncWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MahalaxmiRed = Color(0xFFB71C1C)
private val IndiaZone = ZoneId.of("Asia/Kolkata")
private enum class AppScreen { Scan, Lookup, History, Settings }

private data class InspectionScheduleRow(val date: LocalDate, val category: String, val cycleDays: Int) {
    val baseCategory: String get() = category.substringBefore("(").trim().uppercase()
}

private fun loadInspectionSchedule(activity: ComponentActivity): List<InspectionScheduleRow> = runCatching {
    val json = org.json.JSONArray(activity.assets.open("inspection_schedule.json").bufferedReader().use { it.readText() })
    buildList {
        for (i in 0 until json.length()) {
            val row = json.getJSONObject(i)
            val date = runCatching { LocalDate.parse(row.getString("date")) }.getOrNull() ?: continue
            val category = row.optString("category").trim()
            val cycle = row.optInt("cycleDays", 0)
            if (category.isNotBlank() && cycle > 0) add(InspectionScheduleRow(date, category, cycle))
        }
    }
}.getOrDefault(emptyList())

/** Preserve the supplied calendar. Beyond its end, continue category cycles, at most two per day. */
private fun scheduledRowsForDate(source: List<InspectionScheduleRow>, target: LocalDate): List<InspectionScheduleRow> {
    if (source.isEmpty()) return emptyList()
    val exact = source.filter { it.date == target }
    if (exact.isNotEmpty() || target <= source.maxOf { it.date }) return exact
    data class CycleState(val category: String, val cycleDays: Int, var nextDue: LocalDate)
    val latest = source.groupBy { it.baseCategory }.mapNotNull { (_, rows) ->
        val last = rows.maxByOrNull { it.date } ?: return@mapNotNull null
        CycleState(last.baseCategory, last.cycleDays, last.date.plusDays(last.cycleDays.toLong()))
    }.toMutableList()
    var day = source.maxOf { it.date }.plusDays(1)
    var result = emptyList<InspectionScheduleRow>()
    while (day <= target) {
        val due = latest.filter { !it.nextDue.isAfter(day) }
            .sortedWith(compareBy<CycleState> { it.nextDue }.thenBy { it.category }).take(2)
        result = due.map { InspectionScheduleRow(day, it.category, it.cycleDays) }
        due.forEach { it.nextDue = day.plusDays(it.cycleDays.toLong()) }
        day = day.plusDays(1)
    }
    return result
}

private fun normalizedRuleName(value: String): String = value.uppercase().filter { it.isLetterOrDigit() }

private fun masterCategoryAliases(value: String): List<String> {
    val key = normalizedRuleName(value)
    return when (key) {
        "CHOCOLATES", "CHOCLATES" -> listOf("CHOCLATES", "CHOCOLATES")
        "DEOSANDSPRAYS" -> listOf("DEOS  AND SPRAYS")
        "HEALTHANDOTC" -> listOf("HEALTH  AND  OTC")
        "HOUSEANDKITCHENCLEANING" -> listOf("HOUSE  AND  KITCHEN CLEANING")
        "JAMSANDSAUCES" -> listOf("JAMS  AND  SAUCES")
        "NOODLESANDPASTA" -> listOf("NOODLES  AND  PASTA")
        "DAIRYFOODS" -> listOf("DAIRY FOODS", "GHEES")
        else -> listOf(value.trim())
    }
}

private fun scheduleMasterCategoryKeys(row: InspectionScheduleRow): List<String> {
    val base = row.category.substringBefore("(").trim()
    return base.split("/", "&").flatMap(::masterCategoryAliases).distinctBy { normalizedRuleName(it) }
}

private fun categoryRuleFor(category: String, rules: List<SupabaseSyncRepository.ReturnRule>): SupabaseSyncRepository.ReturnRule? {
    val categoryRules = rules.filter { it.ruleType == "category" }
    val categoryKey = normalizedRuleName(category)

    // Return-day rules always belong to the exact category. In particular,
    // DAIRY FOODS and GHEES must never borrow each other's return-day values.
    val exactMatch = categoryRules.firstOrNull {
        normalizedRuleName(it.matchValue) == categoryKey
    }
    if (exactMatch != null) return exactMatch

    // The only return-rule spelling fallback is the known MASTER typo:
    // CHOCOLATES in the schedule may be stored as CHOCLATES in MASTER/rules.
    // BISCUITS, DAIRY FOODS and GHEES always require their own exact rules.
    val isChocolateSpelling = categoryKey == "CHOCOLATES" || categoryKey == "CHOCLATES"
    if (!isChocolateSpelling) return null

    return categoryRules.firstOrNull {
        val ruleKey = normalizedRuleName(it.matchValue)
        ruleKey == "CHOCOLATES" || ruleKey == "CHOCLATES"
    }
}

private fun scheduleRowForMasterCategory(
    category: String,
    rows: List<InspectionScheduleRow>
): InspectionScheduleRow? {
    val key = normalizedRuleName(category)
    return rows.firstOrNull { row -> scheduleMasterCategoryKeys(row).any { normalizedRuleName(it) == key } }
}

class MainActivity : ComponentActivity() {
    private var feedbackToneGenerator: ToneGenerator? = null

    private fun vibrateFeedback(durationMs: Long = 90L) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun playFeedbackTone(tone: Int) {
        if (!getSharedPreferences("expiry_manager", MODE_PRIVATE).getBoolean("audio_feedback_enabled", true)) return
        runCatching {
            val generator = feedbackToneGenerator ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                .also { feedbackToneGenerator = it }
            generator.startTone(tone, 180)
        }
    }

    private fun blockedEntryFeedback() {
        playFeedbackTone(ToneGenerator.TONE_PROP_NACK)
        vibrateFeedback(180L)
    }

    private fun deleteEntryFeedback() {
        playFeedbackTone(ToneGenerator.TONE_SUP_ERROR)
        vibrateFeedback(220L)
    }

    override fun onDestroy() {
        feedbackToneGenerator?.release()
        feedbackToneGenerator = null
        super.onDestroy()
    }

    private var scannedValue by mutableStateOf("")
    private fun shareCsv(entries: List<ExpiryEntryEntity>) {
        fun escape(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\\n", " ") + "\""
        fun parsedTimestamp(value: String): java.time.OffsetDateTime? =
            runCatching { java.time.OffsetDateTime.parse(value) }.getOrNull()
        val header = listOf("Location", "SKU Code", "SKU Name", "Ean/SKU Scanned", "Qty", "Expiry Date", "Category", "Date", "Time", "Expiry Status")
        val rows = entries.map { entry ->
            val timestamp = parsedTimestamp(entry.createdAt)
            listOf(
                entry.store, entry.skuCode, entry.productName, entry.scannedCode,
                entry.quantity.toString(), entry.expiryDate, entry.category,
                timestamp?.toLocalDate()?.toString() ?: entry.createdAt.take(10),
                timestamp?.toLocalTime()?.withNano(0)?.toString() ?: "", entry.expiry
            )
        }
        val csv = (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { escape(it) } }
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        fun filenamePart(value: String): String =
            value.trim()
                .replace(Regex("[^A-Za-z0-9]+"), "-")
                .trim('-')
                .ifBlank { "Unknown" }
        val storeName = entries.firstOrNull()?.store ?: "Unknown-Store"
        val categories = entries.map { it.category.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val categoryName = when (categories.size) {
            0 -> "Uncategorised"
            1 -> categories.first()
            else -> "Multiple-Categories"
        }
        val exportDate = LocalDate.now(IndiaZone).format(DateTimeFormatter.ISO_DATE)
        val fileName = "${filenamePart(storeName)}_${filenamePart(categoryName)}-$exportDate.csv"
        val file = File(dir, fileName)
        file.writeText(csv, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Mahalaxmi Expiry Check")
            putExtra(Intent.EXTRA_TEXT, "Expiry check export attached.")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share expiry CSV"))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("expiry_manager", MODE_PRIVATE)
        val database = ExpiryDatabase.getInstance(applicationContext)
        val dao = database.expiryDao()

        // Run a one-time sync when online, then retry periodically on connected networks.
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val initialSync = OneTimeWorkRequestBuilder<ExpirySyncWorker>()
            .setConstraints(networkConstraints)
            .build()
        val periodicSync = PeriodicWorkRequestBuilder<ExpirySyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(networkConstraints)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "expiry-initial-sync",
            ExistingWorkPolicy.KEEP,
            initialSync
        )
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "expiry-periodic-sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicSync
        )

        setContent {
            val scope = rememberCoroutineScope()
            val entries by dao.observeEntries().collectAsState(initial = emptyList())
            val currentSessionDate = run {
                val now = LocalDateTime.now(IndiaZone)
                if (now.hour < 15) now.toLocalDate().minusDays(1) else now.toLocalDate()
            }
            val currentSessionEntries = remember(entries, currentSessionDate) {
                entries.filter { entry ->
                    val entrySessionDate = runCatching {
                        java.time.OffsetDateTime.parse(entry.sessionStart).atZoneSameInstant(IndiaZone).toLocalDate()
                    }.getOrElse {
                        runCatching {
                            val created = java.time.OffsetDateTime.parse(entry.createdAt).atZoneSameInstant(IndiaZone).toLocalDateTime()
                            if (created.hour < 15) created.toLocalDate().minusDays(1) else created.toLocalDate()
                        }.getOrNull()
                    }
                    entrySessionDate == currentSessionDate
                }
            }
            val syncRepository = remember { SupabaseSyncRepository(applicationContext, dao) }
            var syncMessage by remember { mutableStateOf("Not synced yet") }
            var lastMasterSync by remember { mutableStateOf(prefs.getString("master_last_sync", null)) }
            var cooldownClock by remember { mutableStateOf(LocalDateTime.now(IndiaZone)) }
            LaunchedEffect(lastMasterSync) {
                while (true) {
                    cooldownClock = LocalDateTime.now(IndiaZone)
                    delay(60_000L)
                }
            }
            val syncUnlockAt = remember(lastMasterSync) {
                lastMasterSync?.let { value ->
                    runCatching { LocalDateTime.parse(value).plusDays(20) }.getOrNull()
                }
            }
            val syncCooldownActive = syncUnlockAt?.isAfter(cooldownClock) == true
            val syncCooldownLabel = remember(syncUnlockAt, cooldownClock) {
                val unlockAt = syncUnlockAt
                if (unlockAt == null || !unlockAt.isAfter(cooldownClock)) "" else {
                    val remaining = Duration.between(cooldownClock, unlockAt)
                    val days = remaining.toDays()
                    val hours = remaining.minusDays(days).toHours()
                    "SYNC AVAILABLE IN " + days + "D " + hours + "H"
                }
            }
            val lastMasterSyncLabel = remember(lastMasterSync) {
                val syncedAt = lastMasterSync?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
                if (syncedAt == null) {
                    "NOT YET SYNCED"
                } else {
                    "LAST SYNCED: " + syncedAt.atZone(IndiaZone)
                        .format(DateTimeFormatter.ofPattern("d MMM yyyy - h:mm a", java.util.Locale.ENGLISH))
                        .uppercase(java.util.Locale.ENGLISH)
                }
            }
            var isSyncing by remember { mutableStateOf(false) }
            var syncCompleted by remember { mutableIntStateOf(0) }
            var syncTotal by remember { mutableIntStateOf(0) }
            var syncPhase by remember { mutableStateOf("") }
            var isUploading by remember { mutableStateOf(false) }
            var uploadMessage by remember { mutableStateOf("Expiry entries have not been uploaded yet.") }
            var store by remember { mutableStateOf(prefs.getString("store", "") ?: "") }
            var storeDraft by remember { mutableStateOf("") }
            var storePinInput by remember { mutableStateOf("") }
            var storePinError by remember { mutableStateOf("") }
            var showStorePinDialog by remember { mutableStateOf(false) }
            var showStoreNameDialog by remember { mutableStateOf(false) }
            var showNoExpiryCategoryDialog by remember { mutableStateOf(false) }
            var showNoExpiryConfirmDialog by remember { mutableStateOf(false) }
            var showNoExpiryAlreadyDialog by remember { mutableStateOf(false) }
            var selectedNoExpiryCategory by remember { mutableStateOf("") }
            var pendingNoExpiryCategory by remember { mutableStateOf("") }
            var categoryMismatchPending by remember { mutableStateOf(false) }
            var screen by remember { mutableStateOf(AppScreen.Scan) }
            var code by remember { mutableStateOf("") }
            var name by remember { mutableStateOf("") }
            var ean by remember { mutableStateOf("") }
            var category by remember { mutableStateOf("") }
            var qty by remember { mutableStateOf("") }
            var expiry by remember { mutableStateOf(LocalDate.now(IndiaZone).format(DateTimeFormatter.ISO_DATE)) }
            var message by remember { mutableStateOf("") }
            var lookupQuery by remember { mutableStateOf("") }
            var lookupCategoryFilter by remember { mutableStateOf("ALL") }
            var historySearch by remember { mutableStateOf("") }
            var historyFilter by remember { mutableStateOf("ALL") }
            var historySoonestFirst by remember { mutableStateOf(false) }
            var selectedHistoryEntry by remember { mutableStateOf<ExpiryEntryEntity?>(null) }
            var masterSkuCount by remember { mutableIntStateOf(0) }
            var audioFeedbackEnabled by remember { mutableStateOf(prefs.getBoolean("audio_feedback_enabled", true)) }
            var selectedLookupProduct by remember { mutableStateOf<com.mahalaxmi.expiry.data.ProductMasterEntity?>(null) }
            var lookupResults by remember { mutableStateOf(emptyList<com.mahalaxmi.expiry.data.ProductMasterEntity>()) }
            var todayCategoryProducts by remember { mutableStateOf(emptyList<com.mahalaxmi.expiry.data.ProductMasterEntity>()) }
            var pendingDelete by remember { mutableStateOf<ExpiryEntryEntity?>(null) }
            var lookupMessage by remember { mutableStateOf("Search by product name, SKU code, or EAN/barcode.") }
            var showReturnRules by remember { mutableStateOf(false) }
            var returnRules by remember { mutableStateOf(emptyList<SupabaseSyncRepository.ReturnRule>()) }
            var returnRulesLoading by remember { mutableStateOf(false) }
            var returnRulesError by remember { mutableStateOf("") }
            var updateInfo by remember { mutableStateOf<AppUpdate?>(null) }
            var updateDialogOpen by remember { mutableStateOf(false) }
            var updateChecking by remember { mutableStateOf(false) }
            var updateDownloading by remember { mutableStateOf(false) }
            var updateProgress by remember { mutableIntStateOf(0) }
            var updateStatus by remember { mutableStateOf("Check whether a newer app version is available.") }
            var downloadedUpdateApk by remember { mutableStateOf<File?>(null) }
            LaunchedEffect(Unit) {
                masterSkuCount = runCatching { dao.activeProductCount() }.getOrDefault(0)
            }
            LaunchedEffect(syncRepository) {
                runCatching { syncRepository.fetchReturnRules() }
                    .onSuccess { returnRules = it }
                    .onFailure { returnRulesError = it.message ?: "Could not load Supabase return rules." }
            }
            LaunchedEffect(Unit) {
                delay(1200L)
                runCatching { AppUpdateManager.checkForUpdate() }
                    .onSuccess { available ->
                        if (available != null) {
                            updateInfo = available
                            updateStatus = "Version ${available.versionName} is available."
                            updateDialogOpen = true
                        }
                    }
            }

            // Daily category assignment comes from the inspection calendar.
            // Return-day values remain exclusively in Supabase expiry_return_rules.
            val inspectionSchedule = remember { loadInspectionSchedule(this@MainActivity) }
            val todaySchedule = remember(currentSessionDate, inspectionSchedule) {
                scheduledRowsForDate(inspectionSchedule, currentSessionDate)
            }
            val scheduledSupabaseCategories = remember(todayCategoryProducts) {
                todayCategoryProducts.mapNotNull { it.category?.trim()?.takeIf(String::isNotBlank) }
                    .distinctBy { normalizedRuleName(it) }
            }

            LaunchedEffect(scannedValue) {
                if (scannedValue.isNotBlank()) {
                    code = scannedValue.trim()
                    ean = ""
                    category = ""
                    name = ""
                    scannedValue = ""
                }
            }

            LaunchedEffect(todaySchedule, lastMasterSync) {
                val categoryKeys = todaySchedule.flatMap(::scheduleMasterCategoryKeys)
                    .distinctBy { normalizedRuleName(it) }
                todayCategoryProducts = if (categoryKeys.isEmpty()) emptyList() else
                    runCatching { dao.getProductsForCategories(categoryKeys.map { it.uppercase() }) }
                        .getOrDefault(emptyList())
            }

            LaunchedEffect(code) {
                // SKU/barcode input is the lookup source. Never retain the previous product
                // name or invent an EAN when the current code has no match.
                name = ""
                ean = ""
                category = ""
                val lookupCode = code.trim()
                if (lookupCode.isNotBlank()) {
                    val matchedProduct = runCatching { dao.findProductByScanCode(lookupCode) }.getOrNull()
                        ?: runCatching { dao.findProductBySku(lookupCode) }.getOrNull()
                    if (matchedProduct != null) {
                        name = matchedProduct.skuName
                        category = matchedProduct.category.orEmpty()
                        // MASTER has no dedicated EAN column, so leave EAN blank unless a
                        // genuine barcode mapping is available from a separate source.
                    }
                }
            }

            fun saveNoExpiryEntry(selectedCategory: String) {
                if (store.isBlank()) {
                    message = "Please select your store first."
                    return
                }
                scope.launch {
                    val now = LocalDateTime.now(IndiaZone)
                    val sessionDate = if (now.hour < 15) now.toLocalDate().minusDays(1) else now.toLocalDate()
                    val session = sessionDate.atTime(15, 0).atZone(IndiaZone).toOffsetDateTime().toString()
                    // An active expiry entry proves that this category has expiry today.
                    // Staff must delete its active entries from History before marking NO EXPIRY.
                    val existingExpiryEntries = currentSessionEntries.filter { entry ->
                        entry.store.equals(store, ignoreCase = true) &&
                            !entry.expiry.equals("NO EXPIRY", ignoreCase = true) &&
                            normalizedRuleName(entry.category) == normalizedRuleName(selectedCategory)
                    }
                    if (existingExpiryEntries.isNotEmpty()) {
                        message = "$selectedCategory already has active expiry entries today. Delete them from History before marking NO EXPIRY."
                        return@launch
                    }
                    if (dao.activeNoExpiryCount(store, session, selectedCategory) > 0) {
                        message = "Already marked NO EXPIRY for $selectedCategory today."
                        return@launch
                    }
                    dao.insertEntry(ExpiryEntryEntity(
                        id = java.util.UUID.randomUUID().toString(), store = store,
                        skuCode = "", productName = "", scannedCode = "", category = "",
                        quantity = 0, expiryDate = "", sessionStart = session,
                        createdAt = now.atZone(IndiaZone).toOffsetDateTime().toString(),
                        expiry = "NO EXPIRY", noExpiryCategory = selectedCategory
                    ))
                    message = "NO EXPIRY FOUND for $selectedCategory saved in History."
                    try {
                        val result = syncRepository.uploadPendingEntries()
                        uploadMessage = result.message
                        message = "NO EXPIRY FOUND for $selectedCategory saved. " + result.message
                    } catch (error: Exception) {
                        uploadMessage = "Confirmation saved locally; upload pending."
                        val retryRequest = OneTimeWorkRequestBuilder<ExpirySyncWorker>().setConstraints(networkConstraints).build()
                        WorkManager.getInstance(applicationContext).enqueueUniqueWork("expiry-auto-upload-retry", ExistingWorkPolicy.KEEP, retryRequest)
                    }
                }
            }

            fun saveExpiryEntry() {
                val q = qty.toIntOrNull()
                val validDate = runCatching { LocalDate.parse(expiry) }.isSuccess
                if (store.isBlank()) {
                    message = "Please select your store in Settings."
                } else if (code.isBlank() || name.isBlank() || q == null || q <= 0 || !validDate) {
                    message = "Enter product, quantity and expiry date correctly."
                } else {
                    scope.launch {
                        val matchedProduct = runCatching { dao.findProductByScanCode(code.trim()) }.getOrNull()
                            ?: runCatching { dao.findProductBySku(code.trim()) }.getOrNull()
                        val now = LocalDateTime.now(IndiaZone)
                        val sessionDate = if (now.hour < 15) now.toLocalDate().minusDays(1) else now.toLocalDate()
                        val session = sessionDate.atTime(15, 0).atZone(IndiaZone).toOffsetDateTime().toString()
                        val entryCategory = matchedProduct?.category.orEmpty()
                        // One scheduled category marked NO EXPIRY blocks all entries.
                        // With two scheduled categories, one mark blocks only that category;
                        // both marks block all entries for this 3 PM business day.
                        val markedScheduledCategories = scheduledSupabaseCategories.filter { scheduledCategory ->
                            dao.activeNoExpiryCount(store, session, scheduledCategory) > 0
                        }
                        val allEntriesBlocked = when (scheduledSupabaseCategories.size) {
                            1 -> markedScheduledCategories.isNotEmpty()
                            2 -> markedScheduledCategories.size == 2
                            else -> false
                        }
                        val entryCategoryIsMarked = entryCategory.isNotBlank() &&
                            markedScheduledCategories.any {
                                normalizedRuleName(it) == normalizedRuleName(entryCategory)
                            }
                        when {
                            allEntriesBlocked -> {
                                blockedEntryFeedback()
                                message = if (scheduledSupabaseCategories.size == 1) {
                                    "NO EXPIRY is already marked for ${scheduledSupabaseCategories.first()}. All expiry entries are blocked for this business day."
                                } else {
                                    "NO EXPIRY is marked for both scheduled categories. All expiry entries are blocked for this business day."
                                }
                                return@launch
                            }
                            entryCategoryIsMarked -> {
                                blockedEntryFeedback()
                                message = "NO EXPIRY is already marked for $entryCategory. Entries for this category are blocked today."
                                return@launch
                            }
                        }
                        dao.insertEntry(
                            ExpiryEntryEntity(
                                id = java.util.UUID.randomUUID().toString(),
                                store = store,
                                skuCode = matchedProduct?.skuCode ?: code.trim(),
                                productName = name.trim(),
                                scannedCode = code.trim(),
                                category = entryCategory,
                                quantity = q,
                                expiryDate = expiry,
                                sessionStart = session,
                                createdAt = now.atZone(IndiaZone).toOffsetDateTime().toString()
                            )
                        )
                        code = ""; name = ""; ean = ""; category = ""; qty = ""
                        message = "Entry saved locally. Uploading automatically…"
                        if (!isUploading) {
                            isUploading = true
                            try {
                                val result = syncRepository.uploadPendingEntries()
                                uploadMessage = result.message
                                message = if (result.uploaded > 0) {
                                    "Entry saved and uploaded automatically."
                                } else {
                                    "Entry saved locally. ${result.message}"
                                }
                            } catch (error: Exception) {
                                uploadMessage = "Automatic upload pending: ${error.message ?: "No connection"}"
                                message = "Entry saved locally. Upload will retry when connected."
                                val retryRequest = OneTimeWorkRequestBuilder<ExpirySyncWorker>()
                                    .setConstraints(networkConstraints)
                                    .build()
                                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                                    "expiry-auto-upload-retry",
                                    ExistingWorkPolicy.KEEP,
                                    retryRequest
                                )
                            } finally {
                                isUploading = false
                            }
                        }
                    }
                }
            }

            MaterialTheme {
                if (updateDialogOpen) {
                    val availableUpdate = updateInfo
                    AlertDialog(
                        onDismissRequest = { },
                        title = {
                            Text(
                                if (availableUpdate == null) "Update required"
                                else "Update required • ${availableUpdate.versionName}"
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    when {
                                        updateDownloading -> "Downloading update… Please wait."
                                        downloadedUpdateApk != null -> "Download complete. Install the update to continue."
                                        else -> "A new version is available. Update the app to continue using MahaMart Expiry Manager."
                                    }
                                )
                                if (updateDownloading) {
                                    LinearProgressIndicator(
                                        progress = { updateProgress / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MahalaxmiRed
                                    )
                                    Text("$updateProgress% downloaded", fontSize = 12.sp)
                                }
                                if (updateStatus.isNotBlank()) {
                                    Text(updateStatus, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        },
                        confirmButton = {
                            if (availableUpdate != null && downloadedUpdateApk == null) {
                                Button(
                                    enabled = !updateDownloading,
                                    onClick = {
                                        scope.launch {
                                            updateDownloading = true
                                            updateStatus = "Preparing download…"
                                            updateProgress = 0
                                            try {
                                                downloadedUpdateApk = AppUpdateManager.download(availableUpdate, applicationContext) {
                                                    updateProgress = it
                                                }
                                                updateStatus = "APK downloaded successfully."
                                            } catch (error: Exception) {
                                                updateStatus = error.message ?: "Download failed. Tap UPDATE to retry."
                                            } finally {
                                                updateDownloading = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed)
                                ) {
                                    Text(if (updateDownloading) "DOWNLOADING…" else "UPDATE")
                                }
                            } else if (downloadedUpdateApk != null) {
                                Button(
                                    onClick = {
                                        if (AppUpdateManager.canInstallPackages(this@MainActivity)) {
                                            runCatching {
                                                AppUpdateManager.install(this@MainActivity, downloadedUpdateApk!!)
                                            }.onFailure {
                                                updateStatus = it.message ?: "Could not open Android installer."
                                            }
                                        } else {
                                            updateStatus = "Allow installation for MahaMart Expiry Manager, then return and tap INSTALL UPDATE."
                                            AppUpdateManager.openInstallPermissionSettings(this@MainActivity)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed)
                                ) {
                                    Text("INSTALL UPDATE")
                                }
                            }
                        },
                        dismissButton = null
                    )
                }
                if (showStorePinDialog) {
                    AlertDialog(
                        onDismissRequest = { showStorePinDialog = false; storePinInput = ""; storePinError = "" },
                        title = { Text("Authorize store change") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Enter the PIN to change the store name.")
                                if (storePinError.isNotBlank()) Text(storePinError, color = MahalaxmiRed)
                                OutlinedTextField(
                                    value = storePinInput,
                                    onValueChange = { storePinInput = it.filter(Char::isDigit).take(4) },
                                    label = { Text("PIN") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    singleLine = true
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (storePinInput == "3331") {
                                    storeDraft = store
                                    storePinInput = ""
                                    storePinError = ""
                                    showStorePinDialog = false
                                    showStoreNameDialog = true
                                } else {
                                    storePinInput = ""
                                    storePinError = "Incorrect PIN. Try again."
                                }
                            }) { Text("CONTINUE", color = MahalaxmiRed) }
                        },
                        dismissButton = { TextButton(onClick = { showStorePinDialog = false; storePinInput = ""; storePinError = "" }) { Text("CANCEL") } }
                    )
                }
                if (showStoreNameDialog) {
                    AlertDialog(
                        onDismissRequest = { showStoreNameDialog = false },
                        title = { Text("Change store name") },
                        text = {
                            OutlinedTextField(
                                value = storeDraft,
                                onValueChange = { storeDraft = it },
                                label = { Text("Store name") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (storeDraft.isNotBlank()) {
                                    store = storeDraft.trim()
                                    prefs.edit().putString("store", store).apply()
                                    message = "Store name updated."
                                }
                                showStoreNameDialog = false
                            }) { Text("SAVE", color = MahalaxmiRed) }
                        },
                        dismissButton = { TextButton(onClick = { showStoreNameDialog = false }) { Text("CANCEL") } }
                    )
                }
                if (showNoExpiryConfirmDialog) {
                    val alreadyMarked = currentSessionEntries.filter { it.expiry.equals("NO EXPIRY", true) }
                        .map { it.noExpiryCategory }.filter { it.isNotBlank() }.distinctBy { it.uppercase() }
                    AlertDialog(
                        onDismissRequest = { showNoExpiryConfirmDialog = false; pendingNoExpiryCategory = "" },
                        title = { Text("Confirm NO EXPIRY") },
                        text = {
                            val previous = if (alreadyMarked.isEmpty()) "" else "Already marked NO EXPIRY for: ${alreadyMarked.joinToString(", ")}.\n\n"
                            Text(previous + "Are you sure you want to save NO EXPIRY for $pendingNoExpiryCategory for the present business day?")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val chosen = pendingNoExpiryCategory
                                showNoExpiryConfirmDialog = false
                                pendingNoExpiryCategory = ""
                                if (chosen.isNotBlank()) saveNoExpiryEntry(chosen)
                            }) { Text("YES, SAVE", color = MahalaxmiRed) }
                        },
                        dismissButton = { TextButton(onClick = { showNoExpiryConfirmDialog = false; pendingNoExpiryCategory = "" }) { Text("CANCEL") } }
                    )
                }
                if (showNoExpiryAlreadyDialog) {
                    val marked = currentSessionEntries.filter { it.expiry.equals("NO EXPIRY", true) }
                        .map { it.noExpiryCategory }.filter { it.isNotBlank() }.distinctBy { it.uppercase() }
                    AlertDialog(
                        onDismissRequest = { showNoExpiryAlreadyDialog = false },
                        title = { Text("NO EXPIRY already marked") },
                        text = { Text("Already marked NO EXPIRY for: ${marked.joinToString(", ")}.") },
                        confirmButton = { TextButton(onClick = { showNoExpiryAlreadyDialog = false }) { Text("OK", color = MahalaxmiRed) } }
                    )
                }
                if (categoryMismatchPending) {
                    val todayCategories = scheduledSupabaseCategories
                    AlertDialog(
                        onDismissRequest = { categoryMismatchPending = false },
                        title = { Text("Category mismatch") },
                        text = {
                            Text(
                                "Product: $name\nEntered SKU category: ${category.ifBlank { "Unknown" }}\nPresent day category: ${todayCategories.joinToString(", ").ifBlank { "None scheduled" }}\n\nDo you wish to continue?"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                categoryMismatchPending = false
                                saveExpiryEntry()
                            }) { Text("YES", color = MahalaxmiRed) }
                        },
                        dismissButton = {
                            TextButton(onClick = { categoryMismatchPending = false }) { Text("NO") }
                        }
                    )
                }
                if (pendingDelete != null) {
                    AlertDialog(
                        onDismissRequest = { pendingDelete = null },
                        title = { Text("Confirm deletion") },
                        text = { Text("Delete this expiry entry from the active history? It will be marked deleted and queued for sync.") },
                        confirmButton = {
                            TextButton(onClick = {
                                val item = pendingDelete
                                pendingDelete = null
                                if (item != null) {
                                    deleteEntryFeedback()
                                    scope.launch {
                                    dao.softDeleteEntry(item.id, LocalDateTime.now(IndiaZone).atZone(IndiaZone).toOffsetDateTime().toString())
                                    }
                                }
                            }) { Text("DELETE", color = MahalaxmiRed) }
                        },
                        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("CANCEL") } }
                    )
                }
                Scaffold(
                    bottomBar = {
                        Surface(
                            color = Color.White,
                            tonalElevation = 0.dp,
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 6.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                val navItems = listOf(
                                    Triple(AppScreen.Scan, "▣", "Scan"),
                                    Triple(AppScreen.Lookup, "⌕", "SKU Lookup"),
                                    Triple(AppScreen.History, "☷", "History"),
                                    Triple(AppScreen.Settings, "⚙", "Settings")
                                )
                                navItems.forEach { (destination, glyph, label) ->
                                    val active = screen == destination
                                    Column(
                                        Modifier.weight(1f).fillMaxHeight().clickable { screen = destination },
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(18.dp),
                                            color = if (active) Color(0xFFFEE2E2) else Color.Transparent
                                        ) {
                                            Text(glyph, Modifier.padding(horizontal = 18.dp, vertical = 3.dp), fontSize = 21.sp,
                                                color = if (active) Color(0xFFC62828) else Color(0xFF64748B))
                                        }
                                        Text(label, fontSize = 10.sp, lineHeight = 12.sp,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            color = if (active) Color(0xFF0F172A) else Color(0xFF64748B), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).background(Color(0xFFF8FAFC))
                    ) {
                        val headerPendingCount = currentSessionEntries.count { it.syncStatus.equals("PENDING", true) }
                        Row(
                            Modifier.fillMaxWidth().background(Color.White)
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("Expiry Manager", fontSize = 16.sp, lineHeight = 19.sp,
                                    fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                Text(store.ifBlank { "Branch Not Set" }, fontSize = 11.sp,
                                    color = Color(0xFF64748B), maxLines = 1)
                            }
                            Surface(
                                onClick = {
                                    if (headerPendingCount > 0 && !isUploading) {
                                        scope.launch {
                                            isUploading = true
                                            try { uploadMessage = syncRepository.uploadPendingEntries().message }
                                            catch (error: Exception) { uploadMessage = error.message ?: "Upload failed; entries remain pending." }
                                            finally { isUploading = false }
                                        }
                                    } else if (headerPendingCount == 0) screen = AppScreen.History
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (headerPendingCount > 0) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    if (isUploading) "Syncing…" else if (headerPendingCount > 0) "$headerPendingCount Pending" else "Synced",
                                    Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                    color = if (headerPendingCount > 0) Color(0xFF92400E) else Color(0xFF15803D),
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Divider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                        when (screen) {
                            AppScreen.Scan -> {
                                val pendingScanCount = currentSessionEntries.count { it.syncStatus.equals("PENDING", true) }
                                val loggedNoExpiry = currentSessionEntries.filter {
                                    it.expiry.equals("NO EXPIRY", true) && it.store.equals(store, true)
                                }.map { it.noExpiryCategory.trim().uppercase() }.toSet()
                                Column(
                                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    if (pendingScanCount > 0) {
                                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCD34D))) {
                                            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text("$pendingScanCount local ${if (pendingScanCount == 1) "entry" else "entries"} pending sync", color = Color(0xFF92400E), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    Text("$store · Ready to upload", color = Color(0xFFB45309), fontSize = 11.sp)
                                                }
                                                Button(onClick = {
                                                    if (!isUploading) scope.launch {
                                                        isUploading = true
                                                        try { uploadMessage = syncRepository.uploadPendingEntries().message }
                                                        catch (error: Exception) { uploadMessage = error.message ?: "Upload failed; entries remain pending." }
                                                        finally { isUploading = false }
                                                    }
                                                }, enabled = !isUploading, shape = RoundedCornerShape(11.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp)) {
                                                    Text(if (isUploading) "Syncing…" else "Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                Text("EAN Barcode or SkuCode", color = Color(0xFF334155), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                Text("${masterSkuCount.toString()} local SKUs", color = Color(0xFF64748B), fontSize = 11.sp)
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = code, onValueChange = { code = it; message = "" },
                                                    modifier = Modifier.weight(1f), singleLine = true,
                                                    placeholder = { Text("Scan or type EAN / SkuCode…", fontSize = 13.sp) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC62828), unfocusedBorderColor = Color(0xFFCBD5E1), focusedContainerColor = Color.White, unfocusedContainerColor = Color(0xFFF8FAFC))
                                                )
                                                Button(onClick = {
                                                    GmsBarcodeScanning.getClient(this@MainActivity).startScan()
                                                        .addOnSuccessListener { barcode -> barcode.rawValue?.let { scannedValue = it } }
                                                        .addOnCanceledListener { message = "Scan cancelled" }
                                                        .addOnFailureListener { e -> message = "Scanner error: ${e.localizedMessage ?: "Please try again"}" }
                                                }, modifier = Modifier.size(52.dp), contentPadding = PaddingValues(0.dp),
                                                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) {
                                                    Text("▦", fontSize = 24.sp)
                                                }
                                            }
                                            if (name.isNotBlank()) {
                                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF8FAFC),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                                    Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                        Text("✓  MATCHED IN MASTER", color = Color(0xFF15803D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        Text(name, color = Color(0xFF0F172A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                        Text("SKU ${code}  ·  ${category.ifBlank { "Category unavailable" }}", color = Color(0xFF475569), fontSize = 11.sp)
                                                    }
                                                }
                                            } else {
                                                Text("Scan a barcode or type an EAN / SkuCode to fetch the product from local Master.",
                                                    color = Color(0xFF64748B), fontSize = 12.sp)
                                            }
                                            Text("Quantity", color = Color(0xFF334155), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                OutlinedButton(onClick = { qty = ((qty.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString() },
                                                    modifier = Modifier.size(50.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(12.dp)) { Text("−", fontSize = 22.sp) }
                                                OutlinedTextField(value = qty, onValueChange = { qty = it.filter(Char::isDigit) },
                                                    modifier = Modifier.weight(1f), singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    placeholder = { Text("1") }, shape = RoundedCornerShape(12.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC62828), unfocusedBorderColor = Color(0xFFCBD5E1), focusedContainerColor = Color.White, unfocusedContainerColor = Color(0xFFF8FAFC)))
                                                OutlinedButton(onClick = { qty = ((qty.toIntOrNull() ?: 0) + 1).toString() },
                                                    modifier = Modifier.size(50.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(12.dp)) { Text("+", fontSize = 22.sp) }
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                Text("Expiry Date", color = Color(0xFF334155), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                                val daysLeft = runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(IndiaZone), LocalDate.parse(expiry)).toInt() }.getOrNull()
                                                Text(daysLeft?.let { if (it < 0) "Expired ${-it}d ago" else "${it} days left" } ?: "", color = if ((daysLeft ?: 999) < 0) Color(0xFFC62828) else Color(0xFF64748B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            OutlinedButton(onClick = {
                                                scope.launch {
                                                    if (returnRules.isEmpty() && !returnRulesLoading) runCatching { returnRules = syncRepository.fetchReturnRules() }
                                                    val current = runCatching { LocalDate.parse(expiry) }.getOrDefault(LocalDate.now(IndiaZone))
                                                    val picker = android.app.DatePickerDialog(this@MainActivity, { _, year, month, day -> expiry = LocalDate.of(year, month + 1, day).format(DateTimeFormatter.ISO_DATE) }, current.year, current.monthValue - 1, current.dayOfMonth)
                                                    val rule = categoryRuleFor(category, returnRules)
                                                    val scheduledRow = scheduleRowForMasterCategory(category, todaySchedule)
                                                    if (rule != null || scheduledRow != null) {
                                                        val effectiveDays = maxOf(rule?.returnDays ?: 0, scheduledRow?.cycleDays ?: 0)
                                                        picker.datePicker.maxDate = LocalDate.now(IndiaZone).plusDays(effectiveDays.toLong()).atStartOfDay(IndiaZone).toInstant().toEpochMilli()
                                                    }
                                                    picker.show()
                                                }
                                            }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF334155)),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))) {
                                                Text("▦   ${runCatching { LocalDate.parse(expiry).format(DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH)) }.getOrDefault(expiry)}   ·   CHANGE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            if (message.isNotBlank()) {
                                                Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFFEE2E2)) {
                                                    Text(message, Modifier.fillMaxWidth().padding(10.dp), color = Color(0xFF991B1B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                            Button(onClick = {
                                                vibrateFeedback(70L)
                                                val enteredCategory = normalizedRuleName(category)
                                                val allowedToday = scheduledSupabaseCategories.map(::normalizedRuleName)
                                                if (enteredCategory.isNotBlank() && enteredCategory !in allowedToday) categoryMismatchPending = true
                                                else saveExpiryEntry()
                                            }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) {
                                                Text("✓   SAVE EXPIRY ENTRY", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            }
                                        }
                                    }

                                    if (scheduledSupabaseCategories.isNotEmpty()) {
                                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color.White),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                    Text("Today's Scheduled Categories — Mark No Expiry", color = Color(0xFF0F172A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    Text("${scheduledSupabaseCategories.size} scheduled", color = Color(0xFF64748B), fontSize = 11.sp)
                                                }                                                Text("If no expiring stock was found in a scheduled category today, record a No Expiry confirmation.", color = Color(0xFF64748B), fontSize = 12.sp)
                                                scheduledSupabaseCategories.forEach { candidate ->
                                                    val isLogged = candidate.trim().uppercase() in loggedNoExpiry
                                                    Button(onClick = {
                                                        if (!isLogged) {
                                                            pendingNoExpiryCategory = candidate
                                                            showNoExpiryConfirmDialog = true
                                                        }
                                                    }, enabled = !isLogged, modifier = Modifier.fillMaxWidth().height(46.dp),
                                                        shape = RoundedCornerShape(11.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isLogged) Color(0xFFDCFCE7) else Color(0xFFF0FDF4),
                                                            disabledContainerColor = Color(0xFFDCFCE7),
                                                            contentColor = Color(0xFF15803D), disabledContentColor = Color(0xFF15803D)
                                                        )) {
                                                        Text(if (isLogged) "✓  No Expiry Logged · $candidate" else "◉  No Expiry · $candidate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    TextButton(onClick = {
                                        showReturnRules = true
                                        if (returnRules.isEmpty() && !returnRulesLoading) scope.launch {
                                            returnRulesLoading = true
                                            try { returnRules = syncRepository.fetchReturnRules() }
                                            catch (error: Exception) { returnRulesError = error.message ?: "Could not load rules." }
                                            finally { returnRulesLoading = false }
                                        }
                                    }, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) { Text("ⓘ Return Rules", color = Color(0xFFC62828), fontWeight = FontWeight.Bold) }
                                }
                                if (showReturnRules) {
                                    val categoryRules = todaySchedule.flatMap { row -> scheduleMasterCategoryKeys(row).mapNotNull { key -> categoryRuleFor(key, returnRules)?.let { Triple(it.matchValue, it, maxOf(it.returnDays, row.cycleDays)) } } }.distinctBy { normalizedRuleName(it.first) }
                                    val brandRules = returnRules.filter { it.ruleType == "brand" }.sortedByDescending { it.priority }
                                    AlertDialog(onDismissRequest = { showReturnRules = false }, title = { Text("Return Rules") },
                                        text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Text("TODAY'S SCHEDULED CATEGORIES", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                            if (returnRulesLoading) Text("Loading rules…") else categoryRules.forEach { (n, rule, effective) -> Text("$n  ·  ${maxOf(rule.returnDays, effective)} days") }
                                            Divider(); Text("BRAND RULES", color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                                            brandRules.forEach { Text("${it.matchValue}: ${it.returnDays} days") }
                                        } }, confirmButton = { TextButton(onClick = { showReturnRules = false }) { Text("CLOSE") } })
                                }
                            }

                            AppScreen.Lookup -> {
                                val uniqueCategoryProducts = remember(todayCategoryProducts) { todayCategoryProducts.distinctBy { it.skuName.trim().lowercase() } }
                                val activeSlot = todaySchedule.firstOrNull { it.baseCategory.equals(lookupCategoryFilter, true) }
                                val categoryProducts = if (lookupCategoryFilter == "ALL") uniqueCategoryProducts else {
                                    val keys = activeSlot?.let(::scheduleMasterCategoryKeys)?.map(::normalizedRuleName)?.toSet() ?: setOf(normalizedRuleName(lookupCategoryFilter))
                                    uniqueCategoryProducts.filter { normalizedRuleName(it.category.orEmpty()) in keys }
                                }
                                val filteredCategoryProducts = remember(categoryProducts, lookupQuery) {
                                    if (lookupQuery.isBlank()) categoryProducts else categoryProducts.filter { p ->
                                        p.skuName.contains(lookupQuery, true) || p.skuCode.contains(lookupQuery, true) || p.scanCode.contains(lookupQuery, true)
                                    }
                                }
                                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("SKU Lookup", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                        Text("◷  Scheduled for ${currentSessionDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH))}", fontSize = 12.sp, color = Color(0xFF64748B))
                                    }
                                    Text("${filteredCategoryProducts.size} SKUs in today's schedule", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                                if (todaySchedule.size > 1) {
                                                    FilterChip(selected = lookupCategoryFilter == "ALL", onClick = { lookupCategoryFilter = "ALL" }, label = { Text("All Today (${uniqueCategoryProducts.size})") }, shape = RoundedCornerShape(11.dp))
                                                }
                                                todaySchedule.forEach { slot ->
                                                    val keys = scheduleMasterCategoryKeys(slot).map(::normalizedRuleName).toSet()
                                                    val count = uniqueCategoryProducts.count { normalizedRuleName(it.category.orEmpty()) in keys }
                                                    FilterChip(selected = lookupCategoryFilter == slot.baseCategory, onClick = { lookupCategoryFilter = slot.baseCategory }, label = { Text("${slot.baseCategory} ($count)", maxLines = 1) }, shape = RoundedCornerShape(11.dp))
                                                }
                                            }
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                OutlinedTextField(value = lookupQuery, onValueChange = { lookupQuery = it; selectedLookupProduct = null },
                                                    modifier = Modifier.weight(1f), singleLine = true,
                                                    placeholder = { Text("Search today's SKUs by name, SkuCode, or EAN…", fontSize = 12.sp) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC62828), unfocusedBorderColor = Color(0xFFCBD5E1), focusedContainerColor = Color.White, unfocusedContainerColor = Color(0xFFF8FAFC)))
                                                Button(onClick = {
                                                    GmsBarcodeScanning.getClient(this@MainActivity).startScan().addOnSuccessListener { it.rawValue?.let { value -> lookupQuery = value } }
                                                        .addOnCanceledListener { lookupMessage = "Scan cancelled" }
                                                        .addOnFailureListener { lookupMessage = it.message ?: "Scanner error" }
                                                }, modifier = Modifier.size(50.dp), contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) { Text("▦", fontSize = 22.sp) }
                                            }
                                        }
                                    }
                                    if (filteredCategoryProducts.isEmpty()) {
                                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                                Text(if (todayCategoryProducts.isEmpty()) "No categories scheduled today" else "No matching SKUs in today's schedule", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                                Text("Try a different SkuCode, EAN, or product name.", fontSize = 12.sp, color = Color(0xFF64748B))
                                            }
                                        }
                                    } else {
                                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                            items(filteredCategoryProducts.take(120), key = { "lookup-${it.rowId}" }) { product ->
                                                Card(onClick = { selectedLookupProduct = product }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp),
                                                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Text(product.skuName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), maxLines = 2)
                                                            Text("SKU ${product.skuCode}  ·  EAN ${product.scanCode.ifBlank { "—" }}  ·  ${product.category.orEmpty()}", fontSize = 11.sp, color = Color(0xFF64748B), maxLines = 2)
                                                        }
                                                        Text("›", fontSize = 25.sp, color = Color(0xFF94A3B8))
                                                    }
                                                }
                                                Divider(color = Color(0xFFF1F5F9))
                                            }
                                        }
                                    }
                                }
                                if (selectedLookupProduct != null) {
                                    val product = selectedLookupProduct!!
                                    Dialog(onDismissRequest = { selectedLookupProduct = null }) {
                                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.Top) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(product.category.orEmpty(), color = Color(0xFFC62828), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                        Text(product.skuName, color = Color(0xFF0F172A), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                                    }
                                                    TextButton(onClick = { selectedLookupProduct = null }) { Text("✕", color = Color(0xFF334155)) }
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) { Column(Modifier.padding(11.dp)) { Text("SkuCode", fontSize = 10.sp, color = Color(0xFF64748B)); Text(product.skuCode, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }
                                                    Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) { Column(Modifier.padding(11.dp)) { Text("EAN Barcode", fontSize = 10.sp, color = Color(0xFF64748B)); Text(product.scanCode.ifBlank { "Use SkuCode" }, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
                                                }
                                                Button(onClick = { code = product.scanCode.ifBlank { product.skuCode }; selectedLookupProduct = null; screen = AppScreen.Scan }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) { Text("Enter Qty & Expiry Date", fontWeight = FontWeight.Bold) }
                                            }
                                        }
                                    }
                                }
                            }

                            AppScreen.History -> {
                                val pendingCount = currentSessionEntries.count { it.syncStatus.equals("PENDING", true) }
                                val filterCounts = listOf(
                                    "ALL" to currentSessionEntries.size,
                                    "EXPIRY" to currentSessionEntries.count { it.expiry.equals("YES", true) },
                                    "NO EXPIRY" to currentSessionEntries.count { it.expiry.equals("NO EXPIRY", true) },
                                    "PENDING" to pendingCount,
                                    "SYNCED" to currentSessionEntries.count { it.syncStatus.equals("SYNCED", true) }
                                )
                                val filteredHistory = currentSessionEntries.filter { item ->
                                    val filterOk = when (historyFilter) {
                                        "EXPIRY" -> item.expiry.equals("YES", true)
                                        "NO EXPIRY" -> item.expiry.equals("NO EXPIRY", true)
                                        "PENDING" -> item.syncStatus.equals("PENDING", true)
                                        "SYNCED" -> item.syncStatus.equals("SYNCED", true)
                                        else -> true
                                    }
                                    val q = historySearch.trim()
                                    filterOk && (q.isBlank() || listOf(item.productName, item.skuCode, item.scannedCode, item.category, item.noExpiryCategory).any { it.contains(q, true) })
                                }.let { list ->
                                    if (historySoonestFirst) list.sortedBy { item -> if (item.expiry.equals("NO EXPIRY", true)) LocalDate.MAX else runCatching { LocalDate.parse(item.expiryDate) }.getOrDefault(LocalDate.MAX) }
                                    else list.sortedByDescending { it.createdAt }
                                }
                                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Expiry History", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                            Text("$store · ${currentSessionEntries.size} ${if (currentSessionEntries.size == 1) "entry" else "entries"} logged", fontSize = 12.sp, color = Color(0xFF64748B))
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            TextButton(onClick = { historySoonestFirst = !historySoonestFirst }) { Text(if (historySoonestFirst) "Soonest" else "Latest", color = Color(0xFF334155), fontSize = 11.sp) }
                                            TextButton(onClick = { shareCsv(filteredHistory) }, enabled = filteredHistory.isNotEmpty()) { Text("Export CSV", color = Color(0xFF334155), fontSize = 11.sp) }
                                        }
                                    }
                                    if (pendingCount > 0) {
                                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCD34D))) {
                                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("$pendingCount pending ${if (pendingCount == 1) "record" else "records"} ready to sync", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                                    Text("$store · Upload local entries to cloud storage", fontSize = 11.sp, color = Color(0xFFB45309))
                                                }
                                                Button(onClick = { if (!isUploading) scope.launch { isUploading = true; try { uploadMessage = syncRepository.uploadPendingEntries().message } catch (e: Exception) { uploadMessage = e.message ?: "Upload failed" } finally { isUploading = false } } }, enabled = !isUploading, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)), shape = RoundedCornerShape(11.dp)) { Text(if (isUploading) "Syncing" else "Sync All", fontSize = 11.sp) }
                                            }
                                        }
                                    }
                                    if (currentSessionEntries.isNotEmpty()) {
                                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                                OutlinedTextField(value = historySearch, onValueChange = { historySearch = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                                    placeholder = { Text("Filter by SKU, EAN, product name, or category…", fontSize = 12.sp) }, shape = RoundedCornerShape(11.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFC62828), unfocusedBorderColor = Color(0xFFCBD5E1), focusedContainerColor = Color.White, unfocusedContainerColor = Color(0xFFF8FAFC)))
                                                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    filterCounts.forEach { (key, count) ->
                                                        FilterChip(selected = historyFilter == key, onClick = { historyFilter = key }, label = { Text("${if (key == "ALL") "All" else key.lowercase().replaceFirstChar { it.uppercase() }} ($count)", fontSize = 10.sp, maxLines = 1) }, shape = RoundedCornerShape(9.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (filteredHistory.isEmpty()) {
                                        Card(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                Text(if (currentSessionEntries.isEmpty()) "No expiry entries for $store yet" else "No matching records", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                                Text(if (currentSessionEntries.isEmpty()) "Scan an EAN or enter a SkuCode to record expiring stock." else "Try clearing your search or switching filter tabs.", fontSize = 12.sp, color = Color(0xFF64748B))
                                                if (currentSessionEntries.isEmpty()) Button(onClick = { screen = AppScreen.Scan }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))) { Text("Start Scanning") }
                                            }
                                        }
                                    } else {
                                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                            items(filteredHistory, key = { it.id }) { item ->
                                                val noExpiry = item.expiry.equals("NO EXPIRY", true)
                                                val pending = item.syncStatus.equals("PENDING", true)
                                                Card(onClick = { selectedHistoryEntry = item }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                                    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 13.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Text(if (noExpiry) "${item.noExpiryCategory} — No Expiring Stock" else item.productName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), maxLines = 2)
                                                            Text(if (noExpiry) "No Expiry  ·  ${item.noExpiryCategory}" else "${item.expiryDate}  ·  SKU ${item.skuCode}", fontSize = 11.sp, color = if (noExpiry) Color(0xFF15803D) else Color(0xFF334155))
                                                            Text("${item.category}  ·  ${item.createdAt.take(16).replace('T', ' ')}  ·  ${if (pending) "Pending" else "Synced"}", fontSize = 10.sp, color = if (pending) Color(0xFFB45309) else Color(0xFF15803D), maxLines = 2)
                                                        }
                                                        if (!noExpiry) Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                                                            Text("Qty ${item.quantity}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                                            Text(item.expiryDate, fontSize = 10.sp, color = Color(0xFF475569))
                                                        }
                                                    }
                                                }
                                                Divider(color = Color(0xFFF1F5F9))
                                            }
                                        }
                                    }
                                }
                                if (selectedHistoryEntry != null) {
                                    val item = selectedHistoryEntry!!
                                    val noExpiry = item.expiry.equals("NO EXPIRY", true)
                                    Dialog(onDismissRequest = { selectedHistoryEntry = null }) {
                                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(if (noExpiry) "No Expiry Category" else item.category, color = if (noExpiry) Color(0xFF15803D) else Color(0xFFC62828), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        Text(if (noExpiry) item.noExpiryCategory else item.productName, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                                    }
                                                    TextButton(onClick = { selectedHistoryEntry = null }) { Text("✕") }
                                                }
                                                Text("Branch  ·  ${item.store}", fontSize = 12.sp, color = Color(0xFF475569))
                                                Text("Logged  ·  ${item.createdAt}", fontSize = 12.sp, color = Color(0xFF475569))
                                                if (!noExpiry) {
                                                    Text("Quantity  ·  ${item.quantity}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    Text("Expiry Date  ·  ${item.expiryDate}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                                    Text("SkuCode  ·  ${item.skuCode}", fontSize = 12.sp)
                                                    Text("EAN / Scanned  ·  ${item.scannedCode}", fontSize = 12.sp)
                                                } else {
                                                    Text("Category ${item.noExpiryCategory} verified clear with no expiring items.", color = Color(0xFF15803D), fontSize = 12.sp)
                                                }
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (!noExpiry) OutlinedButton(onClick = { code = item.scannedCode.ifBlank { item.skuCode }; selectedHistoryEntry = null; screen = AppScreen.Scan }, modifier = Modifier.weight(1f)) { Text("Scan Again") }
                                                    if (item.syncStatus.equals("PENDING", true)) Button(onClick = { pendingDelete = item; selectedHistoryEntry = null }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B))) { Text("Delete") }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            AppScreen.Settings -> {
                                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("Settings", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                        Text("Store branch lock, today's scheduled categories, and local Master storage", fontSize = 12.sp, color = Color(0xFF64748B))
                                    }
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFFEE2E2)) { Text("⌂", Modifier.padding(10.dp), color = Color(0xFFC62828), fontSize = 19.sp) }
                                                Column(Modifier.weight(1f)) {
                                                    Text("Active Store Branch", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                                    Text("Manual entry · PIN-protected to prevent accidental branch changes", fontSize = 11.sp, color = Color(0xFF64748B))
                                                }
                                                Text("🔒 PIN Locked", fontSize = 10.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                                            }
                                            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF8FAFC), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text("Current Branch Location", fontSize = 11.sp, color = Color(0xFF64748B))
                                                        Text(store.ifBlank { "Not Configured" }, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                                                    }
                                                    Button(onClick = { storePinInput = ""; storePinError = ""; showStorePinDialog = true }, shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) { Text("Change Branch", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                                }
                                            }
                                        }
                                    }
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF1F5F9)) { Text("▦", Modifier.padding(10.dp), color = Color(0xFF334155), fontSize = 18.sp) }
                                                Spacer(Modifier.width(10.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text("Today's Scheduled Categories", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                                    Text("Loaded automatically from the project schedule · ${currentSessionDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", java.util.Locale.ENGLISH))}", fontSize = 11.sp, color = Color(0xFF64748B))
                                                }
                                                Text("Read-only", fontSize = 10.sp, color = Color(0xFF64748B))
                                            }
                                            Divider(color = Color(0xFFE2E8F0))
                                            todaySchedule.forEach { slot ->
                                                val keys = scheduleMasterCategoryKeys(slot).map(::normalizedRuleName).toSet()
                                                val count = todayCategoryProducts.count { normalizedRuleName(it.category.orEmpty()) in keys }
                                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(slot.baseCategory, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                                        Text("${slot.cycleDays}-day cycle", fontSize = 11.sp, color = Color(0xFF64748B))
                                                    }
                                                    Text("$count SKUs", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                                }
                                                Divider(color = Color(0xFFF1F5F9))
                                            }
                                            if (todaySchedule.isEmpty()) Text("No categories scheduled today.", fontSize = 12.sp, color = Color(0xFF64748B))
                                        }
                                    }
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFDCFCE7)) { Text("▤", Modifier.padding(10.dp), color = Color(0xFF15803D), fontSize = 18.sp) }
                                                Spacer(Modifier.width(10.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text("SKU Master (Local Device Storage)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                                    Text("Stored locally for instant offline EAN & SkuCode lookup", fontSize = 11.sp, color = Color(0xFF64748B))
                                                }
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf("Local SKUs" to masterSkuCount.toString(), "Mapped EANs" to "—", "Return Rules" to returnRules.size.toString()).forEach { (label, value) ->
                                                    Card(Modifier.weight(1f), shape = RoundedCornerShape(11.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                                        Column(Modifier.padding(9.dp)) { Text(label, fontSize = 9.sp, color = Color(0xFF64748B), maxLines = 1); Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A)) }
                                                    }
                                                }
                                            }
                                            Surface(shape = RoundedCornerShape(9.dp), color = Color(0xFFFEE2E2), modifier = Modifier.fillMaxWidth()) {
                                                Text(lastMasterSyncLabel, Modifier.padding(horizontal = 11.dp, vertical = 10.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                            }
                                            Text("Product master data is stored locally on this device. Expiry entries remain on the device until uploaded.", fontSize = 11.sp, color = Color(0xFF64748B))
                                            Text(syncMessage, fontSize = 11.sp, color = Color(0xFF475569))
                                            if (isSyncing || syncTotal > 0) {
                                                val progress = if (syncTotal > 0) (syncCompleted.toFloat() / syncTotal.toFloat()).coerceIn(0f, 1f) else 0f
                                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Color(0xFFC62828))
                                                Text(if (syncTotal > 0) "$syncPhase: ${syncCompleted.coerceAtMost(syncTotal)} / $syncTotal rows · ${(progress * 100).toInt()}%" else syncPhase, fontSize = 10.sp, color = Color(0xFF64748B))
                                            }
                                            Button(onClick = {
                                                if (!isSyncing) scope.launch {
                                                    isSyncing = true; syncCompleted = 0; syncTotal = 0; syncPhase = "Connecting to Supabase…"; syncMessage = "Importing product master… expiry entries stay on this device."
                                                    try {
                                                        val result = syncRepository.sync { progress -> syncCompleted = progress.completed; syncTotal = progress.total; syncPhase = progress.phase }
                                                        syncMessage = result.message
                                                        val completedAt = LocalDateTime.now(IndiaZone).toString(); prefs.edit().putString("master_last_sync", completedAt).apply(); lastMasterSync = completedAt
                                                        syncPhase = "Complete"; syncCompleted = result.productsUpdated; syncTotal = result.productsUpdated; masterSkuCount = runCatching { dao.activeProductCount() }.getOrDefault(masterSkuCount)
                                                    } catch (error: Exception) { syncMessage = error.message ?: "Sync failed. Check internet and Supabase configuration."; syncPhase = "Sync failed" }
                                                    finally { isSyncing = false }
                                                }
                                            }, enabled = !isSyncing && !syncCooldownActive, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) {
                                                Text(when { isSyncing -> "SYNCING…"; syncCooldownActive -> "SYNC LOCKED"; else -> "SYNC MASTER FROM SUPABASE" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            if (syncCooldownActive) Text(syncCooldownLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                        }
                                    }
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Scanner Preferences", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("Scanner Beep & Confirm Tone", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                                    Text("Play acoustic feedback on barcode capture and entry save", fontSize = 11.sp, color = Color(0xFF64748B))
                                                }
                                                Switch(checked = audioFeedbackEnabled, onCheckedChange = {
                                                    audioFeedbackEnabled = it; prefs.edit().putBoolean("audio_feedback_enabled", it).apply()
                                                })
                                            }
                                        }
                                    }
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("App Updates", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                            Text("Check for the latest version of MahaMart Expiry Manager.", fontSize = 11.sp, color = Color(0xFF64748B))
                                            OutlinedButton(onClick = {
                                                scope.launch {
                                                    updateChecking = true; updateStatus = "Checking GitHub for the latest release…"; updateInfo = null; downloadedUpdateApk = null; updateDialogOpen = false
                                                    try { updateInfo = AppUpdateManager.checkForUpdate(); if (updateInfo != null) { updateStatus = "Version ${updateInfo!!.versionName} is available."; updateDialogOpen = true } else Toast.makeText(this@MainActivity, "This app is already up to date.", Toast.LENGTH_SHORT).show() }
                                                    catch (error: Exception) { Toast.makeText(this@MainActivity, error.message ?: "Could not check for updates.", Toast.LENGTH_LONG).show() }
                                                    finally { updateChecking = false }
                                                }
                                            }, enabled = !updateChecking, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(11.dp)) {
                                                Text(if (updateChecking) "CHECKING FOR UPDATES…" else "CHECK FOR APP UPDATES", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }
                }
            }
        }
    }
}
