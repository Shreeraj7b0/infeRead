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

@Composable
fun AppNavigation(
    pendingUri: Uri? = null,
    onUriHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(
                onNavigateToAuth = {
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
            route = "home?checklistId={checklistId}",
            arguments = listOf(navArgument("checklistId") { type = NavType.IntType; defaultValue = -1 })
        ) { backStackEntry ->
            val checklistId = backStackEntry.arguments?.getInt("checklistId") ?: -1
            HomeScreen(
                initialChecklistId = if (checklistId == -1) null else checklistId,
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
            ) }
        ) { backStackEntry ->
            val fileId = backStackEntry.arguments?.getInt("fileId") ?: return@composable
            ReaderScreen(
                fileId = fileId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToChecklist = { checklistId ->
                    navController.navigate("home?checklistId=$checklistId") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
    }
}
