package com.example.edutute.presentation.subjects

import android.app.Dialog
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.edutute.R
import com.example.edutute.core.ui.EntityListAdapter
import com.example.edutute.core.ui.UiState
import com.example.edutute.databinding.DialogSubjectInputBinding
import com.example.edutute.databinding.FragmentSubjectsBinding
import com.example.edutute.domain.model.Subject
import com.example.edutute.domain.model.SubjectDraft
import com.example.edutute.domain.model.SubjectType
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SubjectsFragment : Fragment() {

    private var bindingRef: FragmentSubjectsBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: SubjectsViewModel by viewModels { appViewModelFactory() }

    private val adapter by lazy {
        EntityListAdapter(
            itemId = Subject::id,
            titleProvider = { it.name },
            subtitleProvider = { formatSubjectType(it.subjectType) },
            metaProvider = { "" },
            onOpen = { showSubjectDialog(it) },
            onEdit = { showSubjectDialog(it) },
            onDelete = { confirmDelete(it) },
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentSubjectsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.addSubjectButton.setOnClickListener { showSubjectDialog(null) }
        binding.searchEditText.doAfterTextChanged { viewModel.updateQuery(it?.toString().orEmpty()) }
        observeState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
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
                            is UiState.Success -> {
                                adapter.submitList(state.data)
                                binding.emptyStateText.isVisible = state.data.isEmpty()
                            }
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
                                Snackbar.make(binding.root, R.string.message_subject_saved, Snackbar.LENGTH_LONG).show()
                                viewModel.clearActionState()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showSubjectDialog(item: Subject?) {
        val dialogBinding = DialogSubjectInputBinding.inflate(layoutInflater)
        lateinit var dialog: Dialog
        val subjectTypes = SubjectType.entries.map { subjectType -> formatSubjectType(subjectType.name) }
        dialogBinding.subjectTypeDropdown.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, subjectTypes),
        )
        dialogBinding.subjectTypeDropdown.setText(
            formatSubjectType(item?.subjectType ?: SubjectType.THEORETICAL.name),
            false,
        )

        item?.let {
            dialogBinding.nameEditText.setText(it.name)
            dialogBinding.subjectTypeDropdown.setText(formatSubjectType(it.subjectType), false)
        }

        dialog = createDialogShell(
            title = getString(if (item == null) R.string.title_add_subject else R.string.title_edit_subject),
            contentView = dialogBinding.root,
            positiveLabel = getString(R.string.action_save),
            onPositive = {
                viewModel.saveSubject(
                    SubjectDraft(
                        id = item?.id.orEmpty(),
                        name = dialogBinding.nameEditText.text?.toString().orEmpty(),
                        subjectType = dialogBinding.subjectTypeDropdown.text
                            ?.toString()
                            .orEmpty()
                            .trim()
                            .replace(' ', '_')
                            .uppercase()
                            .takeIf { candidate -> SubjectType.entries.any { it.name == candidate } }
                            ?: SubjectType.THEORETICAL.name,
                    ),
                )
                dialog.dismiss()
            },
        )
        dialog.show()
    }

    private fun confirmDelete(item: Subject) {
        val messageView = android.widget.TextView(requireContext()).apply {
            text = getString(R.string.message_delete_subject_confirmation, item.name)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_xl),
                resources.getDimensionPixelSize(R.dimen.spacing_sm),
            )
        }
        createDialogShell(
            title = getString(R.string.title_delete_subject),
            contentView = messageView,
            positiveLabel = getString(R.string.action_delete),
            onPositive = { viewModel.deleteSubject(item.id) },
        ).show()
    }

    private fun createDialogShell(
        title: String,
        contentView: View,
        positiveLabel: String,
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
        val cancelButton = MaterialButton(
            ContextThemeWrapper(requireContext(), R.style.Widget_Edutute_Button_Outlined),
            null,
            0,
        ).apply {
            text = getString(R.string.action_cancel)
            setOnClickListener { dialog.dismiss() }
        }
        val saveButton = MaterialButton(
            ContextThemeWrapper(requireContext(), R.style.Widget_Edutute_Button_Primary),
            null,
            0,
        ).apply {
            text = positiveLabel
            setOnClickListener { onPositive() }
        }
        actionsRow.addView(
            cancelButton,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            },
        )
        actionsRow.addView(
            saveButton,
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

    private fun formatSubjectType(value: String): String = when (value.uppercase()) {
        SubjectType.THEORETICAL.name -> getString(R.string.label_subject_type_theoretical)
        SubjectType.PRACTICAL.name -> getString(R.string.label_subject_type_practical)
        else -> value.replace('_', ' ')
    }
}
