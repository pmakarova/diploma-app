package com.example.diploma.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.diploma.R
import com.example.diploma.navigation.Routes
import com.example.diploma.viewmodel.AuthViewModel


@Composable
fun StartScreen(navController: NavController) {
    val authViewModel: AuthViewModel = viewModel()

    // Если пользователь уже авторизован, то сразу перенаправляется на CameraScreen
    LaunchedEffect(Unit) {
        if (authViewModel.isLoggedIn()) {
            navController.navigate(Routes.cameraScreen) {
                popUpTo(Routes.startScreen) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(0.15f))

        Image(
            painter = painterResource(id = R.drawable.photo),
            contentDescription = "Sign Up Image",
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .aspectRatio(0.8f, matchHeightConstraintsFirst = true)
        )

        Spacer(modifier = Modifier.weight(0.025f))

        // Кнопка регистрации
        Button(
            onClick = {
                navController.navigate(Routes.signUpScreen)
            },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(
                text= "Зарегистрироваться",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.padding(5.dp))

        // Кнопка для входа в аккаунт
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text= "Уже есть аккаунт?",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(
                onClick = {
                    navController.navigate(Routes.loginScreen)
                },
            ) {
                Text(
                    text= "Войти",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))
    }
}