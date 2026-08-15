package com.jarvis.assistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Using default sans-serif which maps to Roboto on Android (clean, techy feel)
val JarvisFontFamily = FontFamily.Default

val JarvisTypography = Typography(
    // Large display text - for "JARVIS" title
    displayLarge = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Thin,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = 4.sp,
        color = JarvisTextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 2.sp,
        color = JarvisTextPrimary
    ),
    displaySmall = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 1.sp,
        color = JarvisTextPrimary
    ),
    // Headlines
    headlineLarge = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 1.sp,
        color = JarvisTextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.5.sp,
        color = JarvisTextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = JarvisTextPrimary
    ),
    // Titles
    titleLarge = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.5.sp,
        color = JarvisTextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        color = JarvisTextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = JarvisTextSecondary
    ),
    // Body text
    bodyLarge = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
        color = JarvisTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
        color = JarvisTextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
        color = JarvisTextMuted
    ),
    // Labels
    labelLarge = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.sp,
        color = JarvisTextAccent
    ),
    labelMedium = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = JarvisTextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = JarvisFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = JarvisTextMuted
    )
)
