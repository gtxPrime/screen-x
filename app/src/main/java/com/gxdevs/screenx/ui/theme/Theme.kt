package com.gxdevs.screenx.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = Color(0xFF0C0D0F),
    surfaceContainerLow = Color(0xFF15161A),
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = Color(0xFF26292F),
    surfaceContainerHighest = Color(0xFF30333B),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F9FB),
    surfaceContainer = Color(0xFFF5F6F8),
    surfaceContainerHigh = Color(0xFFEBECEF),
    surfaceContainerHighest = Color(0xFFE1E3E7),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

@Composable
fun ColorScheme.switchableAnimated(): ColorScheme {
    val animSpec = tween<Color>(durationMillis = 300)
    return this.copy(
        primary = animateColorAsState(primary, animSpec, label = "primary").value,
        onPrimary = animateColorAsState(onPrimary, animSpec, label = "onPrimary").value,
        primaryContainer = animateColorAsState(primaryContainer, animSpec, label = "primaryContainer").value,
        onPrimaryContainer = animateColorAsState(onPrimaryContainer, animSpec, label = "onPrimaryContainer").value,
        secondary = animateColorAsState(secondary, animSpec, label = "secondary").value,
        onSecondary = animateColorAsState(onSecondary, animSpec, label = "onSecondary").value,
        secondaryContainer = animateColorAsState(secondaryContainer, animSpec, label = "secondaryContainer").value,
        onSecondaryContainer = animateColorAsState(onSecondaryContainer, animSpec, label = "onSecondaryContainer").value,
        background = animateColorAsState(background, animSpec, label = "background").value,
        onBackground = animateColorAsState(onBackground, animSpec, label = "onBackground").value,
        surface = animateColorAsState(surface, animSpec, label = "surface").value,
        onSurface = animateColorAsState(onSurface, animSpec, label = "onSurface").value,
        surfaceVariant = animateColorAsState(surfaceVariant, animSpec, label = "surfaceVariant").value,
        onSurfaceVariant = animateColorAsState(onSurfaceVariant, animSpec, label = "onSurfaceVariant").value,
        outline = animateColorAsState(outline, animSpec, label = "outline").value,
        outlineVariant = animateColorAsState(outlineVariant, animSpec, label = "outlineVariant").value,
        error = animateColorAsState(error, animSpec, label = "error").value,
        onError = animateColorAsState(onError, animSpec, label = "onError").value
    )
}

@Composable
fun ScreenXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val rawColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isSystemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }
    val colorScheme = rawColorScheme.switchableAnimated()
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        @Suppress("DEPRECATION")
        DisposableEffect(colorScheme.background, isSystemDark, dynamicColor, darkTheme) {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            
            val useDarkIcons = if (dynamicColor) {
                !isSystemDark
            } else {
                !darkTheme
            }
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = useDarkIcons
            insetsController.isAppearanceLightNavigationBars = useDarkIcons
            onDispose {}
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            CompositionLocalProvider(
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = InterFontFamily)
            ) {
                content()
            }
        }
    )
}
