package com.example.edutute.presentation.faculty

import android.os.Bundle
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.example.edutute.databinding.FragmentFacultyFormBinding
import com.example.edutute.domain.model.FacultyDraft
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class FacultyFormFragment : Fragment() {

    private var bindingRef: FragmentFacultyFormBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: FacultyFormViewModel by viewModels { appViewModelFactory() }
    private var isFormattingJoiningDate = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentFacultyFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.saveButton.setOnClickListener { saveFaculty() }
        setupInputBehaviors()
        observeState()
        viewModel.loadFaculty(requireArguments().getString("facultyId"))
    }

    private fun setupInputBehaviors() = with(binding) {
        joiningDateEditText.doAfterTextChanged { editable ->
            if (isFormattingJoiningDate) return@doAfterTextChanged
            val formatted = formatDateInput(editable)
            if (formatted != editable?.toString().orEmpty()) {
                isFormattingJoiningDate = true
                joiningDateEditText.text = Editable.Factory.getInstance().newEditable(formatted)
                joiningDateEditText.setSelection(formatted.length)
                isFormattingJoiningDate = false
            }
        }
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
                                val message = if (state.data.activationEmailSent) {
                                    getString(R.string.message_faculty_saved_with_activation, state.data.faculty.email)
                                } else {
                                    getString(R.string.message_faculty_saved)
                                }
                                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                                viewModel.clearSaveState()
                                findNavController().popBackStack()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun bindFaculty(faculty: com.example.edutute.domain.model.FacultyMember?) = with(binding) {
        if (faculty == null) {
            employeeCodeEditText.setText(getString(R.string.label_auto_generated_on_save))
            return@with
        }
        fullNameEditText.setText(faculty.fullName)
        employeeCodeEditText.setText(faculty.employeeCode)
        emailEditText.setText(faculty.email)
        phoneEditText.setText(faculty.phoneNumber)
        qualificationEditText.setText(faculty.qualification)
        joiningDateEditText.setText(faculty.joiningDate)
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

    private fun saveFaculty() = with(binding) {
        viewModel.saveFaculty(
            FacultyDraft(
                id = requireArguments().getString("facultyId").orEmpty(),
                fullName = fullNameEditText.text?.toString().orEmpty(),
                email = emailEditText.text?.toString().orEmpty(),
                phoneNumber = phoneEditText.text?.toString().orEmpty(),
                qualification = qualificationEditText.text?.toString().orEmpty(),
                joiningDate = joiningDateEditText.text?.toString().orEmpty(),
            ),
        )
    }
}
