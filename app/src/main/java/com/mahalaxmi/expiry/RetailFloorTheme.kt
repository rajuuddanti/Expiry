package com.mahalaxmi.expiry

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RetailRuby = Color(0xFFC62828)
private val RetailInk = Color(0xFF131B2E)
private val RetailSlate = Color(0xFF515F74)
private val RetailGreen = Color(0xFF027836)
private val RetailCanvas = Color(0xFFFAF8FF)
private val RetailOutline = Color(0xFFE4BEBA)

private val RetailFloorColors = lightColorScheme(
    primary = RetailRuby,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC62828),
    onPrimaryContainer = Color(0xFFFFE0DD),
    secondary = RetailSlate,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E3FD),
    onSecondaryContainer = Color(0xFF3A485C),
    tertiary = RetailGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF027836),
    onTertiaryContainer = Color(0xFF9AFDAB),
    background = RetailCanvas,
    onBackground = RetailInk,
    surface = RetailCanvas,
    onSurface = RetailInk,
    surfaceVariant = Color(0xFFDAE2FD),
    onSurfaceVariant = Color(0xFF5B403D),
    outline = Color(0xFF8F706C),
    outlineVariant = RetailOutline,
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val RetailFloorTypography = Typography(
    headlineLarge = androidx.compose.ui.text.TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, letterSpacing = (-0.52).sp),
    headlineMedium = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    headlineSmall = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
    titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.56.sp),
    labelMedium = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, letterSpacing = 0.72.sp),
    labelSmall = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.8.sp)
)

private val RetailFloorShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MahaMartRetailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RetailFloorColors,
        typography = RetailFloorTypography,
        shapes = RetailFloorShapes,
        content = content
    )
}
