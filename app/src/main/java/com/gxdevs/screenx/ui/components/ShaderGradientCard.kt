package com.gxdevs.screenx.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.Lucide
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ============================================================================
// AGSL SHADER SOURCE (Android 13+ / API 33+)
// Normalized Metallic Shader (Guaranteed NO Blowout/Washout to White):
// - Dark Theme (Night): Rich Deep Gunmetal Grey (#24262E / #383B46)
// - Light Theme (Day): High-Contrast Mostly Black with Dark Grey Metal (#0B0C0E / #24252B)
// - Real Brushed Metal Micro-Grain Texture & Subtle Anisotropic Sheen
// - Live Recording: Molten Metallic Crimson & Solar Amber
// ============================================================================
private val AGSL_GRADIENT_SHADER = """
    uniform float2 uResolution;
    uniform float uTime;
    uniform float uRecording;      // 0.0 (idle) or 1.0 (recording)
    uniform float uRippleProgress; // 0.0 to 1.8 (expanding shockwave)
    uniform float2 uRippleOrigin;  // (x, y) normalized tap coordinates (0..1)
    uniform float uTargetState;    // 1.0 = rippling to recording, 0.0 = rippling to idle
    uniform float uIsDarkTheme;    // 1.0 = Dark Mode, 0.0 = Light Mode

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / uResolution;
        float aspect = uResolution.x / uResolution.y;
        
        // Relative coordinates from the ripple origin
        float2 normOrigin = (uv - uRippleOrigin) * float2(aspect, 1.0);
        float dist = length(normOrigin);
        
        // Shockwave ripple parameters
        float radius = uRippleProgress;
        float waveWidth = 0.18;
        float ring = smoothstep(radius - waveWidth, radius, dist) * (1.0 - smoothstep(radius, radius + waveWidth, dist));
        float fade = clamp(1.0 - radius * 0.50, 0.0, 1.0);
        float shockwave = ring * fade;
        
        // Refraction displacement along shockwave wavefront
        float2 rippleDisplace = (dist > 0.001) ? (normOrigin / dist) * shockwave * 0.09 : float2(0.0);
        
        // Fluid organic domain warping for metallic grain flow
        float2 p = (uv - 0.5) * float2(aspect, 1.0);
        float t = uTime * 0.38;
        float wave1 = sin(p.x * 2.8 + t * 0.9) + cos(p.y * 2.5 - t * 0.8);
        float wave2 = cos(p.y * 3.0 + t * 0.7) - sin(p.x * 2.2 - t * 1.1);
        float2 warped = p + float2(wave1, wave2) * 0.18 + rippleDisplace;
        
        // Moving chromatic centers
        float2 c0 = float2(0.14 * sin(t * 0.5), 0.14 * cos(t * 0.6));
        float2 c1 = float2(0.28 * sin(t * 0.7), 0.25 * cos(t * 0.6));
        float2 c2 = float2(0.30 * cos(t * 0.5 + 1.8), 0.26 * sin(t * 0.8 + 1.2));
        float2 c3 = float2(0.28 * sin(t * 0.6 + 3.2), -0.25 * cos(t * 0.4 + 2.1));
        float2 c4 = float2(-0.32 * cos(t * 0.8 + 0.5), 0.22 * sin(t * 0.7 + 4.0));
        
        // Soft exponential falloff for metallic gradients
        float d0 = exp(-length(warped - c0) * 1.5);
        float d1 = exp(-length(warped - c1) * 1.4);
        float d2 = exp(-length(warped - c2) * 1.3);
        float d3 = exp(-length(warped - c3) * 1.5);
        float d4 = exp(-length(warped - c4) * 1.4);
        
        // ====================================================================
        // PALETTES:
        // 1. Dark Mode (Night): Authentic Gunmetal Grey (Hex #24262E to #3C404B)
        // 2. Light Mode (Day): Mostly Black with Dark Grey Metal (Hex #0B0C0E to #26272E)
        // ====================================================================
        // Dark Mode: True Gunmetal Grey
        half3 darkIdleBase   = half3(0.14, 0.15, 0.18); // Gunmetal Base (#24262E)
        half3 darkIdleCol1   = half3(0.22, 0.23, 0.27); // Mid Gunmetal Steel (#383B45)
        half3 darkIdleCol2   = half3(0.17, 0.18, 0.21); // Gunmetal Slate (#2B2E36)
        half3 darkIdleCol3   = half3(0.28, 0.30, 0.35); // Brushed Titanium Sheen (#474D59)
        half3 darkIdleCol4   = half3(0.11, 0.12, 0.14); // Deep Gunmetal Shadow (#1C1E24)
        
        // Light Mode: Mostly Black with Dark Grey Metal
        half3 lightIdleBase  = half3(0.04, 0.04, 0.05); // Jet Black Base (#0B0C0E)
        half3 lightIdleCol1  = half3(0.15, 0.15, 0.18); // Dark Grey Metal (#26262E)
        half3 lightIdleCol2  = half3(0.08, 0.08, 0.09); // Charcoal Metal (#141417)
        half3 lightIdleCol3  = half3(0.22, 0.22, 0.26); // Subtle Steel Highlight (#383842)
        half3 lightIdleCol4  = half3(0.05, 0.05, 0.06); // Obsidian Shadow (#0D0D10)
        
        // Blend Idle Palette based on uIsDarkTheme
        half3 idleBase   = mix(lightIdleBase, darkIdleBase, uIsDarkTheme);
        half3 idleColor1 = mix(lightIdleCol1, darkIdleCol1, uIsDarkTheme);
        half3 idleColor2 = mix(lightIdleCol2, darkIdleCol2, uIsDarkTheme);
        half3 idleColor3 = mix(lightIdleCol3, darkIdleCol3, uIsDarkTheme);
        half3 idleColor4 = mix(lightIdleCol4, darkIdleCol4, uIsDarkTheme);
        
        // Recording Palette (High-voltage molten ruby & solar flare embers)
        half3 recBase     = half3(0.26, 0.03, 0.06); // Molten garnet wash
        half3 recColor1   = half3(0.96, 0.12, 0.25); // Neon scarlet (#F51F40)
        half3 recColor2   = half3(1.00, 0.42, 0.10); // Solar amber (#FF6B1A)
        half3 recColor3   = half3(0.82, 0.08, 0.24); // Molten ruby (#D1143D)
        half3 recColor4   = half3(0.60, 0.04, 0.14); // Deep carmine (#990A24)
        
        // Dynamic ripple state interpolation:
        float pixelState;
        if (uRippleProgress > 0.001) {
            float inside = smoothstep(radius + 0.05, radius - 0.05, dist);
            float previousState = 1.0 - uTargetState;
            pixelState = mix(previousState, uTargetState, inside);
        } else {
            pixelState = uRecording;
        }
        
        half3 baseColor = mix(idleBase, recBase, pixelState);
        half3 col1      = mix(idleColor1, recColor1, pixelState);
        half3 col2      = mix(idleColor2, recColor2, pixelState);
        half3 col3      = mix(idleColor3, recColor3, pixelState);
        half3 col4      = mix(idleColor4, recColor4, pixelState);
        
        // Harmonic wave fields
        float waveA = sin(p.x * 2.2 + p.y * 1.8 + t * 0.7) * 0.5 + 0.5;
        float waveB = cos(p.y * 2.0 - p.x * 1.6 - t * 0.6) * 0.5 + 0.5;
        
        // NORMALIZED WEIGHTED BLEND: Prevents any white blowout or over-accumulation
        half3 color = mix(baseColor, col1, waveA * 0.42);
        color = mix(color, col2, waveB * 0.35);
        color = mix(color, col3, (d2 * 0.4 + d3 * 0.6) * 0.32);
        color = mix(color, col4, d4 * 0.28);
        
        // ====================================================================
        // REAL BRUSHED METAL MICRO-GRAIN TEXTURE
        // ====================================================================
        float grain1 = fract(sin(dot(fragCoord.xy * float2(0.12, 2.5), float2(12.9898, 78.233))) * 43758.5453);
        float grain2 = fract(sin(dot(fragCoord.xy * float2(1.5, 0.18), float2(93.9898, 67.345))) * 24634.6345);
        float brushedTexture = (grain1 * 0.65 + grain2 * 0.35 - 0.5) * 0.035;
        color += half3(brushedTexture);
        
        // ====================================================================
        // SHINE TEXTURE & CONTROLLED SPECULAR LIGHT SWEEPS
        // ====================================================================
        float sweepAngle = p.x * 1.8 + p.y * 1.2;
        float lightBeam = sin(sweepAngle * 2.5 - t * 0.85 + wave1 * 0.4);
        float metallicSweep = pow(clamp(lightBeam * 0.5 + 0.5, 0.0, 1.0), 8.0);
        
        float crestWave = sin(warped.x * 3.4 - warped.y * 2.6 + t * 0.6);
        float chromeGlint = pow(clamp(crestWave * 0.5 + 0.5, 0.0, 1.0), 10.0);
        
        // Controlled metallic shine (peaks at +0.14 max, never washing out to white!)
        half3 shineColor = mix(half3(0.55, 0.58, 0.65), half3(1.0, 0.85, 0.50), pixelState);
        color += shineColor * (metallicSweep * 0.14 + chromeGlint * 0.12);
        
        // Glowing shockwave crest along ripple edge
        half3 crestGlowIdle = half3(0.80, 0.82, 0.88);
        half3 crestGlowRec  = half3(1.0, 0.80, 0.40);
        half3 crestGlow = mix(crestGlowIdle, crestGlowRec, uTargetState);
        color += crestGlow * shockwave * 0.85;
        
        // Subtle edge vignette
        float vignette = 1.0 - 0.08 * length(uv - float2(0.5, 0.7));
        color *= vignette;
        
        return half4(clamp(color, 0.0, 1.0), 1.0);
    }
""".trimIndent()

/**
 * Android 13+ (API 33+) RuntimeShader helper
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslGradientHelper {
    val shader = android.graphics.RuntimeShader(AGSL_GRADIENT_SHADER)

    fun update(
        width: Float,
        height: Float,
        time: Float,
        recording: Float,
        rippleProgress: Float,
        rippleOriginX: Float,
        rippleOriginY: Float,
        targetState: Float,
        isDarkTheme: Float
    ) {
        shader.setFloatUniform("uResolution", width, height)
        shader.setFloatUniform("uTime", time)
        shader.setFloatUniform("uRecording", recording)
        shader.setFloatUniform("uRippleProgress", rippleProgress)
        shader.setFloatUniform("uRippleOrigin", rippleOriginX, rippleOriginY)
        shader.setFloatUniform("uTargetState", targetState)
        shader.setFloatUniform("uIsDarkTheme", isDarkTheme)
    }
}

/**
 * Canvas-based Fallback for Android 8.0 - 12L (API < 33)
 */
private fun DrawScope.drawMeshGradientFallback(
    time: Float,
    recording: Float,
    rippleProgress: Float,
    rippleOrigin: Offset,
    targetState: Float,
    isDarkTheme: Boolean
) {
    val w = size.width
    val h = size.height

    val effectiveState = if (rippleProgress > 0.001f) {
        val maxDist = kotlin.math.max(w, h) * 1.5f
        val currentRadius = rippleProgress * maxDist
        val centerDist = sqrt(
            (w * 0.5f - rippleOrigin.x * w) * (w * 0.5f - rippleOrigin.x * w) +
            (h * 0.5f - rippleOrigin.y * h) * (h * 0.5f - rippleOrigin.y * h)
        )
        if (currentRadius > centerDist) targetState else (1f - targetState)
    } else {
        recording
    }

    val t = time * 0.38f
    // Controlled gunmetal grey & mostly black colors
    val darkCol1 = Color(0xFF383B45) // Gunmetal steel
    val darkCol2 = Color(0xFF2B2E36) // Gunmetal slate
    val darkCol3 = Color(0xFF474D59) // Brushed sheen
    val darkCol4 = Color(0xFF1C1E24) // Gunmetal base

    val lightCol1 = Color(0xFF26262E) // Dark grey metal
    val lightCol2 = Color(0xFF141417) // Charcoal metal
    val lightCol3 = Color(0xFF383842) // Steel highlight
    val lightCol4 = Color(0xFF0B0C0E) // Jet black base

    val idle1 = if (isDarkTheme) darkCol1 else lightCol1
    val idle2 = if (isDarkTheme) darkCol2 else lightCol2
    val idle3 = if (isDarkTheme) darkCol3 else lightCol3
    val idle4 = if (isDarkTheme) darkCol4 else lightCol4

    val recCol1 = Color(0xFFF51F40)
    val recCol2 = Color(0xFFFF6B1A)
    val recCol3 = Color(0xFFD1143D)
    val recCol4 = Color(0xFF990A24)

    val col1 = lerpColor(idle1, recCol1, effectiveState)
    val col2 = lerpColor(idle2, recCol2, effectiveState)
    val col3 = lerpColor(idle3, recCol3, effectiveState)
    val col4 = lerpColor(idle4, recCol4, effectiveState)

    // Full-bleed continuous base gradient across the entire card
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(col4, col1.copy(alpha = 0.85f), col2),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
    )

    val c1 = Offset(w * (0.35f + 0.22f * sin(t * 0.7f)), h * (0.30f + 0.20f * cos(t * 0.6f)))
    val c2 = Offset(w * (0.65f + 0.20f * cos(t * 0.5f + 1.8f)), h * (0.45f + 0.22f * sin(t * 0.8f + 1.2f)))
    val c3 = Offset(w * (0.40f + 0.24f * sin(t * 0.6f + 3.2f)), h * (0.75f - 0.18f * cos(t * 0.4f + 2.1f)))
    val c4 = Offset(w * (0.70f - 0.22f * cos(t * 0.8f + 0.5f)), h * (0.25f + 0.18f * sin(t * 0.7f + 4.0f)))

    val maxR = kotlin.math.max(w, h) * 1.2f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(col1.copy(alpha = 0.45f), Color.Transparent),
            center = c1,
            radius = maxR
        ),
        center = c1,
        radius = maxR
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(col3.copy(alpha = 0.40f), Color.Transparent),
            center = c2,
            radius = maxR * 0.95f
        ),
        center = c2,
        radius = maxR * 0.95f
    )

    // Expanding shockwave ripple ring for fallback
    if (rippleProgress in 0.01f..1.6f) {
        val originPx = Offset(rippleOrigin.x * w, rippleOrigin.y * h)
        val rippleRadiusPx = rippleProgress * kotlin.math.max(w, h) * 1.3f
        val ringAlpha = (1f - rippleProgress * 0.55f).coerceIn(0f, 0.9f)
        val ringColor = if (targetState > 0.5f) Color(0xFFFF5252) else Color(0xFFB0B4C0)

        drawCircle(
            color = ringColor.copy(alpha = ringAlpha),
            center = originPx,
            radius = rippleRadiusPx,
            style = Stroke(width = 10.dp.toPx() * (1f - rippleProgress * 0.4f))
        )
    }
}

private fun lerpColor(c1: Color, c2: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return Color(
        red = c1.red + (c2.red - c1.red) * clamped,
        green = c1.green + (c2.green - c1.green) * clamped,
        blue = c1.blue + (c2.blue - c1.blue) * clamped,
        alpha = c1.alpha + (c2.alpha - c1.alpha) * clamped
    )
}

/**
 * State-of-the-Art Dynamic Permanent Shader Record Card
 *
 * - Dark Mode: True Gunmetal Grey (#24262E / #383B45) with controlled metallic sheen
 * - Light Mode: Mostly Black with Dark Grey Metal (#0B0C0E / #26262E)
 * - Typography: High-contrast Crisp Pure White with subtle shadow for 100% legibility
 * - Live Recording: High-Voltage Pulsing Crimson Embers & Solar Amber
 *
 * Corner Radius: 26.dp (Modern Material You Expressive aesthetic)
 */
@Composable
fun MainRecordShaderCard(
    isRecordingActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    titleText: String? = null,
    subtitleText: String? = null,
    badgeText: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    // Continuous flowing time animation (28-second smooth seamless loop)
    val infiniteTransition = rememberInfiniteTransition(label = "ShaderTimeTransition")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 62.83f, // 20 * PI
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shaderTime"
    )

    // Pulsing heartbeat ring animation when recording is active
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseRingAlpha"
    )

    // Ripple state tracking
    var rippleOrigin by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    val rippleProgressAnim = remember { Animatable(0f) }
    var targetState by remember { mutableStateOf(if (isRecordingActive) 1f else 0f) }

    // When isRecordingActive changes, fire the chromatic expanding shockwave ripple!
    LaunchedEffect(isRecordingActive) {
        targetState = if (isRecordingActive) 1f else 0f
        rippleProgressAnim.snapTo(0f)
        rippleProgressAnim.animateTo(
            targetValue = 1.75f,
            animationSpec = tween(
                durationMillis = 750,
                easing = FastOutSlowInEasing
            )
        )
    }

    // Smooth base fallback recording progress
    val baseRecordingProgress by animateFloatAsState(
        targetValue = if (isRecordingActive) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "baseRecordingProgress"
    )

    // Interactive bouncy spring press scale
    var isPressed by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardBouncyScale"
    )

    // Subtle hairline metallic border
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isRecordingActive) 0.65f else 0.35f),
            Color.White.copy(alpha = 0.06f),
            if (isRecordingActive) Color(0xFFFF5252).copy(alpha = 0.55f) else Color(0xFF4A4E5A).copy(alpha = 0.40f)
        ),
        start = Offset(0f, 0f),
        end = Offset(450f, 650f)
    )

    // Reusable shader helper for API 33+
    val agslHelper = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AgslGradientHelper()
        } else {
            null
        }
    }

    val darkFloat = if (isDarkTheme) 1.0f else 0.0f
    val cardShape = RoundedCornerShape(26.dp)

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, borderBrush),
        modifier = modifier
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
            .clip(cardShape)
            .drawWithCache {
                val width = size.width
                val height = size.height

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslHelper != null) {
                    agslHelper.update(
                        width = width,
                        height = height,
                        time = time,
                        recording = baseRecordingProgress,
                        rippleProgress = rippleProgressAnim.value,
                        rippleOriginX = rippleOrigin.x,
                        rippleOriginY = rippleOrigin.y,
                        targetState = targetState,
                        isDarkTheme = darkFloat
                    )
                    val shaderBrush = ShaderBrush(agslHelper.shader)
                    onDrawBehind {
                        drawRect(brush = shaderBrush)
                    }
                } else {
                    onDrawBehind {
                        drawMeshGradientFallback(
                            time = time,
                            recording = baseRecordingProgress,
                            rippleProgress = rippleProgressAnim.value,
                            rippleOrigin = rippleOrigin,
                            targetState = targetState,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
            }
            .pointerInput(isRecordingActive) {
                detectTapGestures(
                    onPress = { offset ->
                        rippleOrigin = Offset(
                            x = (offset.x / size.width).coerceIn(0f, 1f),
                            y = (offset.y / size.height).coerceIn(0f, 1f)
                        )
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        onClick()
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Glassmorphism Icon Badge + Live Indicator
            Row(
                modifier = Modifier.fillMaxSize().weight(1f, fill = false),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Glassmorphism Record Badge with dynamic aura
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(46.dp)
                ) {
                    // Outer pulse glow when recording
                    if (isRecordingActive) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF1744).copy(alpha = pulseAlpha * 0.38f))
                        )
                    }

                    // Frosted Glass Circle (Crisp Pure White on Gunmetal/Black Metal)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isRecordingActive) Color.White.copy(alpha = 0.28f)
                                else Color.Black.copy(alpha = 0.35f)
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.40f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = icon ?: Lucide.CircleDot,
                            contentDescription = if (isRecordingActive) "Stop Recording" else "Start Recording",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Dynamic Live Pill Badge (when recording)
                if (isRecordingActive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFD50000).copy(alpha = 0.50f))
                            .border(1.dp, Color(0xFFFF5252).copy(alpha = pulseAlpha * 0.85f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = badgeText ?: "REC",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }
            }

            // Bottom Typography (Crisp Pure White with subtle shadow for 100% legibility on Gunmetal/Black)
            Column {
                Text(
                    text = titleText ?: (if (isRecordingActive) "Recording" else "Record"),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    lineHeight = 26.sp,
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.60f),
                            offset = Offset(0f, 2f),
                            blurRadius = 6f
                        )
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleText ?: (if (isRecordingActive) "Tap to stop capture" else "Tap to start capture"),
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 15.sp,
                    style = LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.55f),
                            offset = Offset(0f, 1f),
                            blurRadius = 4f
                        )
                    )
                )
            }
        }
    }
}
