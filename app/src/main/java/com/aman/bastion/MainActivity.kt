package com.aman.bastion

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.aman.bastion.ui.navigation.BastionNavGraph
import com.aman.bastion.ui.navigation.Screen
import com.aman.bastion.ui.theme.BastionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboardingDone = getSharedPreferences("bastion_prefs", Context.MODE_PRIVATE)
            .getBoolean("onboarding_done", false)
        setContent {
            BastionTheme {
                val navController = rememberNavController()
                BastionNavGraph(
                    navController    = navController,
                    startDestination = if (onboardingDone) Screen.Home.route
                                       else Screen.Onboarding.route
                )
            }
        }
    }
}
