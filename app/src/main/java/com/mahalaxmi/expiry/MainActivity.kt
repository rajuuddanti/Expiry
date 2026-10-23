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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private val MahalaxmiRed = Color(0xFFB71C1C)
private val IndiaZone = ZoneId.of("Asia/Kolkata")
private val stores = listOf("Dharmaram", "Gharshakurthy", "Choppadandi", "Vidya Nagar", "Mallial", "Pegadapally (Old)", "Kapuwada", "Pegadapally (New)", "Burugupally", "Keshavapatnam", "Gangadhara", "Koheda", "Ellanthakunta", "Gangadhara New", "Gopalrao Pet", "Vemulawada")

data class ExpiryItem(val id: String = UUID.randomUUID().toString(), val store: String, val sku: String, val name: String, val ean: String, val qty: Int, val expiry: String, val session: String)

class MainActivity : ComponentActivity() {
    private var scannedValue by mutableStateOf("")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("expiry_manager", MODE_PRIVATE)
        setContent {
            var store by remember { mutableStateOf(prefs.getString("store", "") ?: "") }
            var code by remember { mutableStateOf("") }
            var name by remember { mutableStateOf("") }
            var ean by remember { mutableStateOf("") }
            var qty by remember { mutableStateOf("1") }
            var expiry by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
            var items by remember { mutableStateOf(listOf<ExpiryItem>()) }
            var showStorePicker by remember { mutableStateOf(store.isBlank()) }
            var showHistory by remember { mutableStateOf(false) }
            LaunchedEffect(scannedValue) { if (scannedValue.isNotBlank()) { code = scannedValue; ean = scannedValue; scannedValue = "" } }
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFFF7F7F7))) {
                    Column(Modifier.fillMaxWidth().background(MahalaxmiRed).padding(18.dp)) {
                        Text("MAHALAXMI MAHAMART", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("EXPIRY MANAGER", color = Color.White, fontSize = 14.sp)
                    }
                    if (showStorePicker) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Select your store", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            stores.forEach { s -> OutlinedButton(onClick = { store = s; prefs.edit().putString("store", s).apply(); showStorePicker = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(s) } }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("STORE", color = Color.Gray, fontSize = 11.sp); Text(store, fontWeight = FontWeight.Bold) }
                            TextButton(onClick = { showStorePicker = true }) { Text("Change store") }
                        }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { val scanner = GmsBarcodeScanning.getClient(this@MainActivity); scanner.startScan().addOnSuccessListener { b -> b.rawValue?.let { scannedValue = it } } }, colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed), modifier = Modifier.weight(1f)) { Text("SCAN BARCODE") }
                            OutlinedButton(onClick = { showHistory = !showHistory }, modifier = Modifier.weight(1f)) { Text(if (showHistory) "New Entry" else "History") }
                        }
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(code, { code = it }, label = { Text("SKU Code or scanned barcode") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(name, { name = it }, label = { Text("Product name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(ean, { ean = it }, label = { Text("EAN (if available)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(qty, { qty = it.filter(Char::isDigit) }, label = { Text("Quantity") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(expiry, { expiry = it }, label = { Text("Expiry date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Text("Choose the expiry date in YYYY-MM-DD format. Calendar picker will be added in the next UI pass.", fontSize = 12.sp, color = Color.Gray)
                            Button(onClick = {
                                val q = qty.toIntOrNull()
                                val validDate = runCatching { LocalDate.parse(expiry) }.isSuccess
                                if (code.isNotBlank() && name.isNotBlank() && q != null && q > 0 && validDate) {
                                    val now = LocalDateTime.now(IndiaZone)
                                    val sessionDate = if (now.hour < 15) now.toLocalDate().minusDays(1) else now.toLocalDate()
                                    val session = sessionDate.atTime(15, 0).atZone(IndiaZone).toOffsetDateTime().toString()
                                    items = listOf(ExpiryItem(store = store, sku = code.trim(), name = name.trim(), ean = ean.trim(), qty = q, expiry = expiry, session = session)) + items
                                    code = ""; name = ""; ean = ""; qty = "1"
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MahalaxmiRed)) { Text("SAVE ENTRY") }
                        }
                        if (showHistory) {
                            Text("Entries this session: ${items.size}", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold)
                            LazyColumn(Modifier.padding(horizontal = 16.dp)) {
                                items(items) { item ->
                                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(item.name, fontWeight = FontWeight.Bold)
                                            Text("SKU: ${item.sku}   Qty: ${item.qty}")
                                            Text("Expiry: ${item.expiry}")
                                            TextButton(onClick = { items = items.filterNot { it.id == item.id } }) { Text("Delete entry", color = MahalaxmiRed) }
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
