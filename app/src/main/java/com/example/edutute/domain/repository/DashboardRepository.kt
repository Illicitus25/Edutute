package com.example.edutute.domain.repository

import com.example.edutute.domain.model.DashboardSummary

interface DashboardRepository {
    suspend fun getDashboardSummary(): DashboardSummary
}
