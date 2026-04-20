package com.example.edutute.presentation.classes

import android.app.Dialog
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.edutute.R
import com.example.edutute.core.ui.EntityListAdapter
import com.example.edutute.core.ui.UiState
import com.example.edutute.databinding.DialogClassInputBinding
import com.example.edutute.databinding.FragmentClassesBinding
import com.example.edutute.domain.model.ClassSection
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ClassesFragment : Fragment() {

    private var bindingRef: FragmentClassesBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: ClassesViewModel by viewModels { appViewModelFactory() }

    private var screenData: ClassesScreenData = ClassesScreenData()

    private val classSectionAdapter by lazy {
        EntityListAdapter(
            itemId = ClassSection::id,
            titleProvider = { it.displayName },
            subtitleProvider = { buildString { append("Class teacher: "); append(it.classTeacherName.ifBlank { getString(R.string.label_not_assigned) }) } },
            metaProvider = {
                val studentCount = screenData.students.count { student -> student.currentClassSectionId == it.id }
                getString(R.string.label_class_summary_meta, studentCount)
            },
            showActionButtons = false,
            onOpen = { openClassDetails(it.id) },
            onEdit = { openClassDetails(it.id) },
            onDelete = { openClassDetails(it.id) },
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentClassesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.classSectionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.classSectionsRecyclerView.adapter = classSectionAdapter
        binding.addClassButton.setOnClickListener { showCreateClassDialog() }
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
                                Snackbar.make(binding.root, R.string.message_classes_saved, Snackbar.LENGTH_LONG).show()
                                viewModel.clearActionState()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun render(data: ClassesScreenData) = with(binding) {
        screenData = data
        classSectionAdapter.submitList(data.classSections)
        addClassButton.isVisible = !data.isFacultyUser
        emptyClassSectionText.isVisible = data.classSections.isEmpty()
        emptyClassSectionText.text = if (data.isFacultyUser) {
            "No classes are assigned to you right now."
        } else {
            getString(R.string.empty_class_sections)
        }
    }

    private fun openClassDetails(classSectionId: String) {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.classesFragment) return
        val args = Bundle().apply { putString("classSectionId", classSectionId) }
        navController.navigate(R.id.classDetailsFragment, args)
    }

    private fun showCreateClassDialog() {
        val dialogBinding = DialogClassInputBinding.inflate(layoutInflater)
        val dialog = Dialog(requireContext()).apply {
            setTitle(R.string.title_add_class)
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                dialogBinding.root,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        container.addView(createDialogActions(dialog) {
            viewModel.saveClassWithSection(
                className = dialogBinding.nameEditText.text?.toString().orEmpty(),
                sectionName = dialogBinding.sectionEditText.text?.toString().orEmpty(),
            )
            dialog.dismiss()
        })
        dialog.setContentView(container)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        dialog.show()
    }
    private fun createDialogActions(
        dialog: Dialog,
        positiveLabel: String = getString(R.string.action_save),
        onPositive: () -> Unit,
    ): LinearLayout = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(
            resources.getDimensionPixelSize(R.dimen.spacing_xl),
            0,
            resources.getDimensionPixelSize(R.dimen.spacing_xl),
            resources.getDimensionPixelSize(R.dimen.spacing_xl),
        )
        addView(
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
        addView(
            MaterialButton(
                ContextThemeWrapper(requireContext(), R.style.Widget_Edutute_Button_Primary),
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
    }
}
