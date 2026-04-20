package com.example.edutute.core.util

import android.util.Patterns
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

object ValidationUtils {
    private val indianPhoneRegex = Regex("^[6-9][0-9]{9}$")
    private val indianPostalCodeRegex = Regex("^[1-9][0-9]{5}$")
    private val dayMonthYearRegex = Regex("^(0[1-9]|[12][0-9]|3[01])/(0[1-9]|1[0-2])/\\d{4}$")
    private val blockedPhoneValues = setOf(
        "0000000000",
        "1111111111",
        "2222222222",
        "3333333333",
        "4444444444",
        "5555555555",
        "6666666666",
        "7777777777",
        "8888888888",
        "9999999999",
        "0123456789",
        "1234567890",
        "2345678901",
        "3456789012",
        "4567890123",
        "5678901234",
        "6789012345",
        "7890123456",
        "8901234567",
        "9012345678",
        "9876543210",
        "8765432109",
        "7654321098",
        "6543210987",
        "5432109876",
        "4321098765",
        "3210987654",
        "2109876543",
    )

    private val indianStates = listOf(
        "Andaman and Nicobar Islands",
        "Andhra Pradesh",
        "Arunachal Pradesh",
        "Assam",
        "Bihar",
        "Chandigarh",
        "Chhattisgarh",
        "Dadra and Nagar Haveli and Daman and Diu",
        "Delhi",
        "Goa",
        "Gujarat",
        "Haryana",
        "Himachal Pradesh",
        "Jammu and Kashmir",
        "Jharkhand",
        "Karnataka",
        "Kerala",
        "Ladakh",
        "Lakshadweep",
        "Madhya Pradesh",
        "Maharashtra",
        "Manipur",
        "Meghalaya",
        "Mizoram",
        "Nagaland",
        "Odisha",
        "Puducherry",
        "Punjab",
        "Rajasthan",
        "Sikkim",
        "Tamil Nadu",
        "Telangana",
        "Tripura",
        "Uttar Pradesh",
        "Uttarakhand",
        "West Bengal",
    )

    fun normalize(value: String): String = value.trim().lowercase().replace("\\s+".toRegex(), " ")

    fun normalizeCode(value: String): String = value
        .trim()
        .lowercase()
        .replace("[^a-z0-9]+".toRegex(), "_")
        .trim('_')

    fun isValidEmail(value: String): Boolean = value.isBlank() || Patterns.EMAIL_ADDRESS.matcher(value.trim()).matches()

    fun isValidIndianPhone(value: String): Boolean {
        val digits = normalizeIndianPhoneDigits(value)
        return indianPhoneRegex.matches(digits) && !isObviouslyFakePhone(digits)
    }

    fun normalizeIndianPhoneDigits(value: String): String = value.filter { it.isDigit() }.let { digits ->
        when {
            digits.length == 10 -> digits
            digits.length == 12 && digits.startsWith("91") -> digits.removePrefix("91")
            digits.length == 13 && digits.startsWith("091") -> digits.removePrefix("091")
            else -> digits
        }
    }

    fun formatIndianPhoneE164(value: String): String = "+91${normalizeIndianPhoneDigits(value)}"

    fun isObviouslyFakePhone(value: String): Boolean {
        val digits = normalizeIndianPhoneDigits(value)
        return digits in blockedPhoneValues || digits.zipWithNext().all { (left, right) ->
            right.code - left.code == 1
        } || digits.zipWithNext().all { (left, right) ->
            left.code - right.code == 1
        }
    }

    fun isValidIndianPostalCode(value: String): Boolean = indianPostalCodeRegex.matches(value.trim())

    fun isValidDayMonthYear(value: String): Boolean {
        val trimmedValue = value.trim()
        if (trimmedValue.isBlank()) return true
        if (!dayMonthYearRegex.matches(trimmedValue)) return false

        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
            isLenient = false
        }
        val parsePosition = ParsePosition(0)
        formatter.parse(trimmedValue, parsePosition) ?: return false
        return parsePosition.index == trimmedValue.length
    }

    fun isKnownIndianState(value: String): Boolean = indianStates.any { normalize(it) == normalize(value) }

    fun indianStates(): List<String> = indianStates

    fun titleCaseWords(value: String): String = value
        .trim()
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.lowercase().replaceFirstChar { character ->
                if (character.isLowerCase()) {
                    character.titlecase()
                } else {
                    character.toString()
                }
            }
        }

    fun requireNotBlank(value: String, fieldLabel: String) {
        if (value.trim().isBlank()) {
            throw IllegalArgumentException("$fieldLabel is required.")
        }
    }
}
