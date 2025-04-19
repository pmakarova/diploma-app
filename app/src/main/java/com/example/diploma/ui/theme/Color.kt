package com.example.diploma.ui.theme

import android.hardware.lights.Light
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Colors for light scheme
val LightPrimary = Color(0xFF352C47) // DarkPurple buttons
val LightOnPrimary = Color(0xFFFEF7FF) // White text
val LightSecondary = Color(0xFF895EDB) // Purple
val LightTertiary = Color(0xFF3F3D56)
val LightBackground = LightOnPrimary // White background
val LightOnBackground = Color.Black // Black text on background

// Colors for dark scheme
val DarkPrimary = LightSecondary
val DarkOnPrimary = Color(0xFF1E1E1E)
val DarkSecondary = DarkPrimary
val DarkTertiary = LightTertiary
val DarkBackground = DarkOnPrimary
val DarkOnBackground = LightOnPrimary

