package com.gallery.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gallery.android.R

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_medium, FontWeight.SemiBold),
    Font(R.font.barlow_medium, FontWeight.Bold),
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.Normal),
    Font(R.font.barlow_condensed_semibold, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_semibold, FontWeight.Bold),
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 54.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.5.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.4.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.3.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.3.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.8.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.6.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp,
    ),
)
