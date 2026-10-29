package com.mahalaxmi.expiry

import android.os.Bundle
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

/** Keeps the supplied calendar unchanged; beyond its end, continue each category's last cycle,
 * assigning at most two categories per day and rolling overdue categories forward in due-date order. */
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
            .sortedWith(compareBy<CycleState> { it.nextDue }.thenBy { it.category })
            .take(2)
        result = due.map { InspectionScheduleRow(day, it.category, it.cycleDays) }
        due.forEach { it.nextDue = day.plusDays(it.cycleDays.toLong()) }
        day = day.plusDays(1)
    }
    return result
}

private fun normalizedRuleName(value: String): String =
    value.uppercase().filter { it.isLetterOrDigit() }

private fun categoryRuleFor(category: String, rules: List<SupabaseSyncRepository.ReturnRule>): SupabaseSyncRepository.ReturnRule? {
    val key = normalizedRuleName(category.substringBefore("("))
    return rules.filter { it.ruleType == "category" }.firstOrNull {
        val ruleKey = normalizedRuleName(it.matchValue)
        key.contains(ruleKey) || ruleKey.contains(key)
    }
}

class MainActivity : ComponentActivity() {
    private var scannedValue by mutableStateOf("")
    private fun shareCsv(entries: List<ExpiryEntryEntity>) {
        fun escape(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\\n", " ") + "\""
        fun parsedTimestamp(value: String): java.time.OffsetDateTime? =
            runCatching { java.time.OffsetDateTime.parse(value) }.getOrNull()
        val header = listOf("Location", "SKU Code", "SKU Name", "Ean/SKU Scanned", "Qty", "Expiry Date", "Category", "Date", "Time")
        val rows = entries.map { entry ->
            val timestamp = parsedTimestamp(entry.createdAt)
            listOf(
                entry.store, entry.skuCode, entry.productName, entry.scannedCode,
                entry.quantity.toString(), entry.expiryDate, entry.category,
                timestamp?.toLocalDate()?.toString() ?: entry.createdAt.take(10),
                timestamp?.toLocalTime()?.withNano(0)?.toString() ?: ""
            )
        }
        val csv = (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { escape(it) } }
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "expiry_check_${LocalDate.now(IndiaZone)}.csv")
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
            var lookupResults by remember { mutableStateOf(emptyList<com.mahalaxmi.expiry.data.ProductMasterEntity>()) }
            var todayCategoryProducts by remember { mutableStateOf(emptyList<com.mahalaxmi.expiry.data.ProductMasterEntity>()) }
            var pendingDelete by remember { mutableStateOf<ExpiryEntryEntity?>(null) }
            var lookupMessage by remember { mutableStateOf("Search by product name, SKU code, or EAN/barcode.") }
            var showReturnRules by remember { mutableStateOf(false) }
            var returnRules by remember { mutableStateOf(emptyList<SupabaseSyncRepository.ReturnRule>()) }
            var returnRulesLoading by remember { mutableStateOf(false) }
            var returnRulesError by remember { mutableStateOf("") }
            val inspectionSchedule = remember { loadInspectionSchedule(this@MainActivity) }
            val todaySchedule = remember(LocalDate.now(IndiaZone), inspectionSchedule) {
                scheduledRowsForDate(inspectionSchedule, LocalDate.now(IndiaZone))
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

            LaunchedEffect(todaySchedule) {
                val categoryKeys = todaySchedule.flatMap { row ->
                    listOf(row.category.trim().uppercase(), row.baseCategory.trim().uppercase())
                }.distinct()
                todayCategoryProducts = if (categoryKeys.isEmpty()) emptyList() else
                    runCatching { dao.getProductsForCategories(categoryKeys) }.getOrDefault(emptyList())
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
                        dao.insertEntry(
                            ExpiryEntryEntity(
                                id = java.util.UUID.randomUUID().toString(),
                                store = store,
                                skuCode = matchedProduct?.skuCode ?: code.trim(),
                                productName = name.trim(),
                                scannedCode = code.trim(),
                                category = matchedProduct?.category.orEmpty(),
                                quantity = q,
                                expiryDate = expiry,
                                sessionStart = session,
                                createdAt = now.atZone(IndiaZone).toOffsetDateTime().toString()
                            )
                        )
                        code = ""; name = ""; ean = ""; category = ""; qty = "1"
                        message = "Entry saved locally. Category captured for export and upload."
                    }
                }
            }

            MaterialTheme {
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
                if (categoryMismatchPending) {
                    val todayCategories = todaySchedule.map { it.baseCategory }.distinct()
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
                                if (item != null) scope.launch {
                                    dao.softDeleteEntry(item.id, LocalDateTime.now(IndiaZone).atZone(IndiaZone).toOffsetDateTime().toString())
                                }
                            }) { Text("DELETE", color = MahalaxmiRed) }
                        },
                        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("CANCEL") } }
                    )
                }
                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = Color.White) {
                            NavigationBarItem(
                                selected = screen == AppScreen.Scan,
                                onClick = { screen = AppScreen.Scan },
                                icon = { Text("▣", fontSize = 20.sp) },
                                label = { Text("Scan") }
                            )
                            NavigationBarItem(
                                selected = screen == AppScreen.Lookup,
                                onClick = { screen = AppScreen.Lookup },
                                icon = { Text("⌕", fontSize = 20.sp) },
                                label = { Text("SKU Lookup") }
                            )
                            NavigationBarItem(
                                selected = screen == AppScreen.History,
                                onClick = { screen = AppScreen.History },
                                icon = { Text("☷", fontSize = 20.sp) },
                                label = { Text("History") }
                            )
                            NavigationBarItem(
                                selected = screen == AppScreen.Settings,
                                onClick = { screen = AppScreen.Settings },
                                icon = { Text("⚙", fontSize = 20.sp) },
                                label = { Text("Settings") }
                            )
                        }
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).background(Color(0xFFF7F7F7))
                    ) {
                        Column(Modifier.fillMaxWidth().background(MahalaxmiRed).padding(18.dp)) {
                            Text("MAHALAXMI MAHAMART", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            Text("EXPIRY MANAGER", color = Color.White, fontSize = 14.sp)
                        }

                        when (screen) {
                            AppScreen.Scan -> {
                                Column(
                                    Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Present Day Categories", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            todaySchedule.joinToString(" • ") { it.baseCategory }.ifBlank { "No categories scheduled today" },
                                            fontSize = 12.sp,
                                            color = Color.DarkGray,
                                            lineHeight = 15.sp
                                        )
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(
                                                onClick = {
                                                    showReturnRules = true
                                                    if (returnRules.isEmpty() && !returnRulesLoading) {
                                                        scope.launch {
                                                            returnRulesLoading = true
                                                            returnRulesError = ""
                                                            try {
                                                                returnRules = syncRepository.fetchReturnRules()
                                                            } catch (error: Exception) {
                                                                returnRulesError = error.message ?: "Could not load return rules."
                                                            } finally {
                                                                returnRulesLoading = false
                                                            }
                                                        }
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) { Text("ⓘ Return Rules", fontSize = 12.sp, color = MahalaxmiRed, fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                    if (showReturnRules) {
                                        val categoryRules = todaySchedule.mapNotNull { row ->
                                            val rule = categoryRuleFor(row.category, returnRules)
                                            rule?.let {
                                                val effective = maxOf(it.returnDays, row.cycleDays)
                                                Triple(row, it, effective)
                                            }
                                        }
                                        val brandRules = returnRules.filter { it.ruleType == "brand" }
                                            .sortedByDescending { it.priority }
                                        AlertDialog(
                                            onDismissRequest = { showReturnRules = false },
                                            title = { Text("Return Rules • ${LocalDate.now(IndiaZone)}") },
                                            text = {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("TODAY'S SCHEDULED CATEGORIES", fontWeight = FontWeight.Bold, color = MahalaxmiRed)
                                                    when {
                                                        returnRulesLoading -> Text("Loading rules from Supabase…")
                                                        returnRulesError.isNotBlank() -> Text("Could not load rules: $returnRulesError")
                                                        todaySchedule.isEmpty() -> Text("No category is scheduled for today.")
                                                        categoryRules.isEmpty() -> Text("No matching category rules were found for today's schedule.")
                                                        else -> categoryRules.forEach { (row, rule, effective) ->
                                                            Column {
                                                                Text(row.baseCategory, fontWeight = FontWeight.SemiBold)
                                                                Text("Return days: ${effective} days", fontSize = 13.sp)
                                                                if (effective != rule.returnDays) {
                                                                    Text("Configured: ${rule.returnDays} days • Cycle: ${row.cycleDays} days", fontSize = 11.sp, color = Color.Gray)
                                                                }
                                                            }
                                                        }
                                                    }
                                                    Divider()
                                                    Text("BRAND RULES", fontWeight = FontWeight.Bold, color = MahalaxmiRed)
                                                    if (returnRulesLoading) {
                                                        Text("Loading brand rules…")
                                                    } else if (brandRules.isEmpty()) {
                                                        Text("Himalaya — 60 days\nKellogg's — 30 days", fontSize = 13.sp)
                                                        Text("Live brand rules could not be confirmed; showing configured defaults.", fontSize = 11.sp, color = Color.Gray)
                                                    } else {
                                                        brandRules.forEach { rule ->
                                                            Text("${rule.matchValue}: ${rule.returnDays} days", fontWeight = FontWeight.SemiBold)
                                                        }
                                                    }
                                                }
                                            },
                                            confirmButton = { TextButton(onClick = { showReturnRules = false }) { Text("CLOSE") } }
                                        )
                                    }
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            Text("CURRENT STORE", color = Color.Gray, fontSize = 10.sp)
                                            Text(if (store.isBlank()) "Select store in Settings" else store, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = code, onValueChange = { code = it },
                                            label = { Text("SKU code / scanned barcode") },
                                            modifier = Modifier.weight(1f), singleLine = true
                                        )
                                        Button(
                                            onClick = {
                                                GmsBarcodeScanning.getClient(this@MainActivity)
                                                    .startScan()
                                                    .addOnSuccessListener { barcode ->
                                                        barcode.rawValue?.let { scannedValue = it }
                                                    }
                                                    .addOnCanceledListener { message = "Scan cancelled" }
                                                    .addOnFailureListener { e -> message = "Scanner error: ${e.localizedMessage ?: "Please try again"}" }
                                            },
                                            modifier = Modifier.size(width = 60.dp, height = 56.dp),
                                            contentPadding = PaddingValues(0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed)
                                        ) { Text("📷", fontSize = 24.sp) }
                                    }
                                    OutlinedTextField(
                                        value = name, onValueChange = { },
                                        label = { Text("Product name (auto-filled)") },
                                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                                        readOnly = true
                                    )
                                    OutlinedTextField(
                                        value = category, onValueChange = { },
                                        label = { Text("Category (from product master)") },
                                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                                        readOnly = true
                                    )
                                    OutlinedTextField(
                                        value = qty, onValueChange = { qty = it.filter(Char::isDigit) },
                                        label = { Text("Quantity") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(), singleLine = true
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                if (returnRules.isEmpty() && !returnRulesLoading) {
                                                    runCatching { returnRules = syncRepository.fetchReturnRules() }
                                                }
                                                val current = runCatching { LocalDate.parse(expiry) }.getOrDefault(LocalDate.now(IndiaZone))
                                                val picker = android.app.DatePickerDialog(
                                                    this@MainActivity,
                                                    { _, year, month, day ->
                                                        expiry = LocalDate.of(year, month + 1, day).format(DateTimeFormatter.ISO_DATE)
                                                    },
                                                    current.year, current.monthValue - 1, current.dayOfMonth
                                                )
                                                val rule = categoryRuleFor(category, returnRules)
                                                if (rule != null) {
                                                    val maxAllowed = LocalDate.now(IndiaZone).plusDays(rule.returnDays.toLong())
                                                    picker.datePicker.maxDate = maxAllowed.atStartOfDay(IndiaZone).toInstant().toEpochMilli()
                                                }
                                                picker.show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Expiry date: $expiry  •  Choose date") }

                                    Button(
                                        onClick = {
                                            val enteredCategory = normalizedRuleName(category)
                                            val allowedToday = todaySchedule.map { normalizedRuleName(it.baseCategory) }
                                            if (enteredCategory.isNotBlank() && enteredCategory !in allowedToday) {
                                                categoryMismatchPending = true
                                            } else {
                                                saveExpiryEntry()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed)
                                    ) { Text("SAVE ENTRY", fontWeight = FontWeight.Bold) }
                                    if (message.isNotBlank()) Text(message, color = MahalaxmiRed, fontSize = 13.sp)
                                }
                            }

                            AppScreen.Lookup -> {
                                val uniqueCategoryProducts = remember(todayCategoryProducts) {
                                    todayCategoryProducts.distinctBy { it.skuName.trim().lowercase() }
                                }
                                val filteredCategoryProducts = remember(uniqueCategoryProducts, lookupQuery) {
                                    if (lookupQuery.isBlank()) uniqueCategoryProducts
                                    else uniqueCategoryProducts.filter { product ->
                                        product.skuName.contains(lookupQuery, ignoreCase = true) ||
                                            product.skuCode.contains(lookupQuery, ignoreCase = true) ||
                                            product.scanCode.contains(lookupQuery, ignoreCase = true)
                                    }
                                }
                                Column(
                                    Modifier.fillMaxSize().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    todaySchedule.forEach { row ->
                                        Text(row.baseCategory, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedTextField(
                                        value = lookupQuery,
                                        onValueChange = { lookupQuery = it },
                                        label = { Text("SKU search") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    if (filteredCategoryProducts.isEmpty()) {
                                        Text(
                                            if (todayCategoryProducts.isEmpty()) "No products in today's category."
                                            else "No matching products.",
                                            color = Color.Gray,
                                            fontSize = 13.sp
                                        )
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            items(filteredCategoryProducts, key = { "category-${it.rowId}" }) { product ->
                                                Card(
                                                    Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Column(Modifier.padding(10.dp)) {
                                                        Text(product.skuName, fontWeight = FontWeight.SemiBold)
                                                        Text(product.skuCode, fontSize = 12.sp, color = Color.Gray)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            AppScreen.History -> {
                                Column(Modifier.fillMaxSize().padding(16.dp)) {
                                    Text("Entry History", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("Today's entries: ${currentSessionEntries.size}", color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                                    Button(onClick = { shareCsv(currentSessionEntries) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed)) { Text("EXPORT CSV / SHARE") }
                                    Button(
                                        onClick = {
                                            if (!isUploading) {
                                                scope.launch {
                                                    isUploading = true
                                                    uploadMessage = "Uploading pending expiry records to Supabase…"
                                                    try {
                                                        val result = syncRepository.uploadPendingEntries()
                                                        uploadMessage = result.message
                                                    } catch (error: Exception) {
                                                        uploadMessage = "Upload failed; records remain pending. ${error.message ?: "Check internet, table and permissions."}"
                                                    } finally {
                                                        isUploading = false
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isUploading,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))
                                    ) { Text(if (isUploading) "UPLOADING…" else "UPLOAD TO SUPABASE") }
                                    Text(uploadMessage, color = if (uploadMessage.startsWith("Upload failed")) MahalaxmiRed else Color.Gray, fontSize = 12.sp)
                                    if (currentSessionEntries.isEmpty()) {
                                        Text("No entries for the current day yet.")
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(currentSessionEntries, key = { it.id }) { item ->
                                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                                    Column(Modifier.padding(12.dp)) {
                                                        Text(item.productName, fontWeight = FontWeight.Bold)
                                                        Text("SKU code: ${item.skuCode}")
                                                        Text("Ean/SKU Scanned: ${item.scannedCode}")
                                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                            Text("Qty: ${item.quantity}", color = MahalaxmiRed, fontWeight = FontWeight.Bold)
                                                            Spacer(Modifier.width(12.dp))
                                                            Text("Expiry: ${item.expiryDate}")
                                                        }
                                                        Text("Category: ${item.category.ifBlank { "Pending verification" }}")
                                                        TextButton(onClick = { pendingDelete = item }) {
                                                            Text("Delete entry", color = MahalaxmiRed)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            AppScreen.Settings -> {
                                Column(Modifier.fillMaxSize().padding(16.dp)) {
                                    Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("Product master import and local data", color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("SUPABASE SYNC", fontWeight = FontWeight.Bold, color = MahalaxmiRed)
                                            Text(syncMessage, fontSize = 13.sp)
                                            if (isSyncing || syncTotal > 0) {
                                                val progress = if (syncTotal > 0) (syncCompleted.toFloat() / syncTotal.toFloat()).coerceIn(0f, 1f) else 0f
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = MahalaxmiRed
                                                )
                                                Text(
                                                    if (syncTotal > 0) "$syncPhase: ${syncCompleted.coerceAtMost(syncTotal)} / $syncTotal rows  •  ${(progress * 100).toInt()}%"
                                                    else syncPhase,
                                                    fontSize = 12.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    if (!isSyncing) {
                                                        scope.launch {
                                                            isSyncing = true
                                                            syncCompleted = 0
                                                            syncTotal = 0
                                                            syncPhase = "Connecting to Supabase…"
                                                            syncMessage = "Importing product master… expiry entries stay on this device."
                                                            try {
                                                                val result = syncRepository.sync { progress ->
                                                                    syncCompleted = progress.completed
                                                                    syncTotal = progress.total
                                                                    syncPhase = progress.phase
                                                                }
                                                                syncMessage = result.message
                                                                syncPhase = "Complete"
                                                                syncCompleted = result.productsUpdated
                                                                syncTotal = result.productsUpdated
                                                            } catch (error: Exception) {
                                                                syncMessage = error.message ?: "Sync failed. Check internet and Supabase configuration."
                                                                syncPhase = "Sync failed"
                                                            } finally {
                                                                isSyncing = false
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = !isSyncing,
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed)
                                            ) {
                                                Text(if (isSyncing) "SYNCING…" else "SYNC NOW")
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = store,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Store name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    OutlinedButton(
                                        onClick = { storePinInput = ""; storePinError = ""; showStorePinDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("CHANGE STORE NAME (PIN REQUIRED)") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
