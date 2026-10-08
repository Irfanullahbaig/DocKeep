package com.dockeep.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dockeep.app.di.AppContainer
import com.dockeep.app.domain.AppSettings
import com.dockeep.app.security.BiometricAuthenticator
import com.dockeep.app.ui.add.AddDocumentScreen
import com.dockeep.app.ui.add.AddDocumentViewModel
import com.dockeep.app.ui.details.DocumentDetailsScreen
import com.dockeep.app.ui.details.DocumentDetailsViewModel
import com.dockeep.app.ui.documents.AllDocumentsScreen
import com.dockeep.app.ui.documents.AllDocumentsViewModel
import com.dockeep.app.ui.home.HomeScreen
import com.dockeep.app.ui.home.HomeViewModel
import com.dockeep.app.ui.lock.LockScreen
import com.dockeep.app.ui.onboarding.PermissionsOnboardingScreen
import com.dockeep.app.ui.settings.SettingsScreen
import com.dockeep.app.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

@Composable
inline fun <reified VM : ViewModel> dockeepViewModel(): VM {
    return viewModel(factory = LocalAppContainer.current.viewModelFactory)
}

object Routes {
    const val HOME = "home"
    const val DOCUMENTS = "documents?categoryId={categoryId}&expiry={expiry}"
    const val SETTINGS = "settings"
    const val ADD = "add?categoryId={categoryId}&documentId={documentId}"
    const val DETAILS = "details/{documentId}"

    fun documents(categoryId: String? = null, expiry: String? = null): String {
        val params = buildList {
            add("categoryId=${categoryId.orEmpty()}")
            add("expiry=${expiry.orEmpty()}")
        }
        return "documents?${params.joinToString("&")}"
    }

    fun add(categoryId: String? = null, documentId: String? = null): String {
        return "add?categoryId=${categoryId.orEmpty()}&documentId=${documentId.orEmpty()}"
    }

    fun details(id: String) = "details/$id"
}

@Composable
fun DocKeepRoot(
    activity: FragmentActivity,
    container: AppContainer,
    settings: AppSettings
) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        val authenticator = remember(activity) { BiometricAuthenticator(activity) }
        var locked by remember { mutableStateOf(settings.appLockEnabled) }
        var authError by remember { mutableStateOf<String?>(null) }
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()

        fun refreshLock() {
            locked = container.appLockController.shouldLock(settings)
        }

        LaunchedEffect(settings.appLockEnabled, settings.autoLockTimeout) {
            refreshLock()
        }

        DisposableEffect(lifecycleOwner, settings) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> container.appLockController.onBackground()
                    Lifecycle.Event.ON_START -> refreshLock()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        Surface(color = MaterialTheme.colorScheme.background) {
            if (locked && settings.appLockEnabled) {
                LockScreen(
                    availability = authenticator.enrollmentStatus(settings.biometricEnabled),
                    errorMessage = authError,
                    onUnlock = {
                        authenticator.authenticate(
                            title = "Unlock DocKeep",
                            subtitle = "Authenticate to access your documents",
                            allowBiometric = settings.biometricEnabled,
                            onSuccess = {
                                container.appLockController.markUnlocked()
                                authError = null
                                locked = false
                            },
                            onError = { authError = it },
                            onFailed = { authError = "Authentication didn’t succeed. Try again." }
                        )
                    },
                    onDisableLock = {
                        scope.launch {
                            container.settingsRepository.setAppLockEnabled(false)
                            container.appLockController.markUnlocked()
                            locked = false
                        }
                    }
                )
            } else if (!settings.permissionsOnboardingCompleted) {
                PermissionsOnboardingScreen(
                    onFinished = {
                        scope.launch {
                            container.settingsRepository.setPermissionsOnboardingCompleted(true)
                        }
                    }
                )
            } else {
                MainShell(container, authenticator)
            }
        }
    }
}

@Composable
private fun MainShell(
    container: AppContainer,
    authenticator: BiometricAuthenticator
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val showBottomBar = route.startsWith("home") ||
        route.startsWith("documents") ||
        route.startsWith("settings") ||
        route.startsWith("details")

    data class Tab(val id: String, val label: String, val icon: ImageVector, val target: String)

    val tabs = listOf(
        Tab("home", "Home", Icons.Rounded.Home, Routes.HOME),
        Tab("documents", "Documents", Icons.Rounded.FolderOpen, Routes.documents()),
        Tab("settings", "Settings", Icons.Rounded.Settings, Routes.SETTINGS)
    )
    val currentTab = when {
        route.startsWith("documents") -> "documents"
        route.startsWith("settings") -> "settings"
        route.startsWith("home") -> "home"
        else -> null
    }
    var lastTab by remember { mutableStateOf("home") }
    LaunchedEffect(currentTab) {
        if (currentTab != null) lastTab = currentTab
    }
    val selected = currentTab ?: lastTab

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = selected == tab.id,
                            onClick = {
                                if (tab.id == "home") {
                                    if (route == Routes.HOME) return@NavigationBarItem
                                    val returnedHome = navController.popBackStack(
                                        route = Routes.HOME,
                                        inclusive = false
                                    )
                                    if (!returnedHome) {
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(navController.graph.findStartDestination().id)
                                            launchSingleTop = true
                                        }
                                    }
                                    return@NavigationBarItem
                                }
                                navController.navigate(tab.target) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() + slideInHorizontally { it / 10 } },
            exitTransition = { fadeOut() + slideOutHorizontally { -it / 12 } }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = dockeepViewModel<HomeViewModel>(),
                    storage = container.fileStorage,
                    onAdd = { navController.navigate(Routes.add()) },
                    onCategory = { navController.navigate(Routes.documents(categoryId = it)) },
                    onDocuments = { navController.navigate(Routes.documents()) },
                    onExpiring = { navController.navigate(Routes.documents(expiry = "soon")) },
                    onDocument = { navController.navigate(Routes.details(it)) }
                )
            }
            composable(
                route = Routes.DOCUMENTS,
                arguments = listOf(
                    navArgument("categoryId") { type = NavType.StringType; nullable = true; defaultValue = "" },
                    navArgument("expiry") { type = NavType.StringType; nullable = true; defaultValue = "" }
                )
            ) {
                AllDocumentsScreen(
                    viewModel = dockeepViewModel<AllDocumentsViewModel>(),
                    storage = container.fileStorage,
                    onDocument = { navController.navigate(Routes.details(it)) },
                    onAdd = { categoryId -> navController.navigate(Routes.add(categoryId = categoryId)) }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = dockeepViewModel<SettingsViewModel>(),
                    authenticator = authenticator
                )
            }
            composable(
                route = Routes.ADD,
                arguments = listOf(
                    navArgument("categoryId") { type = NavType.StringType; nullable = true; defaultValue = "" },
                    navArgument("documentId") { type = NavType.StringType; nullable = true; defaultValue = "" }
                )
            ) {
                AddDocumentScreen(
                    viewModel = dockeepViewModel<AddDocumentViewModel>(),
                    storage = container.fileStorage,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.DETAILS,
                arguments = listOf(navArgument("documentId") { type = NavType.StringType })
            ) {
                DocumentDetailsScreen(
                    viewModel = dockeepViewModel<DocumentDetailsViewModel>(),
                    storage = container.fileStorage,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.add(documentId = it)) }
                )
            }
        }
    }
}
