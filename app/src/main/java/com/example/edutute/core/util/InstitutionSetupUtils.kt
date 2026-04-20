package com.example.edutute.core.util

import java.time.ZoneId
import java.time.ZonedDateTime

object InstitutionSetupUtils {
    private val indiaZone: ZoneId = ZoneId.of("Asia/Kolkata")

    fun currentAcademicSessionLabel(now: ZonedDateTime = ZonedDateTime.now(indiaZone)): String {
        val startYear = now.year
        return "$startYear-${startYear + 1}"
    }

    fun generateInstitutionId(sequence: Long): String {
        val safeSequence = sequence.coerceAtLeast(1L)
        return "INS${safeSequence.toString().padStart(4, '0')}"
    }
}
