package com.example.edutute.presentation.classes

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
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
import com.example.edutute.databinding.DialogAddStudentsBinding
import com.example.edutute.databinding.DialogSingleSelectBinding
import com.example.edutute.databinding.FragmentClassDetailsBinding
import com.example.edutute.domain.model.AttendanceStatus
import com.example.edutute.domain.model.Student
import com.example.edutute.domain.model.toAttendanceStatus
import com.example.edutute.presentation.attendance.ClassAttendanceSheetAdapter
import com.example.edutute.presentation.attendance.ClassAttendanceSheetItem
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ClassDetailsFragment : Fragment() {

    private var bindingRef: FragmentClassDetailsBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: ClassDetailsViewModel by viewModels { appViewModelFactory() }

    private lateinit var studentsAdapter: ClassDetailsStudentsAdapter
    private lateinit var subjectsAdapter: ClassDetailsSubjectsAdapter
    private lateinit var attendanceAdapter: ClassAttendanceSheetAdapter
    private var pendingDelete = false

    private val classSectionId: String
        get() = arguments?.getString("classSectionId").orEmpty()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentClassDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        studentsAdapter = ClassDetailsStudentsAdapter(
            onRemove = { student -> confirmRemoveStudent(student) },
        )
        subjectsAdapter = ClassDetailsSubjectsAdapter(
            onChangeTeacher = { subjectItem ->
                val currentState = viewModel.state.value
                if (currentState is UiState.Success) {
                    showSubjectTeacherDialog(currentState.data, subjectItem)
                }
            },
            onRemoveSubject = { subjectItem -> confirmRemoveSubject(subjectItem) },
        )
        attendanceAdapter = ClassAttendanceSheetAdapter { studentId, isPresent ->
            viewModel.updateAttendanceStatus(
                studentId = studentId,
                status = if (isPresent) AttendanceStatus.PRESENT else AttendanceStatus.ABSENT,
            )
        }

        binding.studentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.studentsRecyclerView.adapter = studentsAdapter
        binding.subjectsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.subjectsRecyclerView.adapter = subjectsAdapter
        binding.attendanceRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.attendanceRecyclerView.adapter = attendanceAdapter

        binding.classTeacherActionButton.setOnClickListener {
            val currentState = viewModel.state.value
            if (currentState is UiState.Success) {
                showClassTeacherDialog(currentState.data)
            }
        }
        binding.addStudentButton.setOnClickListener {
            val currentState = viewModel.state.value
            if (currentState is UiState.Success) {
                showAddStudentsDialog(currentState.data)
            }
        }
        binding.addSubjectButton.setOnClickListener {
            val currentState = viewModel.state.value
            if (currentState is UiState.Success) {
                showAddSubjectDialog(currentState.data)
            }
        }
        binding.deleteClassButton.setOnClickListener {
            val currentState = viewModel.state.value
            if (currentState is UiState.Success) {
                confirmDeleteClass(currentState.data.classSection.displayName)
            }
        }
        binding.attendanceDateEditText.setOnClickListener {
            val currentState = viewModel.state.value as? UiState.Success ?: return@setOnClickListener
            showDatePicker(currentState.data.attendance.selectedDate)
        }
        binding.checkAttendanceButton.setOnClickListener { viewModel.searchAttendanceByDate() }
        binding.attendanceEditButton.setOnClickListener { viewModel.enterAttendanceEditMode() }
        binding.saveAttendanceRectificationButton.setOnClickListener { viewModel.saveAttendanceRectification() }

        observeState()
        viewModel.load(classSectionId)
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
                                pendingDelete = false
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.clearActionState()
                            }
                            is UiState.Success -> {
                                if (pendingDelete) {
                                    pendingDelete = false
                                    Snackbar.make(binding.root, R.string.message_class_deleted, Snackbar.LENGTH_LONG).show()
                                    viewModel.clearActionState()
                                    findNavController().popBackStack(R.id.classesFragment, false)
                                } else {
                                    Snackbar.make(binding.root, R.string.message_classes_saved, Snackbar.LENGTH_LONG).show()
                                    viewModel.clearActionState()
                                }
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

    private fun render(data: ClassDetailsScreenData) = with(binding) {
        summaryClassNameText.text = data.classSection.className
        summarySectionValueText.text = getString(R.string.label_section_value, data.classSection.sectionName)
        summaryStudentsValueText.text = data.assignedStudents.size.toString()
        summarySubjectsValueText.text = data.assignedSubjects.size.toString()
        classTeacherNameText.text = data.classSection.classTeacherName.ifBlank { getString(R.string.label_not_assigned) }
        classTeacherHintText.text = data.classSection.classTeacherName.ifBlank { getString(R.string.label_no_class_teacher_assigned) }

        studentsAdapter.submitList(data.assignedStudents)
        studentsAdapter.setCanManageStudents(data.canManageClass)
        emptyStudentsText.isVisible = data.assignedStudents.isEmpty()
        studentsRecyclerView.isVisible = data.assignedStudents.isNotEmpty()
        addStudentButton.isVisible = data.canManageClass
        addStudentButton.isEnabled = data.canManageClass && data.availableStudents.isNotEmpty()

        subjectsAdapter.submitList(data.assignedSubjects)
        subjectsAdapter.setCanManageSubjects(data.canManageClass)
        emptySubjectsText.isVisible = data.assignedSubjects.isEmpty()
        subjectsRecyclerView.isVisible = data.assignedSubjects.isNotEmpty()
        addSubjectButton.isVisible = data.canManageClass
        addSubjectButton.isEnabled = data.canManageClass && data.availableSubjects.isNotEmpty()
        classTeacherActionButton.isVisible = data.canManageClass
        deleteClassButton.isVisible = data.canManageClass

        bindAttendance(data)
    }

    private fun bindAttendance(data: ClassDetailsScreenData) = with(binding) {
        val attendance = data.attendance
        val items = attendance.entries.map { entry ->
            ClassAttendanceSheetItem(
                id = entry.studentId,
                rollNumber = entry.rollNumber,
                studentName = entry.fullName,
                isPresent = entry.status.toAttendanceStatus() == AttendanceStatus.PRESENT,
            )
        }
        val hasRows = items.isNotEmpty()

        attendanceDateEditText.setText(AttendanceDateUtils.toDisplayDate(attendance.selectedDate))
        attendanceProgressBar.isVisible = attendance.isBusy
        checkAttendanceButton.isEnabled = !attendance.isBusy

        attendanceAdapter.setEditable(attendance.isEditMode)
        attendanceAdapter.submitList(items)

        attendanceResultCard.isVisible = attendance.hasSearchedDate
        attendanceResultDateText.text = getString(
            R.string.label_attendance_result_date,
            AttendanceDateUtils.toDisplayDate(attendance.selectedDate),
        )
        attendanceResultMessageText.text = attendance.resultMessage
        attendanceResultMessageText.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (attendance.hasRecordForSelectedDate) {
                    R.color.brand_text_secondary
                } else {
                    R.color.brand_warning
                },
            ),
        )

        attendanceSummaryRow.isVisible = attendance.hasRecordForSelectedDate && hasRows
        attendanceSummaryCountBadgeText.text = getString(
            R.string.label_attendance_count_summary,
            attendance.presentCount,
            attendance.absentCount,
            attendance.totalStudents,
        )
        attendanceSummaryPercentBadgeText.text = getString(
            R.string.label_percentage_attendance_value,
            attendance.attendancePercentage,
        )

        attendanceEditButton.isVisible =
            data.canRectifyAttendance && attendance.hasRecordForSelectedDate && hasRows && !attendance.isEditMode
        attendanceEditButton.isEnabled = data.canRectifyAttendance && attendance.hasRecordForSelectedDate && hasRows && !attendance.isBusy
        attendanceListHeaderRow.isVisible = attendance.hasRecordForSelectedDate && hasRows
        attendanceListHeaderDivider.isVisible = attendanceListHeaderRow.isVisible
        attendanceRecyclerView.isVisible = attendance.hasRecordForSelectedDate && hasRows
        attendanceEmptyText.isVisible = attendance.hasRecordForSelectedDate && !hasRows && !attendance.isBusy
        saveAttendanceRectificationButton.isVisible =
            data.canRectifyAttendance && attendance.hasRecordForSelectedDate && attendance.isEditMode && hasRows
        saveAttendanceRectificationButton.isEnabled =
            data.canRectifyAttendance && attendance.hasRecordForSelectedDate && attendance.isEditMode && hasRows && !attendance.isBusy
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

    private fun showClassTeacherDialog(data: ClassDetailsScreenData) {
        val dialogBinding = DialogSingleSelectBinding.inflate(layoutInflater)
        dialogBinding.helperText.text = getString(
            R.string.message_select_teacher_for_class,
            data.classSection.displayName,
        )
        val teacherNames = listOf(getString(R.string.label_not_assigned)) + data.faculty.map { it.fullName }
        dialogBinding.selectionDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, teacherNames),
        )
        dialogBinding.selectionDropdown.setText(
            data.classSection.classTeacherName.ifBlank { getString(R.string.label_not_assigned) },
            false,
        )
        lateinit var dialog: Dialog
        dialog = createDialogShell(
            title = getString(R.string.title_change_class_teacher),
            contentView = dialogBinding.root,
            positiveLabel = getString(R.string.action_save),
            onPositive = {
                val selectedTeacherName = dialogBinding.selectionDropdown.text?.toString().orEmpty()
                val selectedTeacher = data.faculty.firstOrNull { it.fullName == selectedTeacherName }
                viewModel.saveClassTeacher(selectedTeacher?.id.orEmpty())
                dialog.dismiss()
            },
        )
        dialog.show()
    }

    private fun showAddStudentsDialog(data: ClassDetailsScreenData) {
        if (data.availableStudents.isEmpty()) {
            Snackbar.make(binding.root, R.string.message_no_available_students, Snackbar.LENGTH_LONG).show()
            return
        }
        val dialogBinding = DialogAddStudentsBinding.inflate(layoutInflater)
        val selectionAdapter = ClassDetailsStudentSelectionAdapter()
        dialogBinding.studentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        dialogBinding.studentsRecyclerView.adapter = selectionAdapter
        selectionAdapter.submitList(data.availableStudents)
        dialogBinding.emptyText.isVisible = data.availableStudents.isEmpty()
        dialogBinding.studentsRecyclerView.isVisible = data.availableStudents.isNotEmpty()

        lateinit var dialog: Dialog
        dialog = createDialogShell(
            title = getString(R.string.title_add_students),
            contentView = dialogBinding.root,
            positiveLabel = getString(R.string.action_add_student),
            onPositive = {
                viewModel.addStudents(selectionAdapter.selectedStudentIds())
                dialog.dismiss()
            },
        )
        dialog.show()
    }

    private fun showAddSubjectDialog(data: ClassDetailsScreenData) {
        if (data.availableSubjects.isEmpty()) {
            Snackbar.make(binding.root, R.string.message_no_available_subjects, Snackbar.LENGTH_LONG).show()
            return
        }
        val dialogBinding = DialogSingleSelectBinding.inflate(layoutInflater)
        dialogBinding.helperText.text = getString(R.string.message_select_subject_for_class, data.classSection.displayName)
        val subjectNames = data.availableSubjects.map { it.name }
        dialogBinding.selectionDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, subjectNames),
        )

        lateinit var dialog: Dialog
        dialog = createDialogShell(
            title = getString(R.string.title_add_subject_to_class),
            contentView = dialogBinding.root,
            positiveLabel = getString(R.string.action_add_subject),
            onPositive = {
                val selectedSubject = data.availableSubjects.firstOrNull {
                    it.name == dialogBinding.selectionDropdown.text?.toString().orEmpty()
                }
                viewModel.addSubject(selectedSubject?.id.orEmpty())
                dialog.dismiss()
            },
        )
        dialog.show()
    }

    private fun showSubjectTeacherDialog(
        data: ClassDetailsScreenData,
        subjectItem: ClassSubjectItem,
    ) {
        val dialogBinding = DialogSingleSelectBinding.inflate(layoutInflater)
        dialogBinding.helperText.text = getString(
            R.string.message_select_teacher_for_subject,
            subjectItem.subjectName,
            data.classSection.displayName,
        )
        val teacherNames = listOf(getString(R.string.label_not_assigned)) + data.faculty.map { it.fullName }
        dialogBinding.selectionDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, teacherNames),
        )
        dialogBinding.selectionDropdown.setText(
            subjectItem.teacherName.ifBlank { getString(R.string.label_not_assigned) },
            false,
        )

        lateinit var dialog: Dialog
        dialog = createDialogShell(
            title = getString(R.string.title_change_subject_teacher),
            contentView = dialogBinding.root,
            positiveLabel = getString(R.string.action_save),
            onPositive = {
                val selectedTeacherName = dialogBinding.selectionDropdown.text?.toString().orEmpty()
                val selectedTeacher = data.faculty.firstOrNull { it.fullName == selectedTeacherName }
                viewModel.updateSubjectTeacher(subjectItem.subjectId, selectedTeacher?.id.orEmpty())
                dialog.dismiss()
            },
        )
        dialog.show()
    }

    private fun confirmRemoveStudent(student: Student) {
        val content = TextView(requireContext()).apply {
            text = getString(R.string.message_remove_student_value, student.fullName)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_sm),
            )
        }
        lateinit var dialog: Dialog
        dialog = createDialogShell(
            title = getString(R.string.action_remove),
            contentView = content,
            positiveLabel = getString(R.string.action_remove),
            onPositive = {
                viewModel.removeStudent(student.id)
                dialog.dismiss()
            },
        )
        dialog.show()
    }

    private fun confirmRemoveSubject(subjectItem: ClassSubjectItem) {
        val content = TextView(requireContext()).apply {
            text = getString(R.string.message_remove_subject_value, subjectItem.subjectName)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_sm),
            )
        }
        lateinit var dialog: Dialog
        dialog = createDialogShell(
            title = getString(R.string.action_remove),
            contentView = content,
            positiveLabel = getString(R.string.action_remove),
            onPositive = {
                viewModel.removeSubject(subjectItem.subjectId)
                dialog.dismiss()
            },
        )
        dialog.show()
    }

    private fun confirmDeleteClass(classDisplayName: String) {
        val content = TextView(requireContext()).apply {
            text = getString(R.string.message_delete_class_value, classDisplayName)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_sm),
            )
        }
        lateinit var dialog: Dialog
        dialog = createDialogShell(
            title = getString(R.string.title_delete_class_section),
            contentView = content,
            positiveLabel = getString(R.string.action_delete_class),
            positiveStyle = R.style.Widget_Edutute_Button_Danger,
            onPositive = {
                pendingDelete = true
                viewModel.deleteClassSection()
                dialog.dismiss()
            },
        )
        dialog.show()
    }

    private fun createDialogShell(
        title: String,
        contentView: View,
        positiveLabel: String,
        positiveStyle: Int = R.style.Widget_Edutute_Button_Primary,
        onPositive: () -> Unit,
    ): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setTitle(title)
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                contentView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val actionsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                0,
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
            )
        }
        actionsRow.addView(
            MaterialButton(
                ContextThemeWrapper(requireContext(), R.style.Widget_Edutute_Button_Outlined),
                null,
                0,
            ).apply {
                text = getString(R.string.action_cancel)
                setOnClickListener { dialog.dismiss() }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            },
        )
        actionsRow.addView(
            MaterialButton(
                ContextThemeWrapper(requireContext(), positiveStyle),
                null,
                0,
            ).apply {
                text = positiveLabel
                setOnClickListener { onPositive() }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            },
        )
        container.addView(
            actionsRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        dialog.setContentView(container)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return dialog
    }
}
