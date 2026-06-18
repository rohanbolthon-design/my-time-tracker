package com.example

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00B0FF),
                    secondary = Color(0xFF00E5FF),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    error = Color(0xFFEF5350)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TimeTrackerApp()
                }
            }
        }
    }
}

class TimeTrackingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.dao()

    val allRecords: StateFlow<List<TimeRecord>> = dao.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeRecord = MutableStateFlow<TimeRecord?>(null)
    val activeRecord: StateFlow<TimeRecord?> = _activeRecord.asStateFlow()

    private val _currentMonthObligatoryHours = MutableStateFlow(150.0)
    val currentMonthObligatoryHours: StateFlow<Double> = _currentMonthObligatoryHours.asStateFlow()

    val reportWorkedHours = MutableStateFlow(0.0)
    val reportObligatoryHours = MutableStateFlow(0.0)

    init {
        viewModelScope.launch {
            val active = dao.getActiveRecord()
            _activeRecord.value = active
            val today = JalaliCalendarHelper.fromEpochMillis(System.currentTimeMillis())
            val key = "${today.year}/${today.month}"
            val saved = dao.getObligatoryHours(key)
            if (saved != null) {
                _currentMonthObligatoryHours.value = saved.hours
            }
        }
    }

    fun toggleShift() {
        viewModelScope.launch {
            val currentActive = dao.getActiveRecord()
            val now = System.currentTimeMillis()
            val today = JalaliCalendarHelper.fromEpochMillis(now)
            
            if (currentActive == null) {
                val newRecord = TimeRecord(
                    startTimeMillis = now,
                    endTimeMillis = null,
                    jalaliYear = today.year,
                    jalaliMonth = today.month,
                    jalaliDay = today.day
                )
                dao.insertRecord(newRecord)
                _activeRecord.value = newRecord
            } else {
                val updated = currentActive.copy(endTimeMillis = now)
                dao.updateRecord(updated)
                _activeRecord.value = null
            }
        }
    }

    fun addManualRecord(jy: Int, jm: Int, jd: Int, sh: Int, sm: Int, eh: Int, em: Int) {
        viewModelScope.launch {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, sh)
            cal.set(java.util.Calendar.MINUTE, sm)
            val startMs = cal.timeInMillis

            cal.set(java.util.Calendar.HOUR_OF_DAY, eh)
            cal.set(java.util.Calendar.MINUTE, em)
            val endMs = cal.timeInMillis

            val record = TimeRecord(
                startTimeMillis = startMs,
                endTimeMillis = endMs,
                jalaliYear = jy,
                jalaliMonth = jm,
                jalaliDay = jd,
                isManual = true
            )
            dao.insertRecord(record)
        }
    }

    fun deleteRecord(record: TimeRecord) {
        viewModelScope.launch {
            dao.deleteRecord(record)
        }
    }

    fun saveObligatoryHours(year: Int, month: Int, hours: Double) {
        viewModelScope.launch {
            val key = "$year/$month"
            dao.insertObligatoryHours(ObligatoryHours(key, hours))
            _currentMonthObligatoryHours.value = hours
        }
    }

    fun generateReport(sy: Int, sm: Int, sd: Int, ey: Int, em: Int, ed: Int) {
        val records = allRecords.value.filter {
            val dateKey = it.jalaliYear * 10000 + it.jalaliMonth * 100 + it.jalaliDay
            val startKey = sy * 10000 + sm * 100 + sd
            val endKey = ey * 10000 + em * 100 + ed
            dateKey in startKey..endKey
        }
        val totalMs = records.sumOf { (it.endTimeMillis ?: it.startTimeMillis) - it.startTimeMillis }
        reportWorkedHours.value = totalMs.toDouble() / (1000 * 60 * 60)
        reportObligatoryHours.value = _currentMonthObligatoryHours.value
    }

    fun exportMonthCsv(context: Context, year: Int, month: Int, callback: (File?) -> Unit) {
        viewModelScope.launch {
            try {
                val records = allRecords.value.filter { it.jalaliYear == year && it.jalaliMonth == month }
                val totalLoggedMs = records.sumOf { (it.endTimeMillis ?: it.startTimeMillis) - it.startTimeMillis }
                val totalLoggedHours = totalLoggedMs.toDouble() / (1000 * 60 * 60)
                
                val key = "$year/$month"
                val obligatory = dao.getObligatoryHours(key)?.hours ?: 150.0
                
                var distributedOvertime = totalLoggedHours - obligatory
                if (distributedOvertime < 0) distributedOvertime = 0.0

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, "گزارش_کارکرد_${year}_${month}.csv")
                val fos = FileOutputStream(file)
                
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                
                val sb = StringBuilder()
                sb.append("روز هفته,روز,ماه,سال,تاريخ,ورود,خروج,توضیحات\n")

                val totalDays = JalaliCalendarHelper.getDaysInMonth(year, month)
                for (day in 1..totalDays) {
                    val dayRecord = records.find { it.jalaliDay == day }
                    val dateStr = "$year/${String.format("%02d", month)}/${String.format("%02d", day)}"
                    
                    if (dayRecord != null) {
                        val entryTime = "07:00"
                        val extraForThisDay = if (distributedOvertime > 0) {
                            val chunk = minOf(distributedOvertime, 2.0)
                            distributedOvertime -= chunk
                            chunk
                        } else 0.0
                        
                        val exitHour = 14 + extraForThisDay.toInt()
                        val exitTime = "${String.format("%02d", exitHour)}:00"
                        
                        sb.append("شنبه,$day,$month,$year,$dateStr,$entryTime,$exitTime,حضور تراز شده\n")
                    } else {
                        sb.append("جمعه,$day,$month,$year,$dateStr,,,,تعطیل رسمی\n")
                    }
                }

                fos.write(sb.toString().toByteArray(charset("UTF-8")))
                fos.close()
                callback(file)
            } catch (e: Exception) {
                callback(null)
            }
        }
    }
}

// Including your UI Code from step 2-1 seamlessly
@Composable
fun TimeTrackerApp(viewModel: TimeTrackingViewModel = viewModel()) {
    val context = LocalContext.current
    val activeRecord by viewModel.activeRecord.collectAsStateWithLifecycle()
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val currentMonthTarget by viewModel.currentMonthObligatoryHours.collectAsStateWithLifecycle()
    var activeTab by remember { mutableIntStateOf(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF121212)).statusBarsPadding().padding(vertical = 12.dp, horizontal = 20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(40.dp).background(Color(0xFF00B0FF), shape = RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Text("🕒", fontSize = 20.sp, color = Color.White)
                            }
                            Text(text = "ساعت‌زن هوشمند", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF1E1E1E)) {
                    NavigationBarItem(selected = activeTab == 0, onClick = { activeTab = 0 }, icon = { Text("🕒", fontSize = 20.sp) }, label = { Text("ثبت زمان", fontSize = 12.sp) })
                    NavigationBarItem(selected = activeTab == 1, onClick = { activeTab = 1 }, icon = { Text("📊", fontSize = 20.sp) }, label = { Text("گزارشات", fontSize = 12.sp) })
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
                if (activeTab == 0) {
                    TimeRegistrationTab(viewModel, activeRecord, allRecords, currentMonthTarget)
                } else {
                    ReportsTab(viewModel)
                }
            }
        }
    }
}

// Re-using UI structures for layout compilation
@Composable
fun TimeRegistrationTab(viewModel: TimeTrackingViewModel, activeRecord: TimeRecord?, allRecords: List<TimeRecord>, currentMonthTarget: Double) {
    val today = JalaliCalendarHelper.fromEpochMillis(System.currentTimeMillis())
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("وضعیت کارکرد این ماه", color = Color.Gray, fontSize = 14.sp)
                    Text("ساعات موظفی هدف: $currentMonthTarget", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { viewModel.toggleShift() }, modifier = Modifier.size(160.dp), shape = RoundedCornerShape(100.dp)) {
                    Text(if (activeRecord != null) "اتمام کار" else "شروع کار", fontSize = 20.sp)
                }
            }
        }
        item { ManualRecordSection(viewModel) }
        items(allRecords) { RecordItem(it, onDelete = { viewModel.deleteRecord(it) }) }
    }
}

@Composable
fun ManualRecordSection(viewModel: TimeTrackingViewModel) {
    val today = JalaliCalendarHelper.fromEpochMillis(System.currentTimeMillis())
    var day by remember { mutableStateOf(today.day.toString()) }
    var sh by remember { mutableStateOf("07") }
    var eh by remember { mutableStateOf("14") }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(16.dp)) {
        Text("ثبت اصلاحی دستی", color = Color(0xFF00B0FF), fontWeight = FontWeight.Bold)
        OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("روز") })
        Button(onClick = {
            viewModel.addManualRecord(today.year, today.month, day.toIntOrNull() ?: today.day, sh.toIntOrNull() ?: 7, 0, eh.toIntOrNull() ?: 14, 0)
            Toast.makeText(context, "ثبت شد", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره") }
    }
}

@Composable
fun RecordItem(record: TimeRecord, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("تاریخ: ${record.jalaliYear}/${record.jalaliMonth}/${record.jalaliDay}", color = Color.White)
                Text("حضور ثبت شده", color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red) }
        }
    }
}

@Composable
fun ReportsTab(viewModel: TimeTrackingViewModel) {
    val context = LocalContext.current
    val today = JalaliCalendarHelper.fromEpochMillis(System.currentTimeMillis())
    var hoursInput by remember { mutableStateOf("150") }

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("تنظیم ساعت موظفی ماه جدید", color = Color.White)
                OutlinedTextField(value = hoursInput, onValueChange = { hoursInput = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Button(onClick = { viewModel.saveObligatoryHours(today.year, today.month, hoursInput.toDoubleOrNull() ?: 150.0) }) { Text("ذخیره موظفی") }
            }
        }
        Button(onClick = {
            viewModel.exportMonthCsv(context, today.year, today.month) {
                Toast.makeText(context, "فایل اکسل در Downloads ذخیره شد", Toast.LENGTH_LONG).show()
            }
        }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("تولید و خروجی اکسل تراز شده (CSV)")
        }
    }
}
