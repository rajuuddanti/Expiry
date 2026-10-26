package com.mahalaxmi.expiry

import android.os.Bundle
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MahalaxmiRed = Color(0xFFB71C1C)
private val IndiaZone = ZoneId.of("Asia/Kolkata")
private val stores = listOf(
    "Dharmaram", "Gharshakurthy", "Choppadandi", "Vidya Nagar", "Mallial",
    "Pegadapally (Old)", "Kapuwada", "Pegadapally (New)", "Burugupally",
    "Keshavapatnam", "Gangadhara", "Koheda", "Ellanthakunta",
    "Gangadhara New", "Gopalrao Pet", "Vemulawada"
)

private enum class AppScreen { Scan, History, Settings }

class MainActivity : ComponentActivity() {
    private var scannedValue by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("expiry_manager", MODE_PRIVATE)
        val database = ExpiryDatabase.getInstance(applicationContext)
        val dao = database.expiryDao()
        setContent {
            val scope = rememberCoroutineScope()
            val entries by dao.observeEntries().collectAsState(initial = emptyList())
            var store by remember { mutableStateOf(prefs.getString("store", "") ?: "") }
            var screen by remember { mutableStateOf(AppScreen.Scan) }
            var code by remember { mutableStateOf("") }
            var name by remember { mutableStateOf("") }
            var ean by remember { mutableStateOf("") }
            var qty by remember { mutableStateOf("1") }
            var expiry by remember { mutableStateOf(LocalDate.now(IndiaZone).format(DateTimeFormatter.ISO_DATE)) }
            var message by remember { mutableStateOf("") }

            LaunchedEffect(scannedValue) {
                if (scannedValue.isNotBlank()) {
                    code = scannedValue
                    ean = scannedValue
                    scannedValue = ""
                }
            }

            LaunchedEffect(code, ean) {
                // Resolve either the SKU/barcode field or the separate EAN field.
                val lookupCodes = listOf(code.trim(), ean.trim())
                    .filter { it.isNotBlank() }
                    .distinct()
                var matchedProduct: com.mahalaxmi.expiry.data.ProductMasterEntity? = null
                var matchedCode = ""
                for (lookupCode in lookupCodes) {
                    matchedProduct = runCatching { dao.findProductByScanCode(lookupCode) }.getOrNull()
                        ?: runCatching { dao.findProductBySku(lookupCode) }.getOrNull()
                    if (matchedProduct != null) {
                        matchedCode = lookupCode
                        break
                    }
                }
                if (matchedProduct != null) {
                    name = matchedProduct.skuName
                    if (matchedCode != matchedProduct.skuCode && ean.isBlank()) {
                        ean = matchedCode
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
                                        value = name, onValueChange = { name = it },
                                        label = { Text("Product name") },
                                        modifier = Modifier.fillMaxWidth(), singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = ean, onValueChange = { ean = it },
                                        label = { Text("EAN") },
                                        modifier = Modifier.fillMaxWidth(), singleLine = true
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
                                                            skuCode = code.trim(),
                                                            productName = name.trim(),
                                                            scannedCode = ean.trim().ifBlank { code.trim() },
                                                            quantity = q,
                                                            expiryDate = expiry,
                                                            sessionStart = session,
                                                            createdAt = now.atZone(IndiaZone).toOffsetDateTime().toString()
                                                        )
                                                    )
                                                    code = ""; name = ""; ean = ""; qty = "1"
                                                    message = "Entry saved offline. It will remain on this device until synced."
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed)
                                    ) { Text("SAVE ENTRY", fontWeight = FontWeight.Bold) }
                                    if (message.isNotBlank()) Text(message, color = MahalaxmiRed, fontSize = 13.sp)
                                }
                            }

                            AppScreen.History -> {
                                Column(Modifier.fillMaxSize().padding(16.dp)) {
                                    Text("Entry History", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("Recent entries: ${entries.size}", color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                                    if (entries.isEmpty()) {
                                        Text("No entries yet. Saved entries will appear here.")
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(entries, key = { it.id }) { item ->
                                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                                                    Column(Modifier.padding(12.dp)) {
                                                        Text(item.productName, fontWeight = FontWeight.Bold)
                                                        Text("Store: ${item.store}")
                                                        Text("SKU: ${item.skuCode}    Scanned: ${item.scannedCode}")
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
                                    Text("Select the store this device belongs to.", color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(stores) { s ->
                                            OutlinedButton(
                                                onClick = {
                                                    store = s
                                                    prefs.edit().putString("store", s).apply()
                                                    message = "Store changed to $s"
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = if (store == s) ButtonDefaults.outlinedButtonColors(contentColor = MahalaxmiRed) else ButtonDefaults.outlinedButtonColors()
                                            ) {
                                                Text(if (store == s) "✓  $s" else s)
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
