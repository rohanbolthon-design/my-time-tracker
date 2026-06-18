package com.example

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.icu.util.Calendar
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

class TimeTrackingViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.dao()
    private val repository = TimeTrackingRepository(dao)

    val allRecords = repository.allRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _activeRecord = MutableStateFlow<TimeRecord?>(null)
    val activeRecord: StateFlow<TimeRecord?> = _activeRecord

    private val _currentMonthObligatoryHours = MutableStateFlow<Double>(0.0)
    val currentMonthObligatoryHours: StateFlow<Double> = _currentMonthObligatoryHours

    // Reporting States
    private val _reportWorkedHours = MutableStateFlow(0.0)
    val reportWorkedHours: StateFlow<Double> = _reportWorkedHours

    private val _reportObligatoryHours = MutableStateFlow(0.0)
    val reportObligatoryHours: StateFlow<Double> = _reportObligatoryHours

    init {
        checkActiveSession()
        loadCurrentMonthObligatoryHours()
    }

    fun checkActiveSession() {
        viewModelScope.launch {
            _activeRecord.value = repository.getActiveRecord()
        }
    }

    private fun loadCurrentMonthObligatoryHours() {
        viewModelScope.launch {
            val today = JalaliCalendarHelper.fromEpochMillis(System.currentTimeMillis())
            val target = repository.getObligatoryHours(today.year, today.month)
            _currentMonthObligatoryHours.value = target?.requiredHours ?: 0.0
        }
    }

    fun toggleShift() {
        viewModelScope.launch {
            val active = repository.getActiveRecord()
            val now = System.currentTimeMillis()
            val jDate = JalaliCalendarHelper.fromEpochMillis(now)

            if (active == null) {
                // Start Shift
                val newRecord = TimeRecord(
                    jalaliYear = jDate.year,
                    jalaliMonth = jDate.month,
                    jalaliDay = jDate.day,
                    startTimeMillis = now,
                    endTimeMillis = null
                )
                repository.insertRecord(newRecord)
            } else {
                // End Shift (End shift cannot be before start shift)
                val updated = active.copy(endTimeMillis = maxOf(active.startTimeMillis + 1000, now))
                repository.updateRecord(updated)
            }
            checkActiveSession()
        }
    }

    fun saveObligatoryHours(year: Int, month: Int, hours: Double) {
        viewModelScope.launch {
            repository.insertObligatoryHours(MonthlyObligatoryHours(year, month, hours))
            loadCurrentMonthObligatoryHours()
        }
    }

    fun addManualRecord(year: Int, month: Int, day: Int, startH: Int, startM: Int, endH: Int, endM: Int) {
        viewModelScope.launch {
            val startMillis = JalaliCalendarHelper.toEpochMillis(year, month, day, startH, startM)
            val endMillis = JalaliCalendarHelper.toEpochMillis(year, month, day, endH, endM)
            
            // Adjust end time to be after start time if needed
            val finalEndMillis = if (endMillis <= startMillis) startMillis + 60 * 1000 else endMillis

            val record = TimeRecord(
                jalaliYear = year,
                jalaliMonth = month,
                jalaliDay = day,
                startTimeMillis = startMillis,
                endTimeMillis = finalEndMillis,
                isManual = true
            )
            repository.insertRecord(record)
            checkActiveSession()
        }
    }

    fun updateRecord(record: TimeRecord) {
        viewModelScope.launch {
            repository.updateRecord(record)
            checkActiveSession()
        }
    }

    fun deleteRecord(record: TimeRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
            checkActiveSession()
        }
    }

    fun generateReport(sYear: Int, sMonth: Int, sDay: Int, eYear: Int, eMonth: Int, eDay: Int) {
        viewModelScope.launch {
            val startMillis = JalaliCalendarHelper.toEpochMillis(sYear, sMonth, sDay, 0, 0)
            val endMillis = JalaliCalendarHelper.toEpochMillis(eYear, eMonth, eDay, 23, 59)
            val list = repository.getRecordsInRange(startMillis, endMillis)

            val totalWorked = list.sumOf { record ->
                val duration = (record.endTimeMillis ?: System.currentTimeMillis()) - record.startTimeMillis
                duration.toDouble() / (1000 * 60 * 60)
            }

            // Check if there is a manual monthly target input for all months covered in the range
            // For simplicity, let's load any saved monthly required hours, or default to the standard calendar sum
            var obligatorySum = 0.0
            
            // Calculate standard calendar required hours dynamically for the chosen range
            var currentDayMillis = startMillis
            // Prevent infinite loop if endMillis < startMillis
            val safeEndMillis = maxOf(startMillis, endMillis)
            while (currentDayMillis <= safeEndMillis) {
                val jDate = JalaliCalendarHelper.fromEpochMillis(currentDayMillis)
                val dayOfWeek = JalaliCalendarHelper.getDayOfWeekInt(jDate.year, jDate.month, jDate.day)
                if (dayOfWeek != Calendar.FRIDAY) {
                    obligatorySum += if (dayOfWeek == Calendar.THURSDAY) 5.0 else 7.0
                }
                currentDayMillis += 24 * 60 * 60 * 1000 // increment one calendar day
            }

            // If start/end month are the same, we check if there is a manual obligatory setting
            if (sYear == eYear && sMonth == eMonth) {
                val savedTarget = repository.getObligatoryHours(sYear, sMonth)
                if (savedTarget != null) {
                    obligatorySum = savedTarget.requiredHours
                }
            }

            _reportWorkedHours.value = totalWorked
            _reportObligatoryHours.value = obligatorySum
        }
    }

    fun exportMonthCsv(context: Context, year: Int, month: Int, onComplete: (Uri?) -> Unit) {
        viewModelScope.launch {
            val records = repository.getRecordsForMonth(year, month)
            val totalWorked = records.sumOf { record ->
                val duration = (record.endTimeMillis ?: record.startTimeMillis) - record.startTimeMillis
                duration.toDouble() / (1000 * 60 * 60)
            }

            val daysInMonth = JalaliCalendarHelper.getDaysInMonth(year, month)
            var countSatWed = 0
            var countThu = 0
            for (day in 1..daysInMonth) {
                val dayOfWeek = JalaliCalendarHelper.getDayOfWeekInt(year, month, day)
                if (dayOfWeek == Calendar.FRIDAY) continue
                if (dayOfWeek == Calendar.THURSDAY) countThu++ else countSatWed++
            }

            val numWorkingDays = countSatWed + countThu
            
            // Calculate standard base required hours
            val totalStandardHours = (countSatWed * 7.0) + (countThu * 5.0)
            
            // Overtime or Undertime difference
            val diff = totalWorked - totalStandardHours
            val adjustmentPerHour = if (numWorkingDays > 0) diff / numWorkingDays else 0.0

            val csvBuilder = StringBuilder()
            // Excel UTF-8 with BOM standard
            csvBuilder.append("روز,تاریخ,ورود,خروج,کارکرد (ساعت)\n")

            for (day in 1..daysInMonth) {
                val dayOfWeekStr = JalaliCalendarHelper.getDayOfWeekPersian(year, month, day)
                val dayOfWeekInt = JalaliCalendarHelper.getDayOfWeekInt(year, month, day)
                val dateStr = String.format(Locale.US, "%04d/%02d/%02d", year, month, day)

                if (dayOfWeekInt == Calendar.FRIDAY) {
                    csvBuilder.append("$dayOfWeekStr,$dateStr,تعطیل,تعطیل,0.00\n")
                } else {
                    val standardDuration = if (dayOfWeekInt == Calendar.THURSDAY) 5.0 else 7.0
                    val adjustedDuration = maxOf(0.0, standardDuration + adjustmentPerHour)
                    val entryTime = "07:00"
                    val exitTime = formatExitTime(adjustedDuration)
                    val workedFormatted = String.format(Locale.US, "%.2f", adjustedDuration)
                    csvBuilder.append("$dayOfWeekStr,$dateStr,$entryTime,$exitTime,$workedFormatted\n")
                }
            }

            val uri = saveCsvToDownloads(context, year, month, csvBuilder.toString())
            onComplete(uri)
        }
    }

    private fun formatExitTime(durationHours: Double): String {
        val totalTimeInHours = 7.0 + durationHours
        var hours = totalTimeInHours.toInt()
        var minutes = Math.round((totalTimeInHours - hours) * 60).toInt()
        if (minutes >= 60) {
            hours += 1
            minutes -= 60
        }
        return String.format(Locale.US, "%02d:%02d", hours, minutes)
    }

    private fun saveCsvToDownloads(context: Context, year: Int, month: Int, csvContent: String): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "گزارش_کارکرد_${year}_${month}.csv")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                // Write standard UTF-8 BOM bytes
                outputStream.write(0xEF)
                outputStream.write(0xBB)
                outputStream.write(0xBF)
                val writer = OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
                writer.write(csvContent)
                writer.flush()
            }
        }
        return uri
    }
}
