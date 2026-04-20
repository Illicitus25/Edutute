package com.example.edutute.presentation.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.edutute.R
import com.example.edutute.core.ui.UiState
import com.example.edutute.databinding.FragmentSignUpBinding
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SignUpFragment : Fragment() {

    private companion object {
        val userTypeOptions = listOf("Headmaster", "Faculty")
    }

    private var bindingRef: FragmentSignUpBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: SignUpViewModel by viewModels { appViewModelFactory() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentSignUpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUserTypeDropdown()
        bindActions()
        observeState()
    }

    private fun bindActions() = with(binding) {
        createAccountButton.setOnClickListener {
            viewModel.register(
                userType = userTypeAutoCompleteTextView.text?.toString().orEmpty(),
                institutionalId = institutionalIdInputEditText.text?.toString().orEmpty(),
                fullName = fullNameInputEditText.text?.toString().orEmpty(),
                email = emailInputEditText.text?.toString().orEmpty(),
                phoneNumber = phoneInputEditText.text?.toString().orEmpty(),
                qualification = qualificationInputEditText.text?.toString().orEmpty(),
                password = passwordInputEditText.text?.toString().orEmpty(),
                confirmPassword = confirmPasswordInputEditText.text?.toString().orEmpty(),
            )
        }
        signInText.setOnClickListener {
            findNavController().navigateUp()
        }
        listOf(
            userTypeAutoCompleteTextView,
            institutionalIdInputEditText,
            fullNameInputEditText,
            emailInputEditText,
            phoneInputEditText,
            qualificationInputEditText,
            passwordInputEditText,
            confirmPasswordInputEditText,
        ).forEach { editText ->
            editText.doAfterTextChanged {
                when (editText.id) {
                    R.id.userTypeAutoCompleteTextView -> viewModel.clearFieldError(SignUpViewModel.SignUpField.USER_TYPE)
                    R.id.institutionalIdInputEditText -> viewModel.clearFieldError(SignUpViewModel.SignUpField.INSTITUTIONAL_ID)
                    R.id.fullNameInputEditText -> viewModel.clearFieldError(SignUpViewModel.SignUpField.FULL_NAME)
                    R.id.emailInputEditText -> viewModel.clearFieldError(SignUpViewModel.SignUpField.EMAIL)
                    R.id.phoneInputEditText -> viewModel.clearFieldError(SignUpViewModel.SignUpField.PHONE_NUMBER)
                    R.id.qualificationInputEditText -> viewModel.clearFieldError(SignUpViewModel.SignUpField.QUALIFICATION)
                    R.id.passwordInputEditText -> viewModel.clearFieldError(SignUpViewModel.SignUpField.PASSWORD)
                    R.id.confirmPasswordInputEditText -> viewModel.clearFieldError(SignUpViewModel.SignUpField.CONFIRM_PASSWORD)
                }
            }
        }
    }

    private fun setupUserTypeDropdown() = with(binding) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, userTypeOptions)
        userTypeAutoCompleteTextView.setAdapter(adapter)
        userTypeAutoCompleteTextView.setText(userTypeOptions.first(), false)
        updateRoleSpecificFields(userTypeAutoCompleteTextView.text?.toString().orEmpty())
        userTypeAutoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            updateRoleSpecificFields(userTypeOptions[position])
        }
    }

    private fun updateRoleSpecificFields(userType: String) = with(binding) {
        val isFaculty = userType.equals("Faculty", ignoreCase = true)
        institutionalIdInputLayout.visibility = if (isFaculty) View.VISIBLE else View.GONE
        qualificationInputLayout.visibility = if (isFaculty) View.VISIBLE else View.GONE
        facultyInfoText.visibility = if (isFaculty) View.VISIBLE else View.GONE
        if (!isFaculty) {
            institutionalIdInputLayout.error = null
            qualificationInputLayout.error = null
            institutionalIdInputEditText.text = null
            qualificationInputEditText.text = null
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.signUpState.collect { state ->
                        binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                        binding.createAccountButton.isEnabled = state !is UiState.Loading
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.clearState()
                            }
                            is UiState.Success -> {
                                findNavController().previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set("login_notice", "Account created successfully.")
                                findNavController().navigateUp()
                                viewModel.clearState()
                            }
                        }
                    }
                }
                launch {
                    viewModel.fieldErrors.collect { errors ->
                        binding.userTypeInputLayout.error = errors.userType
                        binding.institutionalIdInputLayout.error = errors.institutionalId
                        binding.fullNameInputLayout.error = errors.fullName
                        binding.emailInputLayout.error = errors.email
                        binding.phoneInputLayout.error = errors.phoneNumber
                        binding.qualificationInputLayout.error = errors.qualification
                        binding.passwordInputLayout.error = errors.password
                        binding.confirmPasswordInputLayout.error = errors.confirmPassword
                    }
                }
                launch {
                    viewModel.message.collect { message ->
                        if (message != null) {
                            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                            viewModel.clearMessage()
                        }
                    }
                }
            }
        }
    }
}
