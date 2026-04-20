package com.example.edutute.presentation.institution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.InstitutionSetupUtils
import com.example.edutute.core.util.userMessage
import com.example.edutute.domain.access.RoleAccessManager
import com.example.edutute.domain.model.AcademicSession
import com.example.edutute.domain.model.IndiaAddressValidation
import com.example.edutute.domain.model.Institution
import com.example.edutute.domain.model.InstitutionDraft
import com.example.edutute.domain.repository.InstitutionRepository
import com.example.edutute.domain.repository.LocationValidationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InstitutionProfileData(
    val institution: Institution?,
    val academicSession: AcademicSession?,
    val availableSessionNames: List<String>,
    val isReadOnly: Boolean = false,
)

class InstitutionProfileViewModel(
    private val institutionRepository: InstitutionRepository,
    private val locationValidationRepository: LocationValidationRepository,
    private val roleAccessManager: RoleAccessManager,
) : ViewModel() {

    private val mutableLoadState = MutableStateFlow<UiState<InstitutionProfileData>>(UiState.Loading)
    val loadState: StateFlow<UiState<InstitutionProfileData>> = mutableLoadState.asStateFlow()

    private val mutableSaveState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val saveState: StateFlow<UiState<Unit>> = mutableSaveState.asStateFlow()

    private val mutableAddressLookupState = MutableStateFlow<UiState<IndiaAddressValidation>>(UiState.Idle)
    val addressLookupState: StateFlow<UiState<IndiaAddressValidation>> = mutableAddressLookupState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            mutableLoadState.value = UiState.Loading
            mutableLoadState.value = try {
                UiState.Success(
                    InstitutionProfileData(
                        institution = institutionRepository.getInstitution(),
                        academicSession = institutionRepository.getCurrentAcademicSession(),
                        availableSessionNames = buildList {
                            add(InstitutionSetupUtils.currentAcademicSessionLabel())
                            addAll(institutionRepository.listAcademicSessions().map(AcademicSession::name))
                        }.distinct(),
                        isReadOnly = roleAccessManager.currentContext().isFaculty,
                    ),
                )
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to load institution profile."))
            }
        }
    }

    fun save(draft: InstitutionDraft) {
        viewModelScope.launch {
            mutableSaveState.value = UiState.Loading
            mutableSaveState.value = try {
                institutionRepository.saveInstitution(draft)
                UiState.Success(Unit)
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to save institution profile."))
            }
        }
    }

    fun clearSaveState() {
        mutableSaveState.value = UiState.Idle
    }

    fun lookupPostalCode(postalCode: String) {
        viewModelScope.launch {
            mutableAddressLookupState.value = UiState.Loading
            mutableAddressLookupState.value = try {
                UiState.Success(locationValidationRepository.lookupIndianAddress(postalCode))
            } catch (throwable: Throwable) {
                UiState.Error(throwable.userMessage("Unable to verify postal code."))
            }
        }
    }

    fun clearAddressLookupState() {
        mutableAddressLookupState.value = UiState.Idle
    }
}
