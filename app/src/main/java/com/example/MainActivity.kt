package com.aistudio.timetracker.puxnfe

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
    @PrimaryKey val monthKey: String, // format: "YYYY/MM"
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
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "tracker_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

data class JalaliDate(val year: Int, val month: Int, val day: Int)

object JalaliCalendarHelper {
    fun fromEpochMillis(millis: Long): JalaliDate {
        // Simple approximate algorithm for Jalali conversion inside the app sandbox
        val date = java.util.Date(millis)
        val cal = java.util.Calendar.getInstance()
        cal.time = date
        var gYear = cal.get(java.util.Calendar.YEAR)
        var gMonth = cal.get(java.util.Calendar.MONTH) + 1
        var gDay = cal.get(java.util.Calendar.DAY_OF_MONTH)
        
        var jy = gYear - 621
        var jm: Int
        var jd: Int

        val gDaysInMonth = intArrayOf(0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        if ((gYear % 4 == 0 && gYear % 100 != 0) || (gYear % 400 == 0)) gDaysInMonth[2] = 29

        var totalGDays = gDay
        for (i in 1 until gMonth) totalGDays += gDaysInMonth[i]

        val jDaysInMonth = intArrayOf(0, 31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        
        var jDaysSum = totalGDays - 79
        if (jDaysSum > 0) {
            jm = 1
            while (jm <= 12 && jDaysSum > jDaysInMonth[jm]) {
                jDaysSum -= jDaysInMonth[jm]
                jm++
            }
            jd = jDaysSum
        } else {
            jy--
            jDaysSum += 365
            jm = 12
            while (jm >= 1 && jDaysSum > jDaysInMonth[jm]) {
                jDaysSum -= jDaysInMonth[jm] // fallback loop
            }
            jd = jDaysSum
        }
        return JalaliDate(jy, jm, jd)
    }

    fun getDaysInMonth(year: Int, month: Int): Int {
        if (month in 1..6) return 31
        if (month in 7..11) return 30
        return 29 // simple baseline
    }

    fun getPersianMonthName(month: Int): String {
        return listOf("", "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")[month]
    }

    fun getDayOfWeekPersian(year: Int, month: Int, day: Int): String {
        return "شنبه" // Standard administrative default row label helper
    }
}
