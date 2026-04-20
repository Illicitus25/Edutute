package com.example.edutute.presentation.main

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.edutute.R
import com.example.edutute.core.ui.ThemePreferenceManager
import com.example.edutute.databinding.ActivityMainBinding
import com.example.edutute.domain.model.AppSession
import com.example.edutute.domain.model.UserRole
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfiguration: AppBarConfiguration
    private var currentMenuResId: Int? = null

    private val viewModel: MainViewModel by viewModels { appViewModelFactory() }
    private val themePreferenceManager by lazy { ThemePreferenceManager(this) }

    private val topLevelDestinations = setOf(
        R.id.dashboardFragment,
        R.id.facultyListFragment,
        R.id.studentsListFragment,
        R.id.classesFragment,
        R.id.subjectsFragment,
        R.id.attendanceFragment,
        R.id.institutionProfileFragment,
        R.id.facultyDetailFragment,
    )

    private val authDestinations = setOf(
        R.id.authGateFragment,
        R.id.loginFragment,
        R.id.signUpFragment,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        navController = resolveNavController()
        appBarConfiguration = AppBarConfiguration(topLevelDestinations, binding.drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navigationView.setNavigationItemSelectedListener(::handleDrawerItemSelection)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isAuthDestination = destination.id in authDestinations
            binding.toolbar.isVisible = !isAuthDestination
            binding.navigationView.isVisible = !isAuthDestination
            binding.drawerLayout.setDrawerLockMode(
                if (isAuthDestination) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED,
            )
            if (isAuthDestination) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        observeSessionHeader()
        viewModel.refreshSession()
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    private fun observeSessionHeader() {
        val headerView = binding.navigationView.getHeaderView(0)
        val headerTitle = headerView.findViewById<android.widget.TextView>(R.id.headerTitle)
        val headerSubtitle = headerView.findViewById<android.widget.TextView>(R.id.headerSubtitle)
        val headerBadge = headerView.findViewById<android.widget.TextView>(R.id.headerBadgeText)
        val themeLabel = headerView.findViewById<android.widget.TextView>(R.id.themeModeText)
        val themeIcon = headerView.findViewById<android.widget.ImageView>(R.id.themeIcon)
        val themeSwitch = headerView.findViewById<android.widget.CompoundButton>(R.id.themeSwitch)

        bindThemeToggle(themeSwitch, themeLabel, themeIcon)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentSession.collect { session ->
                    updateHeader(session, headerTitle, headerSubtitle, headerBadge)
                }
            }
        }
    }

    private fun updateHeader(
        session: AppSession?,
        titleView: android.widget.TextView,
        subtitleView: android.widget.TextView,
        badgeView: android.widget.TextView,
    ) {
        if (session == null) {
            titleView.text = getString(R.string.app_name)
            subtitleView.text = getString(R.string.header_signed_out)
            badgeView.text = getString(R.string.header_workspace_badge)
            applyMenuForRole(null)
            return
        }
        val isFaculty = session.userRole == UserRole.FACULTY.name
        titleView.text = if (isFaculty) {
            session.displayName.ifBlank { getString(R.string.app_name) }
        } else {
            session.institutionName.ifBlank { getString(R.string.app_name) }
        }
        subtitleView.text = if (isFaculty) {
            session.institutionName.ifBlank { session.email }
        } else {
            session.email
        }
        badgeView.text = getString(
            if (isFaculty) R.string.header_workspace_badge_faculty else R.string.header_workspace_badge,
        )
        applyMenuForRole(session.userRole)
    }

    private fun bindThemeToggle(
        themeSwitch: android.widget.CompoundButton,
        themeLabel: android.widget.TextView,
        themeIcon: android.widget.ImageView,
    ) {
        val darkModeEnabled = themePreferenceManager.isDarkModeEnabled()
        themeSwitch.setOnCheckedChangeListener(null)
        themeSwitch.isChecked = darkModeEnabled
        updateThemeHeader(themeLabel, themeIcon, darkModeEnabled)
        themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked != themePreferenceManager.isDarkModeEnabled()) {
                themePreferenceManager.setDarkModeEnabled(isChecked)
            }
        }
    }

    private fun updateThemeHeader(
        themeLabel: android.widget.TextView,
        themeIcon: android.widget.ImageView,
        darkModeEnabled: Boolean,
    ) {
        themeLabel.text = getString(if (darkModeEnabled) R.string.label_dark_mode else R.string.label_light_mode)
        themeIcon.setImageResource(if (darkModeEnabled) R.drawable.ic_theme_moon else R.drawable.ic_theme_sun)
    }

    private fun handleDrawerItemSelection(menuItem: MenuItem): Boolean {
        return if (menuItem.itemId == R.id.menu_logout) {
            viewModel.logout()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            navController.navigate(R.id.authGateFragment)
            true
        } else {
            val handled = androidx.navigation.ui.NavigationUI.onNavDestinationSelected(menuItem, navController)
            if (handled) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
            handled
        }
    }

    private fun applyMenuForRole(userRole: String?) {
        val menuResId = if (userRole == UserRole.FACULTY.name) {
            R.menu.menu_faculty_drawer
        } else {
            R.menu.menu_admin_drawer
        }
        if (currentMenuResId != menuResId) {
            binding.navigationView.menu.clear()
            binding.navigationView.inflateMenu(menuResId)
            currentMenuResId = menuResId
        }
    }

    private fun resolveNavController(): NavController {
        supportFragmentManager.executePendingTransactions()

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as? NavHostFragment
            ?: NavHostFragment.create(R.navigation.nav_graph).also { created ->
                supportFragmentManager.beginTransaction()
                    .replace(R.id.navHostFragment, created)
                    .setPrimaryNavigationFragment(created)
                    .commitNow()
            }

        return navHostFragment.navController
    }
}
