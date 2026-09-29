package com.mhdigitalpoint.myexpenses

import android.app.*
import android.content.*
import android.database.sqlite.*
import android.graphics.pdf.PdfDocument
import android.graphics.Paint
import android.net.Uri
import android.os.*
import android.print.*
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.OutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.round

private data class Entry(val id: Long, val category: String, val amount: Double, val type: String, val date: String, val created: Long, val note: String)
private data class LedgerRow(val date: String, val category: String, val note: String, val receive: Double?, val expense: Double?, val balance: Double, val opening: Boolean = false)

private class DB(context: Context) : SQLiteOpenHelper(context, "myexpenses.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, particular TEXT NOT NULL, amount REAL NOT NULL, type TEXT NOT NULL, date TEXT NOT NULL, created_at INTEGER NOT NULL, note TEXT)")
        db.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun all(where: String = "", args: Array<String> = emptyArray(), order: String = "date DESC, created_at DESC"): List<Entry> {
        val c = readableDatabase.query("entries", null, where.ifBlank { null }, if (where.isBlank()) null else args, null, null, order)
        val out = mutableListOf<Entry>()
        c.use { while (it.moveToNext()) out += Entry(it.getLong(0), it.getString(1), it.getDouble(2), it.getString(3), it.getString(4), it.getLong(5), it.getString(6)) }
        return out
    }

    fun insert(category: String, amount: Double, type: String, date: String, note: String) {
        writableDatabase.execSQL("INSERT INTO entries(particular,amount,type,date,created_at,note) VALUES(?,?,?,?,?,?)", arrayOf(category, amount, type, date, System.currentTimeMillis(), note))
    }

    fun update(e: Entry) {
        writableDatabase.execSQL("UPDATE entries SET particular=?,amount=?,type=?,date=?,note=? WHERE id=?", arrayOf(e.category, e.amount, e.type, e.date, e.note, e.id))
    }

    fun delete(id: Long) {
        writableDatabase.delete("entries", "id=?", arrayOf(id.toString()))
    }

    fun clearMonth(prefix: String) {
        writableDatabase.delete("entries", "date LIKE ?", arrayOf("$prefix%"))
    }

    fun categories(): List<String> {
        val c = readableDatabase.query("settings", arrayOf("v"), "k=?", arrayOf("categories"), null, null, null)
        c.use { if (it.moveToFirst()) return it.getString(0).split("|").filter { s -> s.isNotBlank() } }
        return emptyList()
    }

    fun saveCategories(list: List<String>) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO settings(k,v) VALUES(?,?)", arrayOf("categories", list.joinToString("|")))
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var db: DB
    private val prefs by lazy { getSharedPreferences("prefs", MODE_PRIVATE) }
    private var pendingAction: String? = null
    private val createFile = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri -> if (uri != null) pendingAction?.let { doCreate(uri, it) }; pendingAction = null }
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restore(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DB(this)
        setContent {
            MyExpensesApp(db, prefs, ::saveFile, ::restoreFile, ::printReport, ::printLedger, ::exportSummary, ::exportLedger, ::savePdf)
        }
    }

    private fun saveFile(kind: String) {
        pendingAction = kind
        val name = when (kind) {
            "backup" -> "MyExpenses_Backup-${today()}.json"
            "summary" -> "MyExpenses_Summary.csv"
            "ledger" -> "MyExpenses_Ledger.csv"
            else -> "MyExpenses.pdf"
        }
        createFile.launch(name)
    }

    private fun restoreFile() {
        openFile.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    private fun doCreate(uri: Uri, kind: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                when (kind) {
                    "backup" -> out.write(backupJson().toByteArray(Charsets.UTF_8))
                    "summary" -> out.write(summaryCsv().toByteArray(Charsets.UTF_8))
                    "ledger" -> out.write(ledgerCsv().toByteArray(Charsets.UTF_8))
                    "pdf" -> makePdf().writeTo(out)
                }
            }
            toast("File saved successfully")
        } catch (e: Exception) {
            toast("Error: ${e.message}")
        }
    }

    private fun restore(uri: Uri) {
        try {
            val s = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: (throw Exception())
            val db64 = Regex("\\\"database\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(s)?.groupValues?.get(1) ?: return
            val path = getDatabasePath("myexpenses.db").path
            val bytes = android.util.Base64.decode(db64, android.util.Base64.DEFAULT)
            File(path).writeBytes(bytes)
            toast("Restored successfully")
        } catch (e: Exception) {
            toast("Error restoring")
        }
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun month() = today().substring(0, 7)
    private fun entries(): List<Entry> = db.all()

    private fun opening(date: String): Double {
        var b = 0.0
        db.all("date < ?", arrayOf(date), "date ASC, created_at ASC").forEach { b += if (it.type == "income") it.amount else -it.amount }
        return b
    }

    private var filterFrom: String? = null
    private var filterTo: String? = null

    private fun filtered(): List<Entry> {
        val f = filterFrom
        val t = filterTo
        return db.all(buildString {
            if (f != null) append("date >= ? ")
            if (t != null) { if (isNotEmpty()) append("AND "); append("date <= ?") }
        }, listOfNotNull(f, t).toTypedArray())
    }

    private fun ledger(): List<LedgerRow> {
        var b = if (filterFrom != null) opening(filterFrom!!) else 0.0
        val rows = mutableListOf(LedgerRow(filterFrom ?: "Beginning", "Opening Balance", "", null, null, b, true))
        filtered().forEach {
            b += if (it.type == "income") it.amount else -it.amount
            rows += LedgerRow(it.date, it.category, it.note, if (it.type == "income") it.amount else null, if (it.type == "income") null else it.amount, b)
        }
        return rows
    }

    private fun money(v: Double) = "৳" + String.format(Locale.US, "%,.2f", v)

    private fun backupJson(): String {
        val dbBytes = SQLiteDatabase.openDatabase(getDatabasePath("myexpenses.db").path, null, SQLiteDatabase.OPEN_READONLY).let { it.close(); getDatabasePath("myexpenses.db").readBytes() }
        val db64 = android.util.Base64.encodeToString(dbBytes, android.util.Base64.DEFAULT)
        return """{"database":"$db64"}"""
    }

    private fun summaryCsv(): String {
        val list = filtered()
        val map = linkedMapOf<String, DoubleArray>()
        list.forEach { val x = map.getOrPut(it.category) { doubleArrayOf(0.0, 0.0) }; x[if (it.type == "income") 0 else 1] += it.amount }
        return buildString {
            append("Category,Income,Expense\r\n")
            map.forEach { (k, v) -> append(csv(k)).append(',').append(v[0]).append(',').append(v[1]).append("\r\n") }
        }
    }

    private fun ledgerCsv(): String = buildString {
        append("Date,Category,Note,Receive,Expense,Balance\r\n")
        ledger().forEach { append(csv(it.date)).append(',').append(csv(it.category)).append(',').append(csv(it.note)).append(',').append(it.receive ?: "").append(',').append(it.expense ?: "").append(',').append(it.balance).append("\r\n") }
    }

    private fun csv(s: String) = if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"${s.replace("\"", "\"\"")}\"" else s

    private fun makePdf(): PdfDocument {
        val p = PdfDocument()
        val page = p.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas
        val paint = Paint().apply { color = android.graphics.Color.BLACK; textSize = 12f }
        var y = 40f
        c.drawText("MyExpenses - Summary Report", 40f, y, paint)
        y += 30
        c.drawText("Period: ${filterFrom ?: "Start"} to ${filterTo ?: "Today"}", 40f, y, paint)
        y += 30
        filtered().groupBy { it.category }.forEach { (cat, entries) ->
            val sum = entries.sumOf { if (it.type == "income") it.amount else -it.amount }
            c.drawText("$cat: $sum", 40f, y, paint)
            y += 20
            if (y > 800) { p.finishPage(page); y = 40f }
        }
        p.finishPage(page)
        return p
    }

    private fun printReport() {
        val list = filtered()
        val text = buildString {
            append("MyExpenses - Summary Report\n\n")
            append("Period: ${filterFrom ?: "Start"} to ${filterTo ?: "Today"}\n\n")
            list.groupBy { it.category }.forEach { (cat, entries) ->
                append("$cat: ${money(entries.sumOf { if (it.type == "income") it.amount else -it.amount })}\n")
            }
        }
        printText("Summary Report", text)
    }

    private fun printLedger() {
        val text = buildString {
            append("MyExpenses - Account Ledger\n\n")
            ledger().forEach { append("${it.date} | ${it.category} | ${money(it.receive ?: 0.0)} | ${money(it.expense ?: 0.0)} | ${money(it.balance)}\n") }
        }
        printText("Account Ledger", text)
    }

    private fun printText(title: String, text: String) {
        val pm = getSystemService(PRINT_SERVICE) as PrintManager
        pm.print(title, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancellation: CancellationSignal?, callback: LayoutResultCallback?, extras: Bundle?) {
                if (cancellation?.isCanceled == true) { callback?.onLayoutCancelled(); return }
                callback?.onLayoutFinished(PrintDocumentInfo.Builder("document").setPageCount(1).build(), true)
            }

            override fun onWrite(pages: Array<PrintAttributes.PageRange>?, destination: ParcelFileDescriptor?, cancellation: CancellationSignal?, callback: WriteResultCallback?) {
                if (cancellation?.isCanceled == true) { callback?.onWriteCancelled(); return }
                try {
                    val out = ParcelFileDescriptor.AutoCloseOutputStream(destination)
                    out.write(text.toByteArray())
                    out.close()
                    callback?.onWriteFinished(arrayOf(PrintAttributes.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        })
    }

    private fun exportSummary() { saveFile("summary") }
    private fun exportLedger() { saveFile("ledger") }
    private fun savePdf() { saveFile("pdf") }
}

@Composable
fun MyExpensesApp(
    db: DB,
    prefs: android.content.SharedPreferences,
    save: (String) -> Unit,
    restore: () -> Unit,
    printReport: () -> Unit,
    printLedger: () -> Unit,
    exportSummary: () -> Unit,
    exportLedger: () -> Unit,
    savePdf: () -> Unit
) {
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var tab by remember { mutableStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val colors = if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("MyExpenses", fontWeight = FontWeight.ExtraBold) }, actions = {
                IconButton(onClick = { dark = !dark; prefs.edit().putBoolean("dark", dark).apply() }) { Icon(Icons.Default.Brightness4, "Theme") }
            })
        }) { padding ->
            Column(Modifier.padding(padding)) {
                TabRow(tab) {
                    Tab(tab == 0, { tab = 0 }) { Text("Entry") }
                    Tab(tab == 1, { tab = 1 }) { Text("History") }
                    Tab(tab == 2, { tab = 2 }) { Text("Report") }
                }
                when (tab) {
                    0 -> EntryScreen(db, dark) { refresh++ }
                    1 -> HistoryScreen(db) { refresh++ }
                    2 -> ReportScreen(db, save, restore, printReport, printLedger, exportSummary, exportLedger, savePdf) { refresh++ }
                }
            }
        }
    }
}

@Composable
private fun EntryScreen(db: DB, dark: Boolean, onChange: () -> Unit) {
    var type by remember { mutableStateOf("expense") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showCalc by remember { mutableStateOf(false) }
    val cats = loadCats(db)

    if (showCalc) {
        CalculatorDialog({ amount = it.toString(); showCalc = false }, { showCalc = false })
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedButton(onClick = { showCalc = true }, Modifier.fillMaxWidth()) { Text("Calculator") }
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(category, { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
        Row {
            Button(onClick = { type = "income"; }, Modifier.weight(1f)) { Text("Income") }
            Button(onClick = { type = "expense"; }, Modifier.weight(1f)) { Text("Expense") }
        }
        Button(onClick = {
            if (amount.isNotBlank() && category.isNotBlank()) {
                db.insert(category, amount.toDouble(), type, date.ifBlank { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }, note)
                amount = ""
                category = ""
                note = ""
                onChange()
            }
        }, Modifier.fillMaxWidth()) { Text("Add Entry") }
    }
}

@Composable
private fun SummaryMini(label: String, v: Double) {
    Card(Modifier.weight(1f)) {
        Column(Modifier.padding(9.dp)) {
            Text(label, fontSize = 10.sp)
            Text("৳${String.format(Locale.US, "%,.2f", v)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HistoryScreen(db: DB, onChange: () -> Unit) {
    var edit by remember { mutableStateOf<Entry?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val rows = db.all()

    if (edit != null) {
        EditDialog(edit!!, { db.update(it); edit = null; onChange() }, { edit = null })
    }

    LazyColumn {
        items(rows) { e ->
            EntryRow(e, { edit = e }, { db.delete(e.id); onChange() })
        }
    }
}

@Composable
private fun EntryRow(e: Entry, onEdit: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${e.date} | ${e.category}", fontWeight = FontWeight.Bold)
                Text(e.note, fontSize = 12.sp)
            }
            Text(String.format(Locale.US, "%,.2f", e.amount))
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF3B82F6)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFF9CA3AF)) }
        }
    }
}

@Composable
private fun ReportScreen(
    db: DB,
    save: (String) -> Unit,
    restore: () -> Unit,
    printReport: () -> Unit,
    printLedger: () -> Unit,
    exportSummary: () -> Unit,
    exportLedger: () -> Unit,
    savePdf: () -> Unit,
    onChange: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { save("backup") }, Modifier.fillMaxWidth()) { Text("Backup") }
        Button(onClick = { restore() }, Modifier.fillMaxWidth()) { Text("Restore") }
        Button(onClick = { printReport() }, Modifier.fillMaxWidth()) { Text("Print Report") }
        Button(onClick = { exportSummary() }, Modifier.fillMaxWidth()) { Text("Export Summary") }
        Button(onClick = { exportLedger() }, Modifier.fillMaxWidth()) { Text("Export Ledger") }
    }
}

@Composable
private fun CalculatorDialog(set: (Double) -> Unit, close: () -> Unit) {
    var exp by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Calculator") },
        text = {
            Column {
                OutlinedTextField(exp, { exp = it }, Modifier.fillMaxWidth())
                Row {
                    Button(onClick = { exp += "+" }) { Text("+") }
                    Button(onClick = { exp += "-" }) { Text("-") }
                    Button(onClick = { exp += "*" }) { Text("*") }
                    Button(onClick = { exp += "/" }) { Text("/") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { try { set(eval(exp)); close() } catch (e: Exception) {} }) { Text("OK") }
        }
    )
}

private fun eval(s: String): Double {
    if (s.isBlank() || !s.matches(Regex("[0-9+\\-*/. ()]+"))) throw Exception()
    val parts = Regex("(?<=[+\\-*/])|(?=[+\\-*/])").split(s).filter { it.isNotBlank() }
    var r = parts.getOrNull(0)?.toDouble() ?: 0.0
    var op = "+"
    parts.drop(1).forEach {
        if (it in listOf("+", "-", "*", "/")) op = it else {
            r = when (op) {
                "+" -> r + it.toDouble()
                "-" -> r - it.toDouble()
                "*" -> r * it.toDouble()
                "/" -> r / it.toDouble()
                else -> r
            }
        }
    }
    return r
}

@Composable
private fun CategoryDialog(cats: List<String>, save: (List<String>) -> Unit, close: () -> Unit) {
    var list by remember { mutableStateOf(cats) }
    var new by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Categories") },
        text = {
            Column {
                OutlinedTextField(new, { new = it }, label = { Text("New Category") })
                Button(onClick = { if (new.isNotBlank()) { list = list + new; new = "" } }) { Text("Add") }
                LazyColumn {
                    items(list) { cat ->
                        Row {
                            Text(cat, Modifier.weight(1f))
                            IconButton(onClick = { list = list.filter { it != cat } }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { save(list); close() }) { Text("OK") } }
    )
}

@Composable
private fun EditDialog(e: Entry, save: (Entry) -> Unit, close: () -> Unit) {
    var cat by remember { mutableStateOf(e.category) }
    var amt by remember { mutableStateOf(e.amount.toString()) }
    var type by remember { mutableStateOf(e.type) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Edit Entry") },
        text = {
            Column {
                OutlinedTextField(cat, { cat = it }, label = { Text("Category") })
                OutlinedTextField(amt, { amt = it }, label = { Text("Amount") })
                Row {
                    Button(onClick = { type = "income" }) { Text("Income") }
                    Button(onClick = { type = "expense" }) { Text("Expense") }
                }
            }
        },
        confirmButton = {
            Button(onClick = { save(e.copy(category = cat, amount = amt.toDoubleOrNull() ?: 0.0, type = type)); close() }) { Text("Save") }
        }
    )
}

private fun loadCats(db: DB): List<String> {
    val saved = db.categories()
    return if (saved.isEmpty()) listOf("Salary", "Food", "Transport", "Shopping", "Bills", "Health", "Entertainment", "Investment", "Others") else saved
}

private fun saveCats(db: DB, c: List<String>) {
    db.saveCategories(c)
}

private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
private fun money(v: Double) = "৳" + String.format(Locale.US, "%,.2f", v)
private fun Double.round(n: Int) = String.format(Locale.US, "%.${n}f", this).toDouble()
