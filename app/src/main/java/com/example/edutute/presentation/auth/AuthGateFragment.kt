package com.example.edutute.presentation.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.edutute.R
import com.example.edutute.databinding.FragmentAuthGateBinding
import com.example.edutute.domain.model.SessionState
import com.example.edutute.domain.model.UserRole
import com.example.edutute.presentation.main.MainViewModel
import com.example.edutute.presentation.main.appViewModelFactory
import kotlinx.coroutines.launch

class AuthGateFragment : Fragment() {

    private var bindingRef: FragmentAuthGateBinding? = null
    private val binding get() = bindingRef!!

    private val mainViewModel: MainViewModel by activityViewModels { appViewModelFactory() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        bindingRef = FragmentAuthGateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeSession()
        mainViewModel.refreshSession()
    }

    override fun onDestroyView() {
        bindingRef = null
        super.onDestroyView()
    }

    private fun observeSession() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.sessionState.collect { state ->
                    if (findNavController().currentDestination?.id != R.id.authGateFragment) {
                        return@collect
                    }
                    when (state) {
                        SessionState.Loading -> Unit
                        SessionState.Unauthenticated -> findNavController().navigate(R.id.action_authGateFragment_to_loginFragment)
                        is SessionState.Authenticated -> routeAuthenticatedUser(state)
                        is SessionState.SetupRequired -> findNavController().navigate(R.id.action_authGateFragment_to_institutionProfileFragment)
                        is SessionState.Unauthorized -> {
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            findNavController().navigate(R.id.action_authGateFragment_to_loginFragment)
                        }
                    }
                }
            }
        }
    }

    private fun routeAuthenticatedUser(state: SessionState.Authenticated) {
        if (state.session.userRole == UserRole.FACULTY.name) {
            val facultyId = state.session.linkedFacultyId
            if (facultyId.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Faculty profile is missing for this account.", Toast.LENGTH_LONG).show()
                findNavController().navigate(R.id.action_authGateFragment_to_loginFragment)
                return
            }
            findNavController().navigate(R.id.action_authGateFragment_to_dashboardFragment)
        } else {
            findNavController().navigate(R.id.action_authGateFragment_to_dashboardFragment)
        }
    }
}
