package com.yourname.etbledger.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yourname.etbledger.data.repository.LedgerRepository
import com.yourname.etbledger.ui.dashboard.DashboardScreen
import com.yourname.etbledger.ui.reports.ReportsScreen
import com.yourname.etbledger.ui.review.ReviewScreen
import com.yourname.etbledger.ui.settings.SettingsScreen
import com.yourname.etbledger.ui.transactions.TransactionsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Dashboard : NavDestination("dashboard", "Home", Icons.Default.Home)
    data object Transactions : NavDestination("transactions", "Ledger", Icons.Default.ReceiptLong)
    data object Reports : NavDestination("reports", "Reports", Icons.Default.BarChart)
    data object Review : NavDestination("review", "Review", Icons.Default.Inbox)
    data object Settings : NavDestination("settings", "Settings", Icons.Default.Settings)
}

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {

    val pendingReviewCount: StateFlow<Int> = repository.observePendingUnparsedCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    fun triggerFallbackSync() {
        viewModelScope.launch {
            repository.syncInbox()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EtbLedgerAppShell(
    viewModel: AppShellViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val pendingCount by viewModel.pendingReviewCount.collectAsStateWithLifecycle()

    fun checkSmsPermissions(): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val receiveGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && receiveGranted
    }

    var hasSmsPermissions by remember { mutableStateOf(checkSmsPermissions()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasSmsPermissions = checkSmsPermissions()
        if (hasSmsPermissions) {
            viewModel.triggerFallbackSync()
        }
    }

    fun requestRequiredPermissions() {
        val perms = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasSmsPermissions = checkSmsPermissions()
        if (hasSmsPermissions) {
            viewModel.triggerFallbackSync()
        }
    }

    val destinations = listOf(
        NavDestination.Dashboard,
        NavDestination.Transactions,
        NavDestination.Reports,
        NavDestination.Review,
        NavDestination.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                destinations.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (dest == NavDestination.Review && pendingCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text(pendingCount.coerceAtMost(99).toString()) }
                                    }
                                ) {
                                    Icon(dest.icon, contentDescription = dest.label)
                                }
                            } else {
                                Icon(dest.icon, contentDescription = dest.label)
                            }
                        },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(bottom = innerPadding.calculateBottomPadding())
                .consumeWindowInsets(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = NavDestination.Dashboard.route
            ) {
                composable(NavDestination.Dashboard.route) {
                    DashboardScreen(
                        hasSmsPermissions = hasSmsPermissions,
                        onRequestSmsPermissions = ::requestRequiredPermissions
                    )
                }
                composable(NavDestination.Transactions.route) {
                    TransactionsScreen()
                }
                composable(NavDestination.Reports.route) {
                    ReportsScreen()
                }
                composable(NavDestination.Review.route) {
                    ReviewScreen()
                }
                composable(NavDestination.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
}
