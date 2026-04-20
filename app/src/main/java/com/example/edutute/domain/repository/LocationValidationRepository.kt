package com.example.edutute.domain.repository

import com.example.edutute.domain.model.IndiaAddressValidation

interface LocationValidationRepository {
    suspend fun lookupIndianAddress(postalCode: String): IndiaAddressValidation

    suspend fun validateIndianAddress(
        city: String,
        state: String,
        postalCode: String,
    ): IndiaAddressValidation
}
