package com.example.edutute.presentation.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.edutute.R
import com.example.edutute.core.ui.UiState
import com.example.edutute.databinding.FragmentDashboardBinding
import com.example.edutute.presentation.main.appViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var bindingRef: FragmentDashboardBinding? = null
    private val binding get() = bindingRef!!

    private val viewModel: DashboardViewModel by viewModels { appViewModelFactory() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentDashboardBinding.inflate(inflater, container, false)
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
        viewModel.load()
    }

    private fun bindActions() = with(binding) {
        facultyActionButton.setOnClickListener { findNavController().navigate(R.id.facultyListFragment) }
        studentsActionButton.setOnClickListener { findNavController().navigate(R.id.studentsListFragment) }
        classesActionButton.setOnClickListener { findNavController().navigate(R.id.classesFragment) }
        subjectsActionButton.setOnClickListener { findNavController().navigate(R.id.subjectsFragment) }
        institutionProfileActionButton.setOnClickListener { findNavController().navigate(R.id.institutionProfileFragment) }
        attendanceActionButton.setOnClickListener { findNavController().navigate(R.id.attendanceFragment) }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.progressBar.isVisible = state is UiState.Loading
                    when (state) {
                        UiState.Idle, UiState.Loading -> Unit
                        is UiState.Error -> Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                        is UiState.Success -> render(state.data)
                    }
                }
            }
        }
    }

    private fun render(data: DashboardScreenData) = with(binding) {
        val cards = data.cards + List((4 - data.cards.size).coerceAtLeast(0)) {
            DashboardMetricCard(label = "", value = "")
        }
        heroBadgeText.text = data.heroBadge
        heroTitleText.text = data.heroTitle
        heroSubtitleText.text = data.heroSubtitle

        studentsLabelText.text = cards[0].label
        studentsCountText.text = cards[0].value
        facultyLabelText.text = cards[1].label
        facultyCountText.text = cards[1].value
        classSectionsLabelText.text = cards[2].label
        classSectionsCountText.text = cards[2].value
        subjectsLabelText.text = cards[3].label
        subjectsCountText.text = cards[3].value

        facultyActionButton.isVisible = data.showFacultyAction
        studentsActionButton.isVisible = data.showStudentsAction
        classesActionButton.isVisible = data.showClassesAction
        subjectsActionButton.isVisible = data.showSubjectsAction
        institutionProfileActionButton.isVisible = data.showInstitutionAction
        attendanceActionButton.isVisible = data.showAttendanceAction

        setupSummaryTitleText.text = data.summaryTitle
        setupSummaryBodyText.text = data.summaryBody
    }
}
