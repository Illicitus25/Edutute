package com.example.edutute.presentation.students

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.edutute.R
import com.example.edutute.core.ui.UiState
import com.example.edutute.databinding.FragmentStudentFormBinding
import com.example.edutute.domain.model.Gender
import com.example.edutute.domain.model.StudentDraft
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class StudentFormFragment : Fragment() {

    private var bindingRef: FragmentStudentFormBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: StudentFormViewModel by viewModels { appViewModelFactory() }

    private var formData: StudentFormData = StudentFormData(null, emptyList())
    private var isFormattingDob = false
    private var isFormattingPostalCode = false
    private var lastPostalLookup = ""
    private val studentId: String?
        get() = arguments?.getString("studentId")?.takeIf { it.isNotBlank() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentStudentFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.saveButton.setOnClickListener { saveStudent() }
        setupInputBehaviors()
        observeState()
        viewModel.load(studentId)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.formState.collect { state ->
                        binding.progressBar.isVisible = state is UiState.Loading
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            is UiState.Success -> bindForm(state.data)
                        }
                    }
                }
                launch {
                    viewModel.saveState.collect { state ->
                        binding.saveButton.isEnabled = state !is UiState.Loading
                        binding.saveProgressBar.isVisible = state is UiState.Loading
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.clearSaveState()
                            }
                            is UiState.Success -> {
                                Snackbar.make(binding.root, R.string.message_student_saved, Snackbar.LENGTH_LONG).show()
                                viewModel.clearSaveState()
                                findNavController().popBackStack()
                            }
                        }
                    }
                }
                launch {
                    viewModel.addressLookupState.collect { state ->
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.clearAddressLookupState()
                            }
                            is UiState.Success -> {
                                binding.cityEditText.setText(state.data.city)
                                binding.stateEditText.setText(state.data.state)
                                binding.postalCodeEditText.setText(state.data.postalCode)
                                viewModel.clearAddressLookupState()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupInputBehaviors() = with(binding) {
        dateOfBirthEditText.doAfterTextChanged { editable ->
            if (isFormattingDob) return@doAfterTextChanged
            val formatted = formatDateInput(editable)
            if (formatted != editable?.toString().orEmpty()) {
                isFormattingDob = true
                dateOfBirthEditText.text = Editable.Factory.getInstance().newEditable(formatted)
                dateOfBirthEditText.setSelection(formatted.length)
                isFormattingDob = false
            }
        }

        postalCodeEditText.doAfterTextChanged { editable ->
            if (isFormattingPostalCode) return@doAfterTextChanged
            val normalizedPostalCode = editable?.toString().orEmpty().filter { it.isDigit() }.take(6)
            if (normalizedPostalCode != editable?.toString().orEmpty()) {
                isFormattingPostalCode = true
                postalCodeEditText.text = Editable.Factory.getInstance().newEditable(normalizedPostalCode)
                postalCodeEditText.setSelection(normalizedPostalCode.length)
                isFormattingPostalCode = false
                return@doAfterTextChanged
            }

            if (normalizedPostalCode.length == 6 && normalizedPostalCode != lastPostalLookup) {
                lastPostalLookup = normalizedPostalCode
                viewModel.lookupPostalCode(normalizedPostalCode)
            } else if (normalizedPostalCode.length < 6) {
                lastPostalLookup = ""
            }
        }
    }

    private fun bindForm(data: StudentFormData) = with(binding) {
        formData = data
        val genders = Gender.entries.map { it.name.replace('_', ' ') }
        genderDropdown.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, genders))
        admissionNumberEditText.setText(
            data.student?.admissionNumber.orEmpty().ifBlank { getString(R.string.label_auto_generated_on_save) },
        )

        data.student?.let { student ->
            firstNameEditText.setText(student.firstName)
            lastNameEditText.setText(student.lastName)
            genderDropdown.setText(student.gender.replace('_', ' '), false)
            dateOfBirthEditText.setText(student.dateOfBirth)
            guardianNameEditText.setText(student.guardianName)
            guardianPhoneEditText.setText(student.guardianPhone)
            emailEditText.setText(student.email)
            addressLineOneEditText.setText(student.addressLine1)
            addressLineTwoEditText.setText(student.addressLine2)
            cityEditText.setText(student.city)
            stateEditText.setText(student.state)
            lastPostalLookup = student.postalCode
            postalCodeEditText.setText(student.postalCode)
        }
    }

    private fun formatDateInput(editable: Editable?): String {
        val digits = editable?.toString().orEmpty().filter { it.isDigit() }.take(8)
        return buildString {
            digits.forEachIndexed { index, character ->
                append(character)
                if ((index == 1 || index == 3) && index != digits.lastIndex) {
                    append('/')
                }
            }
        }
    }

    private fun saveStudent() = with(binding) {
        viewModel.save(
            StudentDraft(
                id = studentId.orEmpty(),
                admissionNumber = formData.student?.admissionNumber.orEmpty(),
                firstName = firstNameEditText.text?.toString().orEmpty(),
                lastName = lastNameEditText.text?.toString().orEmpty(),
                gender = genderDropdown.text?.toString()?.replace(' ', '_')?.uppercase()
                    .orEmpty()
                    .ifBlank { Gender.UNSPECIFIED.name },
                dateOfBirth = dateOfBirthEditText.text?.toString().orEmpty(),
                guardianName = guardianNameEditText.text?.toString().orEmpty(),
                guardianPhone = guardianPhoneEditText.text?.toString().orEmpty(),
                email = emailEditText.text?.toString().orEmpty(),
                addressLine1 = addressLineOneEditText.text?.toString().orEmpty(),
                addressLine2 = addressLineTwoEditText.text?.toString().orEmpty(),
                city = cityEditText.text?.toString().orEmpty(),
                state = stateEditText.text?.toString().orEmpty(),
                postalCode = postalCodeEditText.text?.toString().orEmpty(),
                classSectionId = formData.student?.currentClassSectionId.orEmpty(),
                rollNumber = formData.student?.currentRollNumber.orEmpty(),
            ),
        )
    }
}
