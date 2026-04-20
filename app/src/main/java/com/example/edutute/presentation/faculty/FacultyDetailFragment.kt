package com.example.edutute.presentation.faculty

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.edutute.R
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.AttendanceDateUtils
import com.example.edutute.databinding.FragmentFacultyDetailBinding
import com.example.edutute.domain.model.AttendanceStatus
import com.google.android.material.chip.Chip
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class FacultyDetailFragment : Fragment() {

    private var bindingRef: FragmentFacultyDetailBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: FacultyDetailViewModel by viewModels { appViewModelFactory() }
    private lateinit var teachingAdapter: FacultyTeachingAssignmentsAdapter
    private var screenData: FacultyDetailScreenData? = null
    private val isSelfProfile: Boolean
        get() = arguments?.getBoolean("isSelfProfile", false) == true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentFacultyDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        teachingAdapter = FacultyTeachingAssignmentsAdapter()
        binding.teachingRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.teachingRecyclerView.adapter = teachingAdapter
        binding.editButton.setOnClickListener {
            if (isSelfProfile) return@setOnClickListener
            val args = Bundle().apply { putString("facultyId", requireArguments().getString("facultyId")) }
            findNavController().navigate(
                R.id.facultyFormFragment,
                args,
            )
        }
        binding.deleteButton.setOnClickListener {
            if (!isSelfProfile) {
                confirmDelete()
            }
        }
        binding.attendanceDateEditText.setOnClickListener {
            showDatePicker(screenData?.attendance?.selectedDate ?: AttendanceDateUtils.todayStorageDate())
        }
        binding.checkAttendanceButton.setOnClickListener { viewModel.searchAttendanceByDate() }
        binding.attendanceEditButton.setOnClickListener { viewModel.enterAttendanceEditMode() }
        binding.attendancePresentButton.setOnClickListener {
            viewModel.updateAttendanceStatus(AttendanceStatus.PRESENT)
        }
        binding.attendanceAbsentButton.setOnClickListener {
            viewModel.updateAttendanceStatus(AttendanceStatus.ABSENT)
        }
        binding.saveAttendanceRectificationButton.setOnClickListener {
            viewModel.saveAttendanceRectification()
        }
        observeState()
        binding.editButton.isVisible = !isSelfProfile
        binding.deleteButton.isVisible = !isSelfProfile
        viewModel.loadFaculty(requireArguments().getString("facultyId").orEmpty())
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.facultyState.collect { state ->
                        binding.progressBar.isVisible = state is UiState.Loading
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            is UiState.Success -> bindFaculty(state.data)
                        }
                    }
                }
                launch {
                    viewModel.deleteState.collect { state ->
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.clearDeleteState()
                            }
                            is UiState.Success -> {
                                Snackbar.make(binding.root, R.string.message_faculty_deleted, Snackbar.LENGTH_LONG).show()
                                viewModel.clearDeleteState()
                                findNavController().popBackStack(R.id.facultyListFragment, false)
                            }
                        }
                    }
                }
                launch {
                    viewModel.attendanceActionState.collect { state ->
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.clearAttendanceActionState()
                            }
                            is UiState.Success -> {
                                Snackbar.make(binding.root, state.data, Snackbar.LENGTH_LONG).show()
                                viewModel.clearAttendanceActionState()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bindFaculty(data: FacultyDetailScreenData) = with(binding) {
        screenData = data
        val faculty = data.faculty
        facultyNameText.text = faculty.fullName
        facultyDesignationText.text = faculty.employeeCode.ifBlank { getString(R.string.label_not_set) }
        facultyMonogramText.text = faculty.fullName
            .split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(separator = "") { token -> token.first().uppercase() }
            .ifBlank { "#" }
        employeeCodeValue.text = faculty.employeeCode
        emailValue.text = faculty.email.ifBlank { getString(R.string.label_no_email) }
        phoneValue.text = faculty.phoneNumber.ifBlank { getString(R.string.label_not_set) }
        qualificationValue.text = faculty.qualification.ifBlank { getString(R.string.label_not_set) }
        joiningDateValue.text = faculty.joiningDate.ifBlank { getString(R.string.label_not_set) }

        classTeacherChipGroup.removeAllViews()
        if (data.classTeacherOf.isEmpty()) {
            classTeacherChipGroup.isVisible = false
            classTeacherEmptyText.isVisible = true
        } else {
            classTeacherChipGroup.isVisible = true
            classTeacherEmptyText.isVisible = false
            data.classTeacherOf.forEach { classSection ->
                classTeacherChipGroup.addView(
                    Chip(requireContext()).apply {
                        text = classSection.displayName
                        isCheckable = false
                        isClickable = false
                        setEnsureMinTouchTargetSize(false)
                        chipBackgroundColor = ContextCompat.getColorStateList(requireContext(), R.color.brand_badge_fill)
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_badge_text))
                        chipStrokeWidth = 0f
                    },
                )
            }
        }

        teachingAdapter.submitList(data.teachingAssignments)
        teachingRecyclerView.isVisible = data.teachingAssignments.isNotEmpty()
        teachingEmptyText.isVisible = data.teachingAssignments.isEmpty()

        bindAttendance(data)
    }

    private fun bindAttendance(data: FacultyDetailScreenData) = with(binding) {
        val attendance = data.attendance

        attendanceScopeText.text = attendance.scopeMessage
        attendancePercentageValue.text = getString(
            R.string.label_percentage_value,
            attendance.summary.attendancePercentage,
        )
        daysAttendedValue.text = getString(R.string.label_number_value, attendance.summary.daysAttended)
        daysHeldValue.text = getString(R.string.label_number_value, attendance.summary.daysHeld)

        attendanceDateEditText.setText(AttendanceDateUtils.toDisplayDate(attendance.selectedDate))
        attendanceDateEditText.isEnabled = !attendance.isBusy
        checkAttendanceButton.isEnabled = !attendance.isBusy
        attendanceProgressBar.isVisible = attendance.isBusy

        attendanceResultCard.isVisible = attendance.hasSearchedDate
        attendanceResultDateText.text = getString(
            R.string.label_attendance_result_date,
            AttendanceDateUtils.toDisplayDate(attendance.selectedDate),
        )
        attendanceResultMessageText.text = attendance.resultMessage

        attendanceStatusValue.isVisible = attendance.hasRecordForSelectedDate
        attendanceStatusValue.text = getString(
            if (attendance.selectedDateStatus == AttendanceStatus.PRESENT) {
                R.string.label_present
            } else {
                R.string.label_absent
            },
        )
        attendanceStatusValue.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (attendance.selectedDateStatus == AttendanceStatus.PRESENT) {
                    R.color.brand_success
                } else {
                    R.color.brand_error
                },
            ),
        )

        attendanceEditButton.isVisible = attendance.hasRecordForSelectedDate && !attendance.isEditMode
        attendanceEditButton.isEnabled = attendance.hasRecordForSelectedDate && !attendance.isBusy

        attendanceStatusToggleGroup.isVisible = attendance.hasRecordForSelectedDate && attendance.isEditMode
        attendancePresentButton.isChecked = attendance.selectedDateStatus == AttendanceStatus.PRESENT
        attendanceAbsentButton.isChecked = attendance.selectedDateStatus == AttendanceStatus.ABSENT
        attendancePresentButton.isEnabled = attendance.isEditMode && !attendance.isBusy
        attendanceAbsentButton.isEnabled = attendance.isEditMode && !attendance.isBusy

        saveAttendanceRectificationButton.isVisible = attendance.hasRecordForSelectedDate && attendance.isEditMode
        saveAttendanceRectificationButton.isEnabled =
            attendance.hasRecordForSelectedDate && attendance.isEditMode && !attendance.isBusy
    }

    private fun showDatePicker(currentDate: String) {
        val calendar = AttendanceDateUtils.toCalendar(currentDate)
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                viewModel.updateAttendanceDate(
                    AttendanceDateUtils.fromCalendar(year, month, dayOfMonth),
                )
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_delete_faculty)
            .setMessage(R.string.message_delete_item_generic)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteFaculty(requireArguments().getString("facultyId").orEmpty())
            }
            .show()
    }
}
