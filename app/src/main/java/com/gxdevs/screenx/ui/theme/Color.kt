package com.gxdevs.screenx.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// LIGHT SCHEME — "Salt and Pepper" Monochrome Minimal (Black, White & Shades)
// - Pure White (#FFFFFF) & Pure Black (#0E0F12)
// - Gradient of Greys: Light Silver (#EAECEF), Slate (#5E636E), Charcoal (#26282D)
// - Canvas: Clean light mist off-white (#F4F5F7)
// ============================================================================
val PrimaryLight = Color(0xFF0E0F12)              // Pure Jet Black (High-contrast hero buttons & titles)
val OnPrimaryLight = Color(0xFFFFFFFF)            // Pure White text on black
val PrimaryContainerLight = Color(0xFFEAECEF)     // Crisp Silver-Grey container wash
val OnPrimaryContainerLight = Color(0xFF0E0F12)

val SecondaryLight = Color(0xFF26282D)            // Dark Charcoal (Secondary actions & active icons)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFECEEF2)   // Soft Slate-Grey Container
val OnSecondaryContainerLight = Color(0xFF16181C)

val TertiaryLight = Color(0xFF4A4E57)             // Medium Slate Grey (Subtle accents)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFE3E5E9)     // Soft Grey Wash
val OnTertiaryContainerLight = Color(0xFF16181C)

val BackgroundLight = Color(0xFFF4F5F7)           // Minimalist Light Mist Canvas
val OnBackgroundLight = Color(0xFF0E0F12)         // Deep Jet Black Ink
val SurfaceLight = Color(0xFFFFFFFF)              // Pure Crisp White Cards
val OnSurfaceLight = Color(0xFF0E0F12)
val SurfaceVariantLight = Color(0xFFEAECEF)       // Silver-Grey container for chips & sliders
val OnSurfaceVariantLight = Color(0xFF5E636E)     // Accessible Slate-Grey secondary text (WCAG AAA)

val OutlineLight = Color(0xFF8E929B)              // Medium Slate outline
val OutlineVariantLight = Color(0xFFE0E2E7)       // Minimalist Hairline Card Border

val ErrorLight = Color(0xFFE53935)                // Vibrant Coral Red (Live recording active)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFDECEB)
val OnErrorContainerLight = Color(0xFF6B1410)

// ============================================================================
// DARK SCHEME — "Salt and Pepper" Monochrome Luxury (Lighter Elevated Cards)
// - Background: Pure Deep Pitch Obsidian Charcoal (#0C0D0F)
// - Elevated Cards: Noticeably lighter refined Charcoal (#1E2025)
// - Containers: Distinct Charcoal Slate (#2A2D34)
// - Text: Crisp White (#FFFFFF) & High-Contrast Slate (#A6ABB5)
// ============================================================================
val PrimaryDark = Color(0xFFFFFFFF)               // Pure White "Salt" (High-contrast hero pop)
val OnPrimaryDark = Color(0xFF0C0D0F)             // Pitch Black "Pepper" on white
val PrimaryContainerDark = Color(0xFF2A2D34)      // Elevated Slate Container
val OnPrimaryContainerDark = Color(0xFFFFFFFF)

val SecondaryDark = Color(0xFFD4D6D9)             // Light Silver Grey (Swatch 2)
val OnSecondaryDark = Color(0xFF0C0D0F)
val SecondaryContainerDark = Color(0xFF2A2D34)
val OnSecondaryContainerDark = Color(0xFFE5E7EB)

val TertiaryDark = Color(0xFFA6ABB5)              // High-contrast Silver-Slate (Swatch 3)
val OnTertiaryDark = Color(0xFF0C0D0F)
val TertiaryContainerDark = Color(0xFF25272D)
val OnTertiaryContainerDark = Color(0xFFD4D6D9)

val BackgroundDark = Color(0xFF0C0D0F)            // Pure Deep Pitch Black Obsidian
val OnBackgroundDark = Color(0xFFFFFFFF)          // Pure Crisp White Text
val SurfaceDark = Color(0xFF1E2025)               // Distinctly Lighter Elevated Charcoal Cards
val OnSurfaceDark = Color(0xFFFFFFFF)
val SurfaceVariantDark = Color(0xFF2A2D34)        // Elevated chip & slider container
val OnSurfaceVariantDark = Color(0xFFA6ABB5)      // High-Contrast Slate Grey subtitles (WCAG AAA)

val OutlineDark = Color(0xFF555964)               // Charcoal Slate outline
val OutlineVariantDark = Color(0xFF333740)        // Clean visible hairline card border

val ErrorDark = Color(0xFFFF5252)                 // High-contrast Coral Red (Live recording active)
val OnErrorDark = Color(0xFF220504)
val ErrorContainerDark = Color(0xFF451412)
val OnErrorContainerDark = Color(0xFFFFDAD6)

// ============================================================================
// SEMANTIC ACCENTS (Status Badges, Paired State, Audio Indicators)
// High-contrast monochrome badges in both light & dark
// ============================================================================
val EmeraldAccent = Color(0xFF0E0F12)             // Jet Black Badge (Light)
val EmeraldAccentDark = Color(0xFFFFFFFF)         // Pure White Badge (Dark)
val EmeraldContainerLight = Color(0xFFEAECEF)     // Silver-Grey Badge Wash
val EmeraldContainerDark = Color(0xFF2A2D34)      // Charcoal Badge Wash
