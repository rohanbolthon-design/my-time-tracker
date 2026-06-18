package com.example

import android.icu.util.Calendar
import android.icu.util.ULocale
import java.util.Date

object JalaliCalendarHelper {

    data class JalaliDate(val year: Int, val month: Int, val day: Int)

    private val persianLocale = ULocale("fa_IR@calendar=persian")

    // Converts a Unix Timestamp (Gregorian) to a Jalali Date
    fun fromEpochMillis(millis: Long): JalaliDate {
        val cal = Calendar.getInstance(persianLocale)
        cal.timeInMillis = millis
        return JalaliDate(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1, // Calendar.MONTH is 0-based
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    // Converts a Jalali Date and Time to Epoch Milliseconds (Gregorian)
    fun toEpochMillis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long {
        val cal = Calendar.getInstance(persianLocale)
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1) // 0-based month
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // Returns Persian weekday name
    fun getDayOfWeekPersian(year: Int, month: Int, day: Int): String {
        val cal = Calendar.getInstance(persianLocale)
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یکشنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنجشنبه"
            Calendar.FRIDAY -> "جمعه"
            else -> ""
        }
    }

    // Returns standard Calendar day identifier (e.g. Calendar.SATURDAY, etc.)
    fun getDayOfWeekInt(year: Int, month: Int, day: Int): Int {
        val cal = Calendar.getInstance(persianLocale)
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        return cal.get(Calendar.DAY_OF_WEEK)
    }

    fun getPersianMonthName(month: Int): String {
        return when (month) {
            1 -> "فروردین"
            2 -> "اردیبهشت"
            3 -> "خرداد"
            4 -> "تیر"
            5 -> "مرداد"
            6 -> "شهریور"
            7 -> "مهر"
            8 -> "آبان"
            9 -> "آذر"
            10 -> "دی"
            11 -> "بهمن"
            12 -> "اسفند"
            else -> ""
        }
    }

    fun getDaysInMonth(year: Int, month: Int): Int {
        if (month in 1..6) return 31
        if (month in 7..11) return 30
        return if (isLeapYear(year)) 30 else 29
    }

    private fun isLeapYear(year: Int): Boolean {
        val cal = Calendar.getInstance(persianLocale)
        cal.clear()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, 11) // Esfand is 11 (0-based)
        cal.set(Calendar.DAY_OF_MONTH, 30)
        return cal.get(Calendar.DAY_OF_MONTH) == 30
    }
}
