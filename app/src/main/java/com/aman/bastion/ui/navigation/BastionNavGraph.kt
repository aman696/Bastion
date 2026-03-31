package com.aman.bastion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.aman.bastion.ui.appdetail.AppDetailScreen
import com.aman.bastion.ui.hardcorelock.HardcoreLockScreen
import com.aman.bastion.ui.home.HomeScreen
import com.aman.bastion.ui.onboarding.OnboardingScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Onboarding : Screen("onboarding")
    object AppDetail : Screen("app_detail/{packageName}") {
        fun createRoute(pkg: String) = "app_detail/$pkg"
    }
    object HardcoreLock : Screen("hardcore_lock/{packageName}") {
        fun createRoute(pkg: String) = "hardcore_lock/$pkg"
    }
}

@Composable
fun BastionNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onAppTapped = { pkg, isHardcoreActive ->
                    if (isHardcoreActive) {
                        navController.navigate(Screen.HardcoreLock.createRoute(pkg))
                    } else {
                        navController.navigate(Screen.AppDetail.createRoute(pkg))
                    }
                }
            )
        }

        composable(
            route = Screen.AppDetail.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { back ->
            AppDetailScreen(
                packageName = back.arguments!!.getString("packageName")!!,
                onNavigateUp = navController::navigateUp,
                onHardcoreActivated = { pkg ->
                    navController.navigate(Screen.HardcoreLock.createRoute(pkg)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(
            route = Screen.HardcoreLock.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { back ->
            HardcoreLockScreen(
                packageName = back.arguments!!.getString("packageName")!!,
                onNavigateUp = navController::navigateUp
            )
        }
    }
}
