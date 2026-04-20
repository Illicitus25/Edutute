package com.example.edutute.presentation.institution

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
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
import com.example.edutute.core.util.InstitutionSetupUtils
import com.example.edutute.core.util.ValidationUtils
import com.example.edutute.databinding.FragmentInstitutionProfileBinding
import com.example.edutute.domain.model.InstitutionDraft
import com.example.edutute.presentation.main.MainViewModel
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class InstitutionProfileFragment : Fragment() {

    private var bindingRef: FragmentInstitutionProfileBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: InstitutionProfileViewModel by viewModels { appViewModelFactory() }
    private val mainViewModel: MainViewModel by activityViewModels { appViewModelFactory() }
    private var isFormattingPostalCode = false
    private var lastPostalLookup = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentInstitutionProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStaticDropdowns()
        observeState()
        bindActions()
        viewModel.load()
    }

    private fun bindActions() = with(binding) {
        saveButton.setOnClickListener {
            viewModel.save(
                InstitutionDraft(
                    name = institutionNameEditText.text?.toString().orEmpty(),
                    addressLine1 = addressLineOneEditText.text?.toString().orEmpty(),
                    addressLine2 = addressLineTwoEditText.text?.toString().orEmpty(),
                    city = cityEditText.text?.toString().orEmpty(),
                    state = stateAutoCompleteTextView.text?.toString().orEmpty(),
                    postalCode = postalCodeEditText.text?.toString().orEmpty(),
                    contactEmail = contactEmailEditText.text?.toString().orEmpty(),
                    contactPhone = contactPhoneEditText.text?.toString().orEmpty(),
                    currentAcademicSessionName = academicSessionAutoCompleteTextView.text?.toString().orEmpty(),
                ),
            )
        }

        listOf(
            institutionNameEditText,
            contactPhoneEditText,
            contactEmailEditText,
        ).forEach { editText ->
            editText.doAfterTextChanged { saveButton.isEnabled = true }
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

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.loadState.collect { state ->
                        binding.contentGroup.isVisible = state !is UiState.Loading
                        binding.progressBar.isVisible = state is UiState.Loading
                        when (state) {
                            UiState.Idle, UiState.Loading -> Unit
                            is UiState.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            is UiState.Success -> bindData(state.data)
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
                                Snackbar.make(binding.root, R.string.message_institution_saved, Snackbar.LENGTH_LONG).show()
                                mainViewModel.refreshSession()
                                viewModel.clearSaveState()
                                findNavController().navigate(R.id.dashboardFragment)
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
                                binding.stateAutoCompleteTextView.setText(state.data.state, false)
                                binding.postalCodeEditText.setText(state.data.postalCode)
                                viewModel.clearAddressLookupState()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupStaticDropdowns() {
        val stateAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            ValidationUtils.indianStates(),
        )
        binding.stateAutoCompleteTextView.setAdapter(stateAdapter)
    }

    private fun bindData(data: InstitutionProfileData) = with(binding) {
        val institution = data.institution

        val sessionAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            data.availableSessionNames,
        )
        academicSessionAutoCompleteTextView.setAdapter(sessionAdapter)

        val currentUser = mainViewModel.currentSession.value

        institutionCodeInputLayout.isVisible = institution != null
        institutionNameEditText.setText(institution?.name.orEmpty())
        institutionCodeEditText.setText(institution?.id.orEmpty())
        addressLineOneEditText.setText(institution?.addressLine1.orEmpty())
        addressLineTwoEditText.setText(institution?.addressLine2.orEmpty())
        cityEditText.setText(institution?.city.orEmpty())
        stateAutoCompleteTextView.setText(institution?.state.orEmpty(), false)
        lastPostalLookup = institution?.postalCode.orEmpty()
        postalCodeEditText.setText(institution?.postalCode.orEmpty())
        contactEmailEditText.setText(institution?.contactEmail.orEmpty().ifBlank { currentUser?.email.orEmpty() })
        contactPhoneEditText.setText(institution?.contactPhone.orEmpty())
        academicSessionAutoCompleteTextView.setText(
            data.academicSession?.name.orEmpty().ifBlank { InstitutionSetupUtils.currentAcademicSessionLabel() },
            false,
        )
        saveButton.isVisible = !data.isReadOnly
        if (data.isReadOnly) {
            saveProgressBar.isVisible = false
        }
        setTextFieldReadOnly(institutionNameEditText, data.isReadOnly)
        setTextFieldReadOnly(addressLineOneEditText, data.isReadOnly)
        setTextFieldReadOnly(addressLineTwoEditText, data.isReadOnly)
        setTextFieldReadOnly(cityEditText, data.isReadOnly)
        setTextFieldReadOnly(postalCodeEditText, data.isReadOnly)
        setTextFieldReadOnly(contactEmailEditText, data.isReadOnly)
        setTextFieldReadOnly(contactPhoneEditText, data.isReadOnly)
        setDropdownReadOnly(stateAutoCompleteTextView, data.isReadOnly)
        setDropdownReadOnly(academicSessionAutoCompleteTextView, data.isReadOnly)
    }

    private fun setTextFieldReadOnly(
        editText: com.google.android.material.textfield.TextInputEditText,
        isReadOnly: Boolean,
    ) {
        editText.isEnabled = !isReadOnly
        editText.isFocusable = !isReadOnly
        editText.isFocusableInTouchMode = !isReadOnly
        editText.isClickable = !isReadOnly
        editText.isCursorVisible = !isReadOnly
    }

    private fun setDropdownReadOnly(
        autoCompleteTextView: com.google.android.material.textfield.MaterialAutoCompleteTextView,
        isReadOnly: Boolean,
    ) {
        autoCompleteTextView.isEnabled = !isReadOnly
        autoCompleteTextView.isFocusable = !isReadOnly
        autoCompleteTextView.isFocusableInTouchMode = !isReadOnly
        autoCompleteTextView.isClickable = !isReadOnly
        autoCompleteTextView.isCursorVisible = !isReadOnly
        autoCompleteTextView.inputType = if (isReadOnly) InputType.TYPE_NULL else InputType.TYPE_CLASS_TEXT
        autoCompleteTextView.keyListener = if (isReadOnly) null else autoCompleteTextView.keyListener
        autoCompleteTextView.dismissDropDown()
    }
}
