package com.example.edutute.presentation.attendance

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.edutute.R
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.AttendanceDateUtils
import com.example.edutute.databinding.FragmentAttendanceBinding
import com.example.edutute.domain.model.AttendanceStatus
import com.example.edutute.domain.model.ClassSection
import com.example.edutute.domain.model.SchoolClass
import com.example.edutute.domain.model.Section
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class AttendanceFragment : Fragment() {

    private data class DropdownOption(
        val id: String,
        val label: String,
    ) {
        override fun toString(): String = label
    }

    private var bindingRef: FragmentAttendanceBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: AttendanceViewModel by viewModels { appViewModelFactory() }

    private val facultyRosterAdapter by lazy {
        AttendanceRosterAdapter { itemId, status ->
            when (screenData.target) {
                AttendanceTarget.CLASS -> viewModel.updateClassAttendanceStatus(itemId, status)
                AttendanceTarget.FACULTY -> viewModel.updateFacultyAttendanceStatus(itemId, status)
            }
        }
    }

    private val classSheetAdapter by lazy {
        ClassAttendanceSheetAdapter { itemId, isPresent ->
            viewModel.updateClassAttendanceStatus(
                studentId = itemId,
                status = if (isPresent) AttendanceStatus.PRESENT else AttendanceStatus.ABSENT,
            )
        }
    }

    private var screenData: AttendanceScreenData = AttendanceScreenData()
    private var isBindingInputs = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentAttendanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupInteractions()
        observeState()
        viewModel.ensureLoaded()
    }

    private fun setupRecyclerView() {
        binding.rosterRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.rosterRecyclerView.adapter = classSheetAdapter
    }

    private fun setupInteractions() = with(binding) {
        markSectionButton.setOnClickListener { viewModel.updateMode(AttendanceMode.MARK) }
        viewSectionButton.setOnClickListener { viewModel.updateMode(AttendanceMode.VIEW) }
        classTypeButton.setOnClickListener { viewModel.updateTarget(AttendanceTarget.CLASS) }
        facultyTypeButton.setOnClickListener { viewModel.updateTarget(AttendanceTarget.FACULTY) }
        classOnlyTypeButton.setOnClickListener { viewModel.updateTarget(AttendanceTarget.CLASS) }
        loadAttendanceButton.setOnClickListener { viewModel.loadCurrentSelection() }
        editAttendanceButton.setOnClickListener { viewModel.enterEditMode() }
        markAllPresentButton.setOnClickListener { viewModel.markAllPresent() }
        markAllAbsentButton.setOnClickListener { viewModel.markAllAbsent() }
        saveAttendanceButton.setOnClickListener { viewModel.saveCurrentSelection() }

        classDropdown.doAfterTextChanged {
            if (isBindingInputs) return@doAfterTextChanged
            val selectedId = currentClassOptions().firstOrNull { option -> option.label == it?.toString().orEmpty() }?.id.orEmpty()
            viewModel.updateClassSelection(selectedId)
        }
        sectionDropdown.doAfterTextChanged {
            if (isBindingInputs) return@doAfterTextChanged
            val selectedId = currentSectionOptions().firstOrNull { option -> option.label == it?.toString().orEmpty() }?.id.orEmpty()
            viewModel.updateSectionSelection(selectedId)
        }

        classDateEditText.setOnClickListener {
            showDatePicker(screenData.classPanel.selectedDate) { selectedDate ->
                viewModel.updateClassDate(selectedDate)
            }
        }
        facultyDateEditText.setOnClickListener {
            showDatePicker(screenData.facultyPanel.selectedDate) { selectedDate ->
                viewModel.updateFacultyDate(selectedDate)
            }
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        binding.progressBar.isVisible = state is UiState.Loading
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            is UiState.Success -> render(state.data)
                        }
                    }
                }
                launch {
                    viewModel.actionState.collect { state ->
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.clearActionState()
                            }
                            is UiState.Success -> {
                                Snackbar.make(binding.root, state.data, Snackbar.LENGTH_LONG).show()
                                viewModel.clearActionState()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun render(data: AttendanceScreenData) = with(binding) {
        screenData = data
        isBindingInputs = true

        markSectionButton.isChecked = data.mode == AttendanceMode.MARK
        viewSectionButton.isChecked = data.mode == AttendanceMode.VIEW
        classTypeButton.isChecked = data.target == AttendanceTarget.CLASS
        facultyTypeButton.isChecked = data.target == AttendanceTarget.FACULTY
        targetToggleGroup.isVisible = data.canAccessFacultyAttendance
        classOnlyTypeButton.isVisible = !data.canAccessFacultyAttendance

        classFiltersGroup.isVisible = data.target == AttendanceTarget.CLASS
        facultyFiltersGroup.isVisible = data.target == AttendanceTarget.FACULTY && data.canAccessFacultyAttendance

        val classOptions = currentClassOptions()
        classDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, classOptions))
        classDropdown.setText(classOptions.firstOrNull { it.id == data.classPanel.selectedClassId }?.label.orEmpty(), false)

        val sectionOptions = currentSectionOptions()
        sectionDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sectionOptions))
        sectionDropdown.setText(sectionOptions.firstOrNull { it.id == data.classPanel.selectedSectionId }?.label.orEmpty(), false)

        classDateEditText.setText(AttendanceDateUtils.toDisplayDate(data.classPanel.selectedDate))
        facultyDateEditText.setText(AttendanceDateUtils.toDisplayDate(data.facultyPanel.selectedDate))

        isBindingInputs = false

        contentTitleText.text = contentTitle(data)
        contentSubtitleText.text = contentSubtitle(data)
        loadAttendanceButton.text = loadActionLabel(data)

        val classSheetItems = classSheetItems(data)
        val facultyItems = facultyRosterItems(data)
        val activeItemCount = if (data.target == AttendanceTarget.CLASS) classSheetItems.size else facultyItems.size
        val hasLoadedData = activeHasLoadedData(data)
        val isEditable = activeIsEditable(data)

        classSheetAdapter.setEditable(data.mode == AttendanceMode.MARK || data.classPanel.isEditMode)
        facultyRosterAdapter.setEditable(data.mode == AttendanceMode.MARK || data.facultyPanel.isEditMode)
        if (data.target == AttendanceTarget.CLASS) {
            if (rosterRecyclerView.adapter !== classSheetAdapter) {
                rosterRecyclerView.adapter = classSheetAdapter
            }
            classSheetAdapter.submitList(classSheetItems)
        } else {
            if (rosterRecyclerView.adapter !== facultyRosterAdapter) {
                rosterRecyclerView.adapter = facultyRosterAdapter
            }
            facultyRosterAdapter.submitList(facultyItems)
        }

        val activePanelMessage = activeMessage(data)
        panelProgressBar.isVisible = isPanelWorking(data)
        panelMessageText.isVisible = shouldShowPanelMessage(
            message = activePanelMessage,
            hasLoadedData = hasLoadedData,
            activeItemCount = activeItemCount,
        )
        panelMessageText.text = activePanelMessage.text
        panelMessageText.setTextColor(ContextCompat.getColor(requireContext(), messageColor(activePanelMessage.tone)))

        recordBadgeText.text = recordBadgeText(data)
        countBadgeText.text = countBadgeText(data)
        saveAttendanceButton.text = saveActionLabel(data)
        markAllAbsentButton.text = if (data.target == AttendanceTarget.CLASS) {
            getString(R.string.action_clear_all_mark_absent)
        } else {
            getString(R.string.action_mark_all_absent)
        }

        val hasRows = activeItemCount > 0
        attendanceSheetCard.isVisible = hasLoadedData
        statusBadgeRow.isVisible = hasLoadedData
        editAttendanceButton.isVisible = data.mode == AttendanceMode.VIEW && hasRows && !isEditable && data.canRectifySavedRecords
        editAttendanceButton.isEnabled = hasRows && !isPanelWorking(data) && data.canRectifySavedRecords
        saveAttendanceButton.isEnabled = hasRows && isEditable && !isPanelWorking(data)
        markAllPresentButton.isEnabled = hasRows && isEditable && !isPanelWorking(data)
        markAllAbsentButton.isEnabled = hasRows && isEditable && !isPanelWorking(data)
        actionButtonsGroup.isVisible = hasRows && isEditable

        classSheetHeaderRow.isVisible = data.target == AttendanceTarget.CLASS && hasRows
        classSheetHeaderDivider.isVisible = classSheetHeaderRow.isVisible

        emptyStateText.isVisible = hasLoadedData && activeItemCount == 0 && !isPanelWorking(data)
        emptyStateText.text = emptyStateMessage(data)
    }

    private fun contentTitle(data: AttendanceScreenData): String = when {
        data.mode == AttendanceMode.MARK && data.target == AttendanceTarget.CLASS -> getString(R.string.title_class_attendance)
        data.mode == AttendanceMode.MARK && data.target == AttendanceTarget.FACULTY -> getString(R.string.title_faculty_attendance)
        data.mode == AttendanceMode.VIEW && data.target == AttendanceTarget.CLASS -> getString(R.string.title_class_attendance_records)
        else -> getString(R.string.title_faculty_attendance_records)
    }

    private fun contentSubtitle(data: AttendanceScreenData): String = when {
        data.mode == AttendanceMode.MARK && data.target == AttendanceTarget.CLASS ->
            getString(R.string.subtitle_mark_class_attendance)
        data.mode == AttendanceMode.MARK && data.target == AttendanceTarget.FACULTY ->
            getString(R.string.subtitle_mark_faculty_attendance)
        data.mode == AttendanceMode.VIEW && data.target == AttendanceTarget.CLASS ->
            getString(R.string.subtitle_view_class_attendance)
        else -> getString(R.string.subtitle_view_faculty_attendance)
    }

    private fun loadActionLabel(data: AttendanceScreenData): String = when {
        data.mode == AttendanceMode.MARK && data.target == AttendanceTarget.CLASS ->
            getString(R.string.action_load_student_roster)
        data.mode == AttendanceMode.MARK && data.target == AttendanceTarget.FACULTY ->
            getString(R.string.action_load_faculty_roster)
        data.mode == AttendanceMode.VIEW && data.target == AttendanceTarget.CLASS ->
            getString(R.string.action_search_class_record)
        else -> getString(R.string.action_search_faculty_record)
    }

    private fun saveActionLabel(data: AttendanceScreenData): String = when (data.target) {
        AttendanceTarget.CLASS -> {
            if (data.classPanel.isExistingRecord) {
                getString(R.string.action_update_class_attendance)
            } else {
                getString(R.string.action_submit_attendance)
            }
        }

        AttendanceTarget.FACULTY -> {
            if (data.facultyPanel.isExistingRecord) {
                getString(R.string.action_update_faculty_attendance)
            } else {
                getString(R.string.action_save_faculty_attendance)
            }
        }
    }

    private fun recordBadgeText(data: AttendanceScreenData): String = when (data.target) {
        AttendanceTarget.CLASS -> when {
            data.classPanel.isExistingRecord -> getString(R.string.label_existing_record)
            data.classPanel.entries.isNotEmpty() -> getString(R.string.label_new_record)
            else -> getString(R.string.label_attendance_not_loaded)
        }

        AttendanceTarget.FACULTY -> when {
            data.facultyPanel.isExistingRecord -> getString(R.string.label_existing_record)
            data.facultyPanel.entries.isNotEmpty() -> getString(R.string.label_new_record)
            else -> getString(R.string.label_attendance_not_loaded)
        }
    }

    private fun countBadgeText(data: AttendanceScreenData): String = when (data.target) {
        AttendanceTarget.CLASS -> {
            val total = data.classPanel.entries.size
            val present = data.classPanel.entries.count { it.status == AttendanceStatus.PRESENT }
            val absent = total - present
            if (total == 0) {
                getString(R.string.label_total_count_value, 0)
            } else {
                getString(R.string.label_attendance_count_summary, present, absent, total)
            }
        }

        AttendanceTarget.FACULTY -> {
            val total = data.facultyPanel.entries.size
            val present = data.facultyPanel.entries.count { it.status == AttendanceStatus.PRESENT }
            val absent = total - present
            if (total == 0) {
                getString(R.string.label_total_count_value, 0)
            } else {
                getString(R.string.label_attendance_count_summary, present, absent, total)
            }
        }
    }

    private fun classSheetItems(data: AttendanceScreenData): List<ClassAttendanceSheetItem> = data.classPanel.entries.map { entry ->
        ClassAttendanceSheetItem(
            id = entry.studentId,
            rollNumber = entry.rollNumber,
            studentName = entry.fullName,
            isPresent = entry.status == AttendanceStatus.PRESENT,
        )
    }

    private fun facultyRosterItems(data: AttendanceScreenData): List<AttendanceRosterListItem> = data.facultyPanel.entries.map { entry ->
        AttendanceRosterListItem(
            id = entry.facultyId,
            title = entry.fullName,
            subtitle = entry.employeeCode.ifBlank { getString(R.string.label_employee_code_missing) },
            meta = entry.qualification.ifBlank { getString(R.string.label_qualification_missing) },
            status = entry.status,
        )
    }

    private fun emptyStateMessage(data: AttendanceScreenData): String {
        val message = activeMessage(data).text
        if (activeHasLoadedData(data) && message.isNotBlank()) return message
        return when {
            data.target == AttendanceTarget.CLASS && data.mode == AttendanceMode.MARK ->
                getString(R.string.empty_mark_class_attendance)
            data.target == AttendanceTarget.FACULTY && data.mode == AttendanceMode.MARK ->
                getString(R.string.empty_mark_faculty_attendance)
            data.target == AttendanceTarget.CLASS && data.mode == AttendanceMode.VIEW ->
                getString(R.string.empty_view_class_attendance)
            else -> getString(R.string.empty_view_faculty_attendance)
        }
    }

    private fun activeHasLoadedData(data: AttendanceScreenData): Boolean = when (data.target) {
        AttendanceTarget.CLASS -> data.classPanel.hasLoadedData
        AttendanceTarget.FACULTY -> data.facultyPanel.hasLoadedData
    }

    private fun activeIsEditable(data: AttendanceScreenData): Boolean = when (data.target) {
        AttendanceTarget.CLASS -> data.mode == AttendanceMode.MARK || data.classPanel.isEditMode
        AttendanceTarget.FACULTY -> data.mode == AttendanceMode.MARK || data.facultyPanel.isEditMode
    }

    private fun shouldShowPanelMessage(
        message: AttendanceMessage,
        hasLoadedData: Boolean,
        activeItemCount: Int,
    ): Boolean {
        if (message.text.isBlank()) return false
        return !(hasLoadedData && activeItemCount == 0)
    }

    private fun activeMessage(data: AttendanceScreenData): AttendanceMessage = when (data.target) {
        AttendanceTarget.CLASS -> data.classPanel.message
        AttendanceTarget.FACULTY -> data.facultyPanel.message
    }

    private fun isPanelWorking(data: AttendanceScreenData): Boolean = when (data.target) {
        AttendanceTarget.CLASS -> data.classPanel.isWorking
        AttendanceTarget.FACULTY -> data.facultyPanel.isWorking
    }

    private fun messageColor(tone: AttendanceMessageTone): Int = when (tone) {
        AttendanceMessageTone.INFO -> R.color.brand_text_secondary
        AttendanceMessageTone.SUCCESS -> R.color.brand_success
        AttendanceMessageTone.WARNING -> R.color.brand_warning
        AttendanceMessageTone.ERROR -> R.color.brand_error
    }

    private fun currentClassOptions(): List<DropdownOption> = screenData.classes
        .filter { schoolClass ->
            eligibleClassSections().any { it.classId == schoolClass.id }
        }
        .sortedBy(SchoolClass::displayOrder)
        .map { schoolClass ->
            DropdownOption(id = schoolClass.id, label = schoolClass.name)
        }

    private fun currentSectionOptions(): List<DropdownOption> {
        val classId = screenData.classPanel.selectedClassId
        val sectionIds = eligibleClassSections()
            .filter { classSection -> classId.isBlank() || classSection.classId == classId }
            .map(ClassSection::sectionId)
            .toSet()

        val eligibleSections = if (classId.isBlank()) {
            screenData.sections
        } else {
            screenData.sections.filter { section -> section.id in sectionIds }
        }

        return eligibleSections
            .sortedBy(Section::displayOrder)
            .map { section ->
                DropdownOption(id = section.id, label = section.name)
            }
    }

    private fun eligibleClassSections(): List<ClassSection> = when {
        screenData.isFacultyUser && screenData.mode == AttendanceMode.MARK ->
            screenData.classSections.filter { it.id in screenData.markableClassSectionIds }

        else -> screenData.classSections
    }

    private fun showDatePicker(
        currentDate: String,
        onDateSelected: (String) -> Unit,
    ) {
        val calendar = AttendanceDateUtils.toCalendar(currentDate)
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                onDateSelected(AttendanceDateUtils.fromCalendar(year, month, dayOfMonth))
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
        ).show()
    }
}
