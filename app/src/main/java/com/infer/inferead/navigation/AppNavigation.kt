package com.infer.inferead.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.infer.inferead.ui.screens.AuthScreen
import com.infer.inferead.ui.screens.HomeScreen
import com.infer.inferead.ui.screens.ReaderScreen
import com.infer.inferead.ui.screens.SettingsScreen
import com.infer.inferead.ui.screens.SplashScreen

import android.net.Uri

var hasShownSplashScreen = false

@Composable
fun AppNavigation(
    pendingUri: Uri? = null,
    widgetAction: String? = null,
    widgetChecklistId: Int? = null,
    onUriHandled: () -> Unit = {},
    onWidgetActionHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    androidx.compose.runtime.LaunchedEffect(widgetAction, widgetChecklistId) {
        if (widgetAction == "com.infer.inferead.NEW_CHECKLIST") {
            navController.navigate("home?newChecklist=true")
            onWidgetActionHandled()
        } else if (widgetAction == "com.infer.inferead.OPEN_CHECKLIST" && widgetChecklistId != null) {
            navController.navigate("home?checklistId=$widgetChecklistId")
            onWidgetActionHandled()
        }
    }

    val startDestination = if (hasShownSplashScreen) "auth" else "splash"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("splash") {
            SplashScreen(
                onNavigateToAuth = {
                    hasShownSplashScreen = true
                    navController.navigate("auth") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }
        
        composable("auth") {
            AuthScreen(
                onNavigateToHome = {
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "home?checklistId={checklistId}&newChecklist={newChecklist}",
            arguments = listOf(
                navArgument("checklistId") { type = NavType.IntType; defaultValue = -1 },
                navArgument("newChecklist") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val checklistId = backStackEntry.arguments?.getInt("checklistId") ?: -1
            val newChecklist = backStackEntry.arguments?.getBoolean("newChecklist") ?: false
            HomeScreen(
                initialChecklistId = if (checklistId == -1) null else checklistId,
                initialNewChecklist = newChecklist,
                pendingUri = pendingUri,
                onUriHandled = onUriHandled,
                onNavigateToReader = { fileId ->
                    navController.navigate("reader/$fileId")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToStats = {
                    navController.navigate("stats")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToStats = { navController.navigate("stats") },
                onOpenFile = { fileId -> navController.navigate("reader/$fileId") }
            )
        }
        composable("stats") {
            com.infer.inferead.ui.screens.StatsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "reader/{fileId}",
            arguments = listOf(navArgument("fileId") { type = NavType.IntType }),
            deepLinks = listOf(navDeepLink { uriPattern = "inferead://reader/{fileId}" }),
            popExitTransition = { androidx.compose.animation.slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = androidx.compose.animation.core.tween(250)
            ) },
            popEnterTransition = { androidx.compose.animation.EnterTransition.None }
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getInt("fileId") ?: return@composable
            ReaderScreen(
                fileId = fileId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChecklist = { checklistId ->
                    navController.navigate("home?checklistId=$checklistId") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
    }
}
