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

class MainActivity : ComponentActivity() {
    private var scannedValue by mutableStateOf("")
    private fun shareCsv(entries: List<ExpiryEntryEntity>) {
        fun escape(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\\n", " ") + "\""
        val header = listOf("Store", "SKU code", "SKU name", "Scanned code", "QTY", "Expiry date")
        val rows = entries.map { listOf(it.store, it.skuCode, it.productName, it.scannedCode, it.quantity.toString(), it.expiryDate) }
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
            var store by remember { mutableStateOf(prefs.getString("store", "") ?: "") }
            var screen by remember { mutableStateOf(AppScreen.Scan) }
            var code by remember { mutableStateOf("") }
            var name by remember { mutableStateOf("") }
            var ean by remember { mutableStateOf("") }
            var qty by remember { mutableStateOf("") }
            var expiry by remember { mutableStateOf(LocalDate.now(IndiaZone).format(DateTimeFormatter.ISO_DATE)) }
            var message by remember { mutableStateOf("") }
            var lookupQuery by remember { mutableStateOf("") }
            var lookupResults by remember { mutableStateOf(emptyList<com.mahalaxmi.expiry.data.ProductMasterEntity>()) }
            var lookupMessage by remember { mutableStateOf("Search by product name, SKU code, or EAN/barcode.") }

            LaunchedEffect(scannedValue) {
                if (scannedValue.isNotBlank()) {
                    code = scannedValue.trim()
                    ean = ""
                    name = ""
                    scannedValue = ""
                }
            }

            LaunchedEffect(code) {
                // SKU/barcode input is the lookup source. Never retain the previous product
                // name or invent an EAN when the current code has no match.
                name = ""
                ean = ""
                val lookupCode = code.trim()
                if (lookupCode.isNotBlank()) {
                    val matchedProduct = runCatching { dao.findProductByScanCode(lookupCode) }.getOrNull()
                        ?: runCatching { dao.findProductBySku(lookupCode) }.getOrNull()
                    if (matchedProduct != null) {
                        name = matchedProduct.skuName
                        // MASTER has no dedicated EAN column, so leave EAN blank unless a
                        // genuine barcode mapping is available from a separate source.
                    }
                }
            }

            MaterialTheme {
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
                                    Modifier.fillMaxSize().padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Expiry Entry", fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                                        value = ean, onValueChange = { },
                                        label = { Text("EAN (when mapped)") },
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
                                                            quantity = q,
                                                            expiryDate = expiry,
                                                            sessionStart = session,
                                                            createdAt = now.atZone(IndiaZone).toOffsetDateTime().toString()
                                                        )
                                                    )
                                                    code = ""; name = ""; ean = ""; qty = "1"
                                                    message = "Entry saved on this device only."
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
                                    if (entries.isEmpty()) {
                                        Text("No entries yet. Saved entries will appear here.")
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(entries, key = { it.id }) { item ->
                                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                                    Column(Modifier.padding(12.dp)) {
                                                        Text(item.productName, fontWeight = FontWeight.Bold)
                                                        Text("Store: ${item.store}")
                                                        Text("SKU code: ${item.skuCode}")
                                                        Text("Scanned code: ${item.scannedCode}")
                                                        Text("Qty: ${item.quantity}    Expiry: ${item.expiryDate}")
                                                        TextButton(onClick = { scope.launch { dao.softDeleteEntry(item.id, LocalDateTime.now(IndiaZone).atZone(IndiaZone).toOffsetDateTime().toString()) } }) {
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
