package com.example.edutute.data.repository

import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.domain.model.IndiaAddressValidation
import com.example.edutute.domain.repository.LocationValidationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class IndiaLocationValidationRepository : LocationValidationRepository {

    override suspend fun lookupIndianAddress(postalCode: String): IndiaAddressValidation = withContext(Dispatchers.IO) {
        val normalizedPostalCode = postalCode.trim()
        if (!ValidationUtils.isValidIndianPostalCode(normalizedPostalCode)) {
            throw IllegalArgumentException("Enter a valid 6-digit Indian PIN code.")
        }

        val offices = loadPostOffices(normalizedPostalCode)
        val primaryOffice = (0 until offices.length())
            .asSequence()
            .mapNotNull { index -> offices.optJSONObject(index) }
            .firstOrNull()
            ?: throw IllegalArgumentException("PIN code could not be verified. Check the PIN and try again.")

        val resolvedCity = primaryOffice.optString("District").ifBlank {
            primaryOffice.optString("Block").ifBlank {
                primaryOffice.optString("Name")
            }
        }
        val resolvedState = primaryOffice.optString("State")

        if (resolvedCity.isBlank() || resolvedState.isBlank()) {
            throw IllegalArgumentException("PIN code could not be verified. Check the PIN and try again.")
        }

        IndiaAddressValidation(
            city = ValidationUtils.titleCaseWords(resolvedCity),
            state = ValidationUtils.titleCaseWords(resolvedState),
            postalCode = normalizedPostalCode,
        )
    }

    override suspend fun validateIndianAddress(
        city: String,
        state: String,
        postalCode: String,
    ): IndiaAddressValidation = withContext(Dispatchers.IO) {
        ValidationUtils.requireNotBlank(city, "City")
        ValidationUtils.requireNotBlank(state, "State")
        ValidationUtils.requireNotBlank(postalCode, "Postal code")

        if (!ValidationUtils.isKnownIndianState(state)) {
            throw IllegalArgumentException("Select a valid Indian state.")
        }
        if (!ValidationUtils.isValidIndianPostalCode(postalCode)) {
            throw IllegalArgumentException("Enter a valid 6-digit Indian PIN code.")
        }

        try {
            val offices = loadPostOffices(postalCode.trim())

            val normalizedState = ValidationUtils.normalize(state)
            val normalizedCity = ValidationUtils.normalize(city)

            var matchedState: String? = null
            var matchedCity: String? = null

            for (index in 0 until offices.length()) {
                val office = offices.optJSONObject(index) ?: continue
                val officeState = office.optString("State")
                val officeDistrict = office.optString("District")
                val officeBlock = office.optString("Block")
                val officeName = office.optString("Name")
                val officeDivision = office.optString("Division")

                if (ValidationUtils.normalize(officeState) == normalizedState) {
                    matchedState = officeState
                }

                val cityCandidates = listOf(officeDistrict, officeBlock, officeName, officeDivision)
                    .filter { it.isNotBlank() }
                    .map(ValidationUtils::normalize)

                if (cityCandidates.any { candidate ->
                        candidate == normalizedCity ||
                            candidate.contains(normalizedCity) ||
                            normalizedCity.contains(candidate)
                    }
                ) {
                    matchedCity = officeDistrict.ifBlank { officeName }
                }
            }

            if (matchedState == null) {
                throw IllegalArgumentException("PIN code does not belong to the selected state.")
            }

            if (matchedCity == null) {
                throw IllegalArgumentException("City does not align with the selected PIN code and state.")
            }

            IndiaAddressValidation(
                city = ValidationUtils.titleCaseWords(matchedCity),
                state = ValidationUtils.titleCaseWords(matchedState),
                postalCode = postalCode.trim(),
            )
        } catch (exception: IllegalArgumentException) {
            throw exception
        } catch (exception: Exception) {
            throw IllegalStateException("Address verification is unavailable right now. Please check your connection and try again.")
        }
    }

    private fun loadPostOffices(postalCode: String): JSONArray {
        val connection = (URL("https://api.postalpincode.in/pincode/$postalCode").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            doInput = true
        }

        return try {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val payload = JSONArray(response).optJSONObject(0)
                ?: throw IllegalStateException("Address verification could not be completed.")
            val status = payload.optString("Status")
            if (!status.equals("Success", ignoreCase = true)) {
                throw IllegalArgumentException("PIN code could not be verified. Check the PIN and try again.")
            }

            payload.optJSONArray("PostOffice")
                ?: throw IllegalArgumentException("PIN code could not be verified. Check the PIN and try again.")
        } finally {
            connection.disconnect()
        }
    }
}
