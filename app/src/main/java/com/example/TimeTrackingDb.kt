package com.example

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "time_records")
data class TimeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jalaliYear: Int,
    val jalaliMonth: Int,
    val jalaliDay: Int,
    val startTimeMillis: Long,
    val endTimeMillis: Long?, // Null if the current shift is active (not finished)
    val isManual: Boolean = false
)

@Entity(tableName = "obligatory_hours", primaryKeys = ["jalaliYear", "jalaliMonth"])
data class MonthlyObligatoryHours(
    val jalaliYear: Int,
    val jalaliMonth: Int,
    val requiredHours: Double
)

@Dao
interface TimeTrackingDao {
    @Query("SELECT * FROM time_records ORDER BY startTimeMillis DESC")
    fun getAllRecordsFlow(): Flow<List<TimeRecord>>

    @Query("SELECT * FROM time_records WHERE jalaliYear = :year AND jalaliMonth = :month ORDER BY startTimeMillis ASC")
    suspend fun getRecordsForMonth(year: Int, month: Int): List<TimeRecord>

    @Query("SELECT * FROM time_records WHERE startTimeMillis >= :startMillis AND startTimeMillis <= :endMillis ORDER BY startTimeMillis ASC")
    suspend fun getRecordsInRange(startMillis: Long, endMillis: Long): List<TimeRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TimeRecord): Long

    @Update
    suspend fun updateRecord(record: TimeRecord)

    @Delete
    suspend fun deleteRecord(record: TimeRecord)

    @Query("SELECT * FROM time_records WHERE endTimeMillis IS NULL LIMIT 1")
    suspend fun getActiveRecord(): TimeRecord?

    @Query("SELECT * FROM obligatory_hours WHERE jalaliYear = :year AND jalaliMonth = :month LIMIT 1")
    suspend fun getObligatoryHours(year: Int, month: Int): MonthlyObligatoryHours?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObligatoryHours(hours: MonthlyObligatoryHours)
}

@Database(entities = [TimeRecord::class, MonthlyObligatoryHours::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TimeTrackingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "time_tracking_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class TimeTrackingRepository(private val dao: TimeTrackingDao) {
    val allRecords: Flow<List<TimeRecord>> = dao.getAllRecordsFlow()

    suspend fun getRecordsForMonth(year: Int, month: Int): List<TimeRecord> =
        dao.getRecordsForMonth(year, month)

    suspend fun getRecordsInRange(startMillis: Long, endMillis: Long): List<TimeRecord> =
        dao.getRecordsInRange(startMillis, endMillis)

    suspend fun insertRecord(record: TimeRecord): Long = dao.insertRecord(record)

    suspend fun updateRecord(record: TimeRecord) = dao.updateRecord(record)

    suspend fun deleteRecord(record: TimeRecord) = dao.deleteRecord(record)

    suspend fun getActiveRecord(): TimeRecord? = dao.getActiveRecord()

    suspend fun getObligatoryHours(year: Int, month: Int): MonthlyObligatoryHours? =
        dao.getObligatoryHours(year, month)

    suspend fun insertObligatoryHours(hours: MonthlyObligatoryHours) =
        dao.insertObligatoryHours(hours)
}
