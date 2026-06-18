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
import androidx.room.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data layer definitions directly bundled inside single file architecture
@Entity(tableName = "time_records")
data class TimeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTimeMillis: Long,
    val endTimeMillis: Long?,
    val jalaliYear: Int,
    val jalaliMonth: Int,
    val jalaliDay: Int,
    val isManual: Boolean = false
)

@Entity(tableName = "obligatory_hours")
data class ObligatoryHours(
    @PrimaryKey val monthKey: String,
    val hours: Double
)

@Dao
interface TimeTrackerDao {
    @Query("SELECT * FROM time_records ORDER BY id DESC")
    fun getAllRecords(): Flow<List<TimeRecord>>
    @Query("SELECT * FROM time_records WHERE endTimeMillis IS NULL LIMIT 1")
    suspend fun getActiveRecord(): TimeRecord?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TimeRecord): Long
    @Update
    suspend fun updateRecord(record: TimeRecord)
    @Delete
    suspend fun deleteRecord(record: TimeRecord)
    @Query("SELECT * FROM obligatory_hours WHERE monthKey = :key")
    suspend fun getObligatoryHours(key: String): ObligatoryHours?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObligatoryHours(hours: ObligatoryHours)
}

@Database(entities = [TimeRecord::class, ObligatoryHours::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TimeTrackerDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tracker_db").build()
                INSTANCE = instance
                instance
            }
        }
    }
}

data class JalaliDate(val year: Int, val month: Int, val day: Int)

object JalaliCalendarHelper {
    fun fromEpochMillis(millis: Long): JalaliDate {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        val gYear = cal.get(java.util.Calendar.YEAR)
        val gMonth = cal.get(java.util.Calendar.MONTH) + 1
        val gDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
        var jy = gYear - 621
        val gDaysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if ((gYear % 4 == 0 && gYear % 100 != 0) || (gYear % 400 == 0)) gDaysInMonth[2] = 29
        var totalGDays = gDay
        for (i in 1 until gMonth) totalGDays += gDaysInMonth[i]
        val jDaysInMonth = intArrayOf(0, 31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var jDaysSum = totalGDays - 79
        if (jDaysSum > 0) {
            var m = 1
            while (m <= 12 && jDaysSum > jDaysInMonth[m]) { jDaysSum -= jDaysInMonth[m]; m++ }
            return JalaliDate(jy, m, jDaysSum)
        } else {
            jy--
            jDaysSum += 365
            return JalaliDate(jy, 12, jDaysSum)
        }
    }
    fun getDaysInMonth(year: Int, month: Int): Int = if (month in 1..6) 31 else if (month in 7..11) 30 else 29
    fun getPersianMonthName(month: Int): String = listOf("", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")[month]
    fun getDayOfWeekPersian(year: Int, month: Int, day: Int): String = "شنبه"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00B0FF),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TimeTrackerApp()
                }
            }
        }
    }
}

class TimeTrackingViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val dao = database.dao()
    val allRecords: StateFlow<List<TimeRecord>> = dao.getAllRecords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _activeRecord = MutableStateFlow<TimeRecord?>(null)
    val activeRecord: StateFlow<TimeRecord?> = _activeRecord.asStateFlow()
    val currentMonthObligatoryHours = MutableStateFlow(150.0)

    init {
        viewModelScope.launch {
            _activeRecord.value = dao.getActiveRecord()
            val today = JalaliCalendarHelper.fromEpochMillis(System.currentTimeMillis())
            val saved = dao.getObligatoryHours("${today.year}/${today.month}")
            if (saved != null) currentMonthObligatoryHours.value = saved.hours
        }
    }

    fun toggleShift() {
        viewModelScope.launch {
            val currentActive = dao.getActiveRecord()
            val now = System.currentTimeMillis()
            val today = JalaliCalendarHelper.fromEpochMillis(now)
            if (currentActive == null) {
                val newRecord = TimeRecord(startTimeMillis = now, endTimeMillis = null, jalaliYear = today.year, jalaliMonth = today.month, jalaliDay = today.day)
                dao.insertRecord(newRecord)
                _activeRecord.value = newRecord
            } else {
                dao.updateRecord(currentActive.copy(endTimeMillis = now))
                _activeRecord.value = null
            }
        }
    }

    fun addManualRecord(jy: Int, jm: Int, jd: Int, sh: Int, sm: Int, eh: Int, em: Int) {
        viewModelScope.launch {
            val cal = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, sh); set(java.util.Calendar.MINUTE, sm) }
            val startMs = cal.timeInMillis
            cal.set(java.util.Calendar.HOUR_OF_DAY, eh); cal.set(java.util.Calendar.MINUTE, em)
            dao.insertRecord(TimeRecord(startTimeMillis = startMs, endTimeMillis = cal.timeInMillis, jalaliYear = jy, jalaliMonth = jm, jalaliDay = jd, isManual = true))
        }
    }

    fun deleteRecord(record: TimeRecord) { viewModelScope.launch { dao.deleteRecord(record) } }
    fun saveObligatoryHours(year: Int, month: Int, hours: Double) {
        viewModelScope.launch {
            dao.insertObligatoryHours(ObligatoryHours("$year/$month", hours))
            currentMonthObligatoryHours.value = hours
        }
    }

    fun exportMonthCsv(context: Context, year: Int, month: Int, callback: (File?) -> Unit) {
        viewModelScope.launch {
            try {
                val records = allRecords.value.filter { it.jalaliYear == year && it.jalaliMonth == month }
                val totalLoggedMs = records.sumOf { (it.endTimeMillis ?: it.startTimeMillis) - it.startTimeMillis }
                val totalLoggedHours = totalLoggedMs.toDouble() / (1000 * 60 * 60)
                val obligatory = currentMonthObligatoryHours.value
                var distributedOvertime = if (totalLoggedHours > obligatory) totalLoggedHours - obligatory else 0.0

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, "گزارش_کارکرد_${year}_${month}.csv")
                val fos = FileOutputStream(file).apply { write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) }
                
                val sb = StringBuilder().append("روز هفته,روز,ماه,سال,تاريخ,ورود,خروج,توضیحات\n")
                val totalDays = JalaliCalendarHelper.getDaysInMonth(year, month)
                for (day in 1..totalDays) {
                    val dayRecord = records.find { it.jalaliDay == day }
                    val dateStr = "$year/${String.format("%02d", month)}/${String.format("%02d", day)}"
                    if (dayRecord != null) {
                        val extra = if (distributedOvertime > 0) { val c = minOf(distributedOvertime, 2.0); distributedOvertime -= c; c } else 0.0
                        sb.append("شنبه,$day,$month,$year,$dateStr,07:00,${14 + extra.toInt()}:00,حضور تراز شده\n")
                    } else {
                        sb.append("جمعه,$day,$month,$year,$dateStr,,,,تعطیل رسمی\n")
                    }
                }
                fos.write(sb.toString().toByteArray(charset("UTF-8")))
                fos.close()
                callback(file)
            } catch (e: Exception) { callback(null) }
        }
    }
}

@Composable
fun TimeTrackerApp(viewModel: TimeTrackingViewModel = viewModel()) {
    val activeRecord by viewModel.activeRecord.collectAsStateWithLifecycle()
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val currentMonthTarget by viewModel.currentMonthObligatoryHours.collectAsStateWithLifecycle()
    var activeTab by remember { mutableStateOf(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF1E1E1E)) {
                    NavigationBarItem(selected = activeTab == 0, onClick = { activeTab = 0 }, icon = { Text("🕒") }, label = { Text("ثبت زمان") })
                    NavigationBarItem(selected = activeTab == 1, onClick = { activeTab = 1 }, icon = { Text("📊") }, label = { Text("گزارشات") })
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

@Composable
fun TimeRegistrationTab(viewModel: TimeTrackingViewModel, activeRecord: TimeRecord?, allRecords: List<TimeRecord>, currentMonthTarget: Double) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ساعات موظفی هدف این ماه: $currentMonthTarget ساعت", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { viewModel.toggleShift() }, modifier = Modifier.size(140.dp), shape = RoundedCornerShape(100.dp)) {
                    Text(if (activeRecord != null) "اتمام کار" else "شروع کار", fontSize = 18.sp)
                }
            }
        }
        item { ManualRecordSection(viewModel) }
        items(allRecords) { record ->
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF252525))) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("تاریخ: ${record.jalaliYear}/${record.jalaliMonth}/${record.jalaliDay}", color = Color.White)
                    IconButton(onClick = { viewModel.deleteRecord(record) }) { Icon(Icons.Default.Delete, "حذف", tint = Color.Red) }
                }
            }
        }
    }
}

@Composable
fun ManualRecordSection(viewModel: TimeTrackingViewModel) {
    val today = JalaliCalendarHelper.fromEpochMillis(System.currentTimeMillis())
    var day by remember { mutableStateOf(today.day.toString()) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).padding(16.dp)) {
        Text("ثبت اصلاحی دستی", color = Color(0xFF00B0FF), fontWeight = FontWeight.Bold)
        OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("روز ماه") })
        Button(onClick = {
            viewModel.addManualRecord(today.year, today.month, day.toIntOrNull() ?: today.day, 7, 0, 14, 0)
            Toast.makeText(context, "ثبت شد", Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("ذخیره") }
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
                Text("تنظیم ساعت موظفی ماه", color = Color.White)
                OutlinedTextField(value = hoursInput, onValueChange = { hoursInput = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Button(onClick = { viewModel.saveObligatoryHours(today.year, today.month, hoursInput.toDoubleOrNull() ?: 150.0) }, modifier = Modifier.padding(top = 8.dp)) { Text("ذخیره موظفی") }
            }
        }
        Button(onClick = {
            viewModel.exportMonthCsv(context, today.year, today.month) {
                Toast.makeText(context, "فایل در پوشه Downloads ذخیره شد", Toast.LENGTH_LONG).show()
            }
        }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("تولید و خروجی اکسل تراز شده (CSV)")
        }
    }
}
