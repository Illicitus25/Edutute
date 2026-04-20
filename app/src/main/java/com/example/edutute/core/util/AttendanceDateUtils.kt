package com.example.edutute.core.util

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object AttendanceDateUtils {
    private const val STORAGE_PATTERN = "yyyy-MM-dd"
    private const val DISPLAY_PATTERN = "dd MMM yyyy"
    private const val DISPLAY_WITH_DAY_PATTERN = "EEE, dd MMM yyyy"
    private val indiaTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    fun todayStorageDate(): String = storageFormatter().format(Date())

    fun isValidStorageDate(value: String): Boolean {
        val trimmedValue = value.trim()
        if (trimmedValue.isBlank()) return false

        val formatter = storageFormatter()
        val parsePosition = ParsePosition(0)
        formatter.parse(trimmedValue, parsePosition) ?: return false
        return parsePosition.index == trimmedValue.length
    }

    fun toDisplayDate(value: String): String = parseStorageDate(value)?.let { displayFormatter().format(it) } ?: value

    fun toDisplayDateWithDay(value: String): String =
        parseStorageDate(value)?.let { displayWithDayFormatter().format(it) } ?: value

    fun toCalendar(value: String): Calendar = Calendar.getInstance(indiaTimeZone).apply {
        time = parseStorageDate(value) ?: Date()
    }

    fun fromCalendar(
        year: Int,
        monthZeroBased: Int,
        dayOfMonth: Int,
    ): String = Calendar.getInstance(indiaTimeZone).apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, monthZeroBased)
        set(Calendar.DAY_OF_MONTH, dayOfMonth)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.let { storageFormatter().format(it.time) }

    fun toDateKey(value: String): String = value.replace("-", "")

    private fun parseStorageDate(value: String): Date? {
        val trimmedValue = value.trim()
        if (trimmedValue.isBlank()) return null
        return storageFormatter().parse(trimmedValue)
    }

    private fun storageFormatter(): SimpleDateFormat = SimpleDateFormat(STORAGE_PATTERN, Locale.US).apply {
        isLenient = false
        timeZone = indiaTimeZone
    }

    private fun displayFormatter(): SimpleDateFormat = SimpleDateFormat(DISPLAY_PATTERN, Locale.US).apply {
        isLenient = false
        timeZone = indiaTimeZone
    }

    private fun displayWithDayFormatter(): SimpleDateFormat =
        SimpleDateFormat(DISPLAY_WITH_DAY_PATTERN, Locale.US).apply {
            isLenient = false
            timeZone = indiaTimeZone
        }
}
