package com.gxdevs.screenx.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.gxdevs.screenx.R

val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

private val defaultTypography = Typography()

val Typography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = InterFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = InterFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = InterFontFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = InterFontFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = InterFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = InterFontFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = InterFontFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = InterFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = InterFontFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = InterFontFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = InterFontFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = InterFontFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = InterFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = InterFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = InterFontFamily)
)
