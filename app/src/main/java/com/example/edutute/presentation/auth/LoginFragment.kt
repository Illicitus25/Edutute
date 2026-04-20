package com.example.edutute.presentation.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.edutute.R
import com.example.edutute.core.ui.UiState
import com.example.edutute.databinding.FragmentLoginBinding
import com.example.edutute.presentation.main.MainViewModel
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var bindingRef: FragmentLoginBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: LoginViewModel by viewModels { appViewModelFactory() }
    private val mainViewModel: MainViewModel by activityViewModels { appViewModelFactory() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindActions()
        observeState()
        observeNavigationNotice()
    }

    private fun bindActions() = with(binding) {
        loginButton.setOnClickListener {
            viewModel.login(
                emailInputEditText.text?.toString().orEmpty(),
                passwordInputEditText.text?.toString().orEmpty(),
            )
        }
        forgotPasswordText.setOnClickListener {
            showForgotPasswordDialog(emailInputEditText.text?.toString().orEmpty())
        }
        createAccountText.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_signUpFragment)
        }
        emailInputEditText.doAfterTextChanged {
            emailInputLayout.error = null
        }
        passwordInputEditText.doAfterTextChanged {
            passwordInputLayout.error = null
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loginState.collect { state ->
                        binding.progressBar.visibility = if (state is UiState.Loading) View.VISIBLE else View.GONE
                        binding.loginButton.isEnabled = state !is UiState.Loading
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.clearLoginState()
                            }
                            is UiState.Success -> {
                                mainViewModel.refreshSession()
                                findNavController().navigate(R.id.action_loginFragment_to_authGateFragment)
                                viewModel.clearLoginState()
                            }
                        }
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

    private fun showForgotPasswordDialog(prefilledEmail: String) {
        val emailInput = EditText(requireContext()).apply {
            setText(prefilledEmail)
            setSelection(text.length)
            hint = getString(R.string.label_email)
            setPadding(48, 40, 48, 24)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.title_forgot_password)
            .setMessage(R.string.forgot_password_message)
            .setView(emailInput)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_send_link) { _, _ ->
                viewModel.sendPasswordReset(emailInput.text?.toString().orEmpty())
            }
            .show()
    }

    private fun observeNavigationNotice() {
        val savedStateHandle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        savedStateHandle.getLiveData<String>("login_notice").observe(viewLifecycleOwner) { notice ->
            if (notice != null) {
                Snackbar.make(binding.root, notice, Snackbar.LENGTH_LONG).show()
                savedStateHandle.remove<String>("login_notice")
            }
        }
    }
}
