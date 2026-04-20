package com.example.edutute.presentation.faculty

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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
import com.example.edutute.databinding.FragmentFacultyListBinding
import com.example.edutute.domain.model.FacultyMember
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class FacultyListFragment : Fragment() {

    private var bindingRef: FragmentFacultyListBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: FacultyListViewModel by viewModels { appViewModelFactory() }

    private val adapter by lazy {
        EntityListAdapter(
            itemId = FacultyMember::id,
            titleProvider = { it.fullName },
            subtitleProvider = { "${it.qualification.ifBlank { getString(R.string.label_not_set) }} • ${it.email.ifBlank { getString(R.string.label_no_email) }}" },
            metaProvider = { "Code: ${it.employeeCode}" },
            onOpen = { openDetail(it.id) },
            onEdit = { openForm(it.id) },
            onDelete = { confirmDelete(it) },
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentFacultyListBinding.inflate(inflater, container, false)
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
        binding.addFacultyButton.setOnClickListener { openForm(null) }
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
                                Snackbar.make(binding.root, R.string.message_faculty_deleted, Snackbar.LENGTH_LONG).show()
                                viewModel.clearActionState()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openForm(facultyId: String?) {
        val args = Bundle().apply {
            facultyId?.let { putString("facultyId", it) }
        }
        findNavController().navigate(
            R.id.facultyFormFragment,
            args,
        )
    }

    private fun openDetail(facultyId: String) {
        val args = Bundle().apply { putString("facultyId", facultyId) }
        findNavController().navigate(
            R.id.facultyDetailFragment,
            args,
        )
    }

    private fun confirmDelete(faculty: FacultyMember) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.title_delete_faculty)
            .setMessage(getString(R.string.message_delete_faculty_confirmation, faculty.fullName))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteFaculty(faculty.id)
            }
            .show()
    }
}
