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
            val syncRepository = remember { SupabaseSyncRepository(applicationContext, dao) }
            var syncMessage by remember { mutableStateOf("Not synced yet") }
            var isSyncing by remember { mutableStateOf(false) }
            var isUploading by remember { mutableStateOf(false) }
            var uploadMessage by remember { mutableStateOf("Expiry entries have not been uploaded yet.") }
            var store by remember { mutableStateOf(prefs.getString("store", "") ?: "") }
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

            MaterialTheme {
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
                                    Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Expiry Entry", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        TextButton(onClick = {
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
                                        }) { Text("ⓘ Return Rules", color = MahalaxmiRed, fontWeight = FontWeight.Bold) }
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
                                        Column(Modifier.padding(14.dp)) {
                                            Text("CURRENT STORE", color = Color.Gray, fontSize = 11.sp)
                                            Text(if (store.isBlank()) "Select store in Settings" else store, fontWeight = FontWeight.Bold)
                                        }
                                    }
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
                                        modifier = Modifier.fillMaxWidth().height(56.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed)
                                    ) { Text("📷  SCAN WITH CAMERA", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

                                    OutlinedTextField(
                                        value = code, onValueChange = { code = it },
                                        label = { Text("SKU code / scanned barcode") },
                                        modifier = Modifier.fillMaxWidth(), singleLine = true
                                    )
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
                                            val current = runCatching { LocalDate.parse(expiry) }.getOrDefault(LocalDate.now(IndiaZone))
                                            val picker = android.app.DatePickerDialog(
                                                this@MainActivity,
                                                { _, year, month, day ->
                                                    expiry = LocalDate.of(year, month + 1, day).format(DateTimeFormatter.ISO_DATE)
                                                },
                                                current.year, current.monthValue - 1, current.dayOfMonth
                                            )
                                            picker.show()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Expiry date: $expiry  •  Choose date") }

                                    Button(
                                        onClick = {
                                            val q = qty.toIntOrNull()
                                            val validDate = runCatching { LocalDate.parse(expiry) }.isSuccess
                                            if (store.isBlank()) {
                                                message = "Please select your store in Settings."
                                            } else if (code.isBlank() || name.isBlank() || q == null || q <= 0 || !validDate) {
                                                message = "Enter product, quantity and expiry date correctly."
                                            } else {
                                                val now = LocalDateTime.now(IndiaZone)
                                                val sessionDate = if (now.hour < 15) now.toLocalDate().minusDays(1) else now.toLocalDate()
                                                val session = sessionDate.atTime(15, 0).atZone(IndiaZone).toOffsetDateTime().toString()
                                                scope.launch {
                                                    dao.insertEntry(
                                                        ExpiryEntryEntity(
                                                            id = java.util.UUID.randomUUID().toString(),
                                                            store = store,
                                                            skuCode = (runCatching { dao.findProductByScanCode(code.trim()) }.getOrNull() ?: runCatching { dao.findProductBySku(code.trim()) }.getOrNull())?.skuCode ?: code.trim(),
                                                            productName = name.trim(),
                                                            scannedCode = code.trim(),
                                                            category = (runCatching { dao.findProductByScanCode(code.trim()) }.getOrNull()
                                                                ?: runCatching { dao.findProductBySku(code.trim()) }.getOrNull())?.category.orEmpty(),
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
                                        },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed)
                                    ) { Text("SAVE ENTRY", fontWeight = FontWeight.Bold) }
                                    if (message.isNotBlank()) Text(message, color = MahalaxmiRed, fontSize = 13.sp)
                                }
                            }

                            AppScreen.Lookup -> {
                                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("SKU Lookup", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("TODAY'S CATEGORY LIST • ${LocalDate.now(IndiaZone)}", fontWeight = FontWeight.Bold, color = MahalaxmiRed)
                                    if (todaySchedule.isEmpty()) {
                                        Text("No categories are scheduled for today.", color = Color.Gray, fontSize = 12.sp)
                                    } else {
                                        todaySchedule.forEach { row ->
                                            Text("${row.baseCategory} • ${todayCategoryProducts.count { normalizedRuleName(it.category.orEmpty()) == normalizedRuleName(row.baseCategory) }} SKU(s)", fontWeight = FontWeight.SemiBold)
                                        }
                                        if (todayCategoryProducts.isEmpty()) {
                                            Text("No products matched today's scheduled categories in the locally synced master.", color = Color.Gray, fontSize = 12.sp)
                                        } else {
                                            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                items(todayCategoryProducts, key = { "today-${it.rowId}" }) { product ->
                                                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                                                        Column(Modifier.padding(8.dp)) {
                                                            Text(product.skuName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                            Text("SKU: ${product.skuCode} • ${product.category.orEmpty()}", fontSize = 11.sp, color = Color.Gray)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Divider()
                                    Text("SEARCH A PARTICULAR PRODUCT", fontWeight = FontWeight.Bold)
                                    Text("Search MASTER by product name, SKU code, or EAN/barcode. SKU and EAN codes share SkuCode; duplicate rows are preserved.",
                                        color = Color.Gray, fontSize = 12.sp)
                                    OutlinedTextField(
                                        value = lookupQuery,
                                        onValueChange = { value ->
                                            lookupQuery = value
                                            scope.launch {
                                                if (value.isBlank()) {
                                                    lookupResults = emptyList()
                                                    lookupMessage = "Search by product name, SKU code, or EAN/barcode."
                                                } else {
                                                    lookupResults = runCatching { dao.searchProducts(value.trim()) }.getOrDefault(emptyList())
                                                    lookupMessage = if (lookupResults.isEmpty()) "No matching master rows. Check sync or verify this code manually." else "${lookupResults.size} result(s), in master source order."
                                                }
                                            }
                                        },
                                        label = { Text("Product name / SKU / EAN") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    Text(lookupMessage, color = Color.Gray, fontSize = 12.sp)
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(lookupResults, key = { it.rowId }) { product ->
                                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Text(product.skuName, fontWeight = FontWeight.Bold)
                                                    Text("SKU (FCFS): ${product.skuCode}")
                                                    Text("Matched master code: ${product.scanCode}")
                                                    Text("Category: ${product.category ?: "Pending verification"}")
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            AppScreen.History -> {
                                Column(Modifier.fillMaxSize().padding(16.dp)) {
                                    Text("Entry History", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("Recent entries: ${entries.size}", color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                                    Button(onClick = { shareCsv(entries) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed)) { Text("EXPORT CSV / SHARE") }
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
                                    if (entries.isEmpty()) {
                                        Text("No entries yet. Saved entries will appear here.")
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(entries, key = { it.id }) { item ->
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
                                            Button(
                                                onClick = {
                                                    if (!isSyncing) {
                                                        scope.launch {
                                                            isSyncing = true
                                                            syncMessage = "Importing product master from Supabase… expiry entries stay on this device."
                                                            try {
                                                                val result = syncRepository.sync()
                                                                syncMessage = result.message
                                                            } catch (error: Exception) {
                                                                syncMessage = error.message ?: "Sync failed. Check internet and Supabase configuration."
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
                                    OutlinedTextField(value = store, onValueChange = { store = it; prefs.edit().putString("store", it).apply() }, label = { Text("Store name (manual entry)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
