package com.example.diploma.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.diploma.screen.CameraScreen
import com.example.diploma.screen.LoginScreen
import com.example.diploma.screen.SignUpScreen
import com.example.diploma.screen.StartScreen
import com.example.diploma.viewmodel.AuthViewModel
import com.example.diploma.viewmodel.CameraViewModel

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun MyAppNavigation() {
    val navController = rememberNavController()

    // ViewModel для авторизации
    val authViewModel: AuthViewModel = viewModel()

    // Стартовый экран на основе состояния авторизации
    val startDestination = if (authViewModel.isLoggedIn()) {
        Routes.cameraScreen
    } else {
        Routes.startScreen
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.startScreen) {
            StartScreen(navController)
        }

        composable(Routes.signUpScreen) {
            SignUpScreen(navController)
        }

        composable(Routes.loginScreen) {
            LoginScreen(navController)
        }

        composable(Routes.cameraScreen) {
            val cameraViewModel: CameraViewModel = viewModel()
            CameraScreen(
                navController = navController,
                viewModel = cameraViewModel
            )
        }
    }
}