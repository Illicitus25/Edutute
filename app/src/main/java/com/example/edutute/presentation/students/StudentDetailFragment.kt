package com.example.edutute.presentation.students

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
import com.example.edutute.R
import com.example.edutute.core.ui.UiState
import com.example.edutute.core.util.AttendanceDateUtils
import com.example.edutute.databinding.FragmentStudentDetailBinding
import com.example.edutute.domain.model.AttendanceStatus
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class StudentDetailFragment : Fragment() {

    private var bindingRef: FragmentStudentDetailBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: StudentDetailViewModel by viewModels { appViewModelFactory() }

    private var screenData: StudentDetailScreenData? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentStudentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.editButton.setOnClickListener {
            val navController = findNavController()
            if (navController.currentDestination?.id != R.id.studentDetailFragment) return@setOnClickListener
            val args = Bundle().apply { putString("studentId", arguments?.getString("studentId")) }
            navController.navigate(R.id.studentFormFragment, args)
        }
        binding.deleteButton.setOnClickListener { confirmDelete() }
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
        viewModel.loadStudent(arguments?.getString("studentId").orEmpty())
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.studentState.collect { state ->
                        binding.progressBar.isVisible = state is UiState.Loading
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            is UiState.Success -> bindScreen(state.data)
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
                                Snackbar.make(binding.root, R.string.message_student_deleted, Snackbar.LENGTH_LONG).show()
                                viewModel.clearDeleteState()
                                findNavController().popBackStack(R.id.studentsListFragment, false)
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

    private fun bindScreen(data: StudentDetailScreenData) = with(binding) {
        screenData = data
        bindStudent(data)
        bindAttendance(data)
    }

    private fun bindStudent(data: StudentDetailScreenData) = with(binding) {
        val student = data.student
        studentNameText.text = student.fullName
        admissionText.text = student.admissionNumber
        studentMonogramText.text = student.fullName
            .split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(separator = "") { token -> token.first().uppercase() }
            .ifBlank { "#" }
        classSectionValue.text = student.currentClassSectionName.ifBlank { getString(R.string.label_not_assigned) }
        rollNumberValue.text = student.currentRollNumber.ifBlank { getString(R.string.label_not_set) }
        guardianNameValue.text = student.guardianName
        guardianPhoneValue.text = student.guardianPhone
        emailValue.text = student.email.ifBlank { getString(R.string.label_no_email) }
        genderValue.text = student.gender.replace('_', ' ')
        dateOfBirthValue.text = student.dateOfBirth.ifBlank { getString(R.string.label_not_set) }
        addressValue.text = listOf(
            student.addressLine1,
            student.addressLine2,
            student.city,
            student.state,
            student.postalCode,
        ).filter { it.isNotBlank() }.joinToString(", ").ifBlank { getString(R.string.label_not_set) }
    }

    private fun bindAttendance(data: StudentDetailScreenData) = with(binding) {
        val attendance = data.attendance
        val canManageAttendance = data.student.currentClassSectionId.isNotBlank()

        attendanceScopeText.text = attendance.scopeMessage
        attendancePercentageValue.text = getString(
            R.string.label_percentage_value,
            attendance.summary.attendancePercentage,
        )
        daysAttendedValue.text = getString(R.string.label_number_value, attendance.summary.daysAttended)
        daysHeldValue.text = getString(R.string.label_number_value, attendance.summary.daysHeld)

        attendanceDateEditText.setText(AttendanceDateUtils.toDisplayDate(attendance.selectedDate))
        attendanceDateEditText.isEnabled = canManageAttendance
        checkAttendanceButton.isEnabled = canManageAttendance && !attendance.isBusy
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
        saveAttendanceRectificationButton.isEnabled = attendance.hasRecordForSelectedDate && attendance.isEditMode && !attendance.isBusy
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
            .setTitle(R.string.title_delete_student)
            .setMessage(R.string.message_delete_item_generic)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteStudent(arguments?.getString("studentId").orEmpty())
            }
            .show()
    }
}
