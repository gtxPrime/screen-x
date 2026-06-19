package com.gxdevs.screenx.service

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import android.widget.FrameLayout
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.*
import kotlinx.coroutines.delay

// ── Palette ───────────────────────────────────────────────────────────────────
private val Teal      = Color(0xFF00A19B)
private val TealDim   = Color(0x4400A19B)
private val DarkGlass = Color(0xBB0B1412)
private val GlassHigh = Color(0x18FFFFFF)
private val StopRed   = Color(0xFFFF453A)
private val BrushCyan = Color(0xFF5CE1E6)
private val CamGreen  = Color(0xFF34C759)
private val DismissRd = Color(0xCCFF3B30)
// ─────────────────────────────────────────────────────────────────────────────

class FloatingControlOverlay(private val context: Context) {

    private val density = context.resources.displayMetrics.density

    // Pre-computed expanded pill width (dp math):
    // outer padding(6) + orb(32) + gap(2) + inner-pad(4) + 4×btn(36) + 3×gap(2) + inner-pad(2) + outer-pad(6) = 198dp
    private val EXPANDED_W_PX = (198 * density).toInt()

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: android.view.View? = null
    private var lifecycleOwner: CustomLifecycleOwner? = null

    // Compose-observable states
    private val isExpandedState = mutableStateOf(false)
    private val isPausedState   = mutableStateOf(false)
    private val isRecordingState = mutableStateOf(false)
    private val inDismissZone   = mutableStateOf(false)
    private val lastTouchTime   = mutableStateOf(System.currentTimeMillis())

    private var isOnLeftEdge = true
    private var dismissGradientView: android.view.View? = null
    private var snapAnimator: ValueAnimator? = null
    private var isSnapped = false

    private val layoutParams = WindowManager.LayoutParams().apply {
        type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else { @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE }
        format  = PixelFormat.TRANSLUCENT
        flags   = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                  WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        width   = WindowManager.LayoutParams.WRAP_CONTENT
        height  = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.TOP or Gravity.START
        x = 0; y = 500
    }

    // ── Public API ────────────────────────────────────────────────────────────
    @Suppress("DEPRECATION")
    fun show(
        onStop:        () -> Unit,
        onPauseToggle: () -> Unit,
        onBrushToggle: () -> Unit,
        onScreenshot:  () -> Unit
    ) {
        updateState()
        lifecycleOwner = CustomLifecycleOwner().apply { onCreate(); onStart(); onResume() }

        val composeView = ComposeView(context).apply {
            setContent {
                PillContent(
                    onStop        = onStop,
                    onPauseToggle = onPauseToggle,
                    onBrushToggle = onBrushToggle,
                    onScreenshot  = {
                        // 1. Collapse + hide
                        isExpandedState.value = false
                        rootView?.postDelayed({
                            hideView()
                            // 2. Trigger service capture (service also calls hideView=no-op + showView)
                            rootView?.postDelayed({ onScreenshot() }, 200)
                        }, 100)
                    }
                )
            }
        }

        // ── FrameLayout handles drag / tap while collapsed ─────────────────
        val frameLayout = object : FrameLayout(context) {
            private var initialX    = 0;  private var initialY    = 0
            private var initialRawX = 0f; private var initialRawY = 0f
            private var isDragging  = false
            private val touchSlop   = android.view.ViewConfiguration.get(context).scaledTouchSlop

            @Suppress("DEPRECATION")
            override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
                // Signal Compose to reset idle timer on every touch
                if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
                    lastTouchTime.value = System.currentTimeMillis()
                }

                // Expanded → let Compose handle all input
                if (isExpandedState.value) {
                    inDismissZone.value = false
                    return super.dispatchTouchEvent(ev)
                }

                val metrics   = screenMetrics()
                val dismissTh = metrics.heightPixels * 0.80f

                when (ev.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX    = this@FloatingControlOverlay.layoutParams.x
                        initialY    = this@FloatingControlOverlay.layoutParams.y
                        initialRawX = ev.rawX;        initialRawY = ev.rawY
                        isDragging  = false
                        return true
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = ev.rawX - initialRawX; val dy = ev.rawY - initialRawY
                        if (!isDragging && dx * dx + dy * dy > touchSlop * touchSlop) {
                            isDragging = true
                            showDismissGradient()
                        }
                        if (isDragging) {
                            val targetX = (initialX + dx.toInt()).coerceIn(0, metrics.widthPixels - this.width)
                            val targetY = (initialY + dy.toInt()).coerceIn(0, metrics.heightPixels - this.height)

                            val ballCenterX = targetX + this.width / 2
                            val ballCenterY = targetY + this.height / 2
                            val circleCenterX = metrics.widthPixels / 2
                            val circleCenterY = (metrics.heightPixels - 90f * density).toInt()

                            val dist = Math.hypot((ballCenterX - circleCenterX).toDouble(), (ballCenterY - circleCenterY).toDouble())
                            val inZone = dist < 75 * density

                            if (inZone) {
                                if (!isSnapped) {
                                    isSnapped = true
                                    animateToSnap(circleCenterX - this.width / 2, circleCenterY - this.height / 2)
                                    setInDismissZone(true)
                                }
                            } else {
                                if (isSnapped) {
                                    isSnapped = false
                                    snapAnimator?.cancel()
                                    setInDismissZone(false)
                                }
                                this@FloatingControlOverlay.layoutParams.x = targetX
                                this@FloatingControlOverlay.layoutParams.y = targetY
                                safeUpdateLayout(this)
                            }
                        }
                        return true
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        hideDismissGradient()
                        isSnapped = false
                        snapAnimator?.cancel()
                        when {
                            !isDragging         -> expandPill()
                            inDismissZone.value -> { inDismissZone.value = false; dismiss() }
                            else                -> { inDismissZone.value = false; snapToEdge(true) }
                        }
                        return true
                    }
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        hideDismissGradient()
                        isSnapped = false
                        snapAnimator?.cancel()
                        inDismissZone.value = false; snapToEdge(true); return true
                    }
                }
                return super.dispatchTouchEvent(ev)
            }
        }

        frameLayout.setViewTreeLifecycleOwner(lifecycleOwner)
        frameLayout.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        frameLayout.setViewTreeViewModelStoreOwner(lifecycleOwner)
        frameLayout.addView(composeView)
        rootView = frameLayout

        try { windowManager.addView(rootView, layoutParams) }
        catch (e: Exception) { e.printStackTrace() }
    }

    // ── Expand: content grows first, then x is pinned in next frame (no visual jump) ──
    private fun expandPill() {
        isExpandedState.value = true          // grow content immediately
        if (!isOnLeftEdge) {
            // Post so the view has been laid out with the new expanded width
            rootView?.post {
                val view    = rootView ?: return@post
                val expandedW = view.width.takeIf { it > 0 } ?: EXPANDED_W_PX
                val targetX   = (screenMetrics().widthPixels - expandedW).coerceAtLeast(0)
                if (targetX != layoutParams.x) {
                    layoutParams.x = targetX
                    safeUpdateLayout(view)
                }
            }
        }
    }

    // ── Compose UI ────────────────────────────────────────────────────────────
    @Composable
    private fun PillContent(
        onStop:        () -> Unit,
        onPauseToggle: () -> Unit,
        onBrushToggle: () -> Unit,
        onScreenshot:  () -> Unit
    ) {
        var isExpanded by remember { isExpandedState }
        val isPaused   by remember { isPausedState }
        val isRecording by remember { isRecordingState }
        val inDismiss  by remember { inDismissZone }
        val lastTouch  by remember { lastTouchTime }

        // ── Auto-dim: pill fades to 15% after 2.5s idle ───────────────────
        var isIdle by remember { mutableStateOf(false) }
        LaunchedEffect(lastTouch, isExpanded) {
            isIdle = false
            if (!isExpanded) { delay(2500L); isIdle = true }
        }
        val pillAlpha by animateFloatAsState(
            targetValue   = if (isIdle) 0.15f else 1f,
            animationSpec = tween(700, easing = FastOutSlowInEasing),
            label         = "pillAlpha"
        )

        // ── Pill shape: morph corner radius ───────────────────────────────
        val corner by animateDpAsState(
            targetValue   = if (isExpanded) 16.dp else 40.dp,
            animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            label         = "corner"
        )
        val pillShape = RoundedCornerShape(corner)

        // ── Staggered button appearance (alpha + spring-scale) ────────────
        var b1 by remember { mutableStateOf(false) }
        var b2 by remember { mutableStateOf(false) }
        var b3 by remember { mutableStateOf(false) }
        var b4 by remember { mutableStateOf(false) }
        LaunchedEffect(isExpanded) {
            if (isExpanded) {
                delay(10L); b1 = true
                delay(55L); b2 = true
                delay(55L); b3 = true
                delay(55L); b4 = true
            } else {
                b4 = false; b3 = false; b2 = false; b1 = false
            }
        }

        // ── Pulsing orb glow when collapsed ───────────────────────────────
        val infiniteT = rememberInfiniteTransition(label = "orb")
        val orbGlow by infiniteT.animateFloat(
            0.35f, 0.75f,
            infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glow"
        )

        Box(
            modifier = Modifier
                .graphicsLayer { alpha = pillAlpha }
                .wrapContentSize()
                // Subtle neutral dark shadow
                .then(if (!inDismiss) Modifier.shadow(
                    elevation    = if (isExpanded) 16.dp else 6.dp,
                    shape        = pillShape,
                    ambientColor = Color.Black.copy(alpha = 0.35f),
                    spotColor    = Color.Black.copy(alpha = 0.45f)
                ) else Modifier.shadow(
                    elevation    = 16.dp,
                    shape        = pillShape,
                    ambientColor = StopRed.copy(alpha = 0.35f),
                    spotColor    = StopRed.copy(alpha = 0.45f)
                ))
        ) {
            // Glass body
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(pillShape)
                    .background(
                        color = if (inDismiss) DismissRd
                                else Color(0xB31F1F21)
                    )
            )
            // Border
            Box(modifier = Modifier
                .matchParentSize()
                .border(
                    0.5.dp,
                    Color(0x28FFFFFF),
                    pillShape
                )
            )

            // Content row — NO AnimatedVisibility for width (instant toggle eliminates jitter)
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier              = Modifier.padding(6.dp)
            ) {
                // ── Orb size animation for minimalist look ──
                val orbSize by animateDpAsState(
                    targetValue   = if (isExpanded) 28.dp else 24.dp,
                    animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                    label         = "orbSize"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(orbSize)
                        .clip(CircleShape)
                        .background(
                            if (inDismiss)
                                Brush.radialGradient(listOf(StopRed, Color(0xCCAA1A10)))
                            else if (isExpanded)
                                Brush.radialGradient(listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E)))
                            else
                                Brush.radialGradient(listOf(Color(0x803A3A3C), Color(0x991C1C1E)))
                        )
                        .then(if (isExpanded) Modifier.border(1.dp, Color(0x3DFFFFFF), CircleShape) else Modifier)
                        .clickable {
                            if (isExpanded) {
                                isExpanded = false
                                rootView?.postDelayed({ snapToEdge(true) }, 50)
                            } else {
                                expandPill()
                            }
                        }
                ) {
                    val icon = when {
                        inDismiss  -> Icons.Default.Delete
                        isExpanded -> Icons.Default.KeyboardArrowDown
                        else       -> Icons.Default.FiberManualRecord
                    }
                    val iconSize = if (isExpanded || inDismiss) 16.dp else 8.dp
                    Icon(
                        icon, 
                        null, 
                        tint     = if (inDismiss) Color.White else Color(0xEEFFFFFF), 
                        modifier = Modifier.size(iconSize)
                    )
                }

                // ── Expanded buttons: layout is INSTANT (no width animation = no jitter) ──
                // Buttons visually spring in via scale/alpha WITHOUT changing window size
                if (isExpanded) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier              = Modifier.padding(start = 4.dp, end = 2.dp)
                    ) {
                        if (isRecording) {
                            GlassBtn(b1, if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, Color.White) { onPauseToggle() }
                            GlassBtn(b2, Icons.Default.Stop,      StopRed)  { onStop() }
                            GlassBtn(b3, Icons.Default.Brush,     BrushCyan) { onBrushToggle() }
                            GlassBtn(b4, Icons.Default.CameraAlt, CamGreen)  { onScreenshot() }
                        } else {
                            // Play/Record button to start recording directly
                            GlassBtn(b1, Icons.Default.PlayArrow, CamGreen) {
                                isExpanded = false
                                val intent = Intent(context, TileHelperActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                context.startActivity(intent)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun animateToSnap(targetX: Int, targetY: Int) {
        val view = rootView ?: return
        snapAnimator?.cancel()
        val startX = layoutParams.x
        val startY = layoutParams.y
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = android.view.animation.OvershootInterpolator(1.4f)
            addUpdateListener { anim ->
                if (!isSnapped) return@addUpdateListener
                val f = anim.animatedFraction
                layoutParams.x = (startX + (targetX - startX) * f).toInt()
                layoutParams.y = (startY + (targetY - startY) * f).toInt()
                safeUpdateLayout(view)
            }
            start()
        }
    }

    // ── Snap to left or right edge ────────────────────────────────────────────
    @Suppress("DEPRECATION")
    private fun snapToEdge(animate: Boolean) {
        val view = rootView ?: return
        val m = screenMetrics()
        val viewW  = view.width.takeIf { it > 0 } ?: (32 * density).toInt()
        val viewH  = view.height.takeIf { it > 0 } ?: (32 * density).toInt()
        val targetX = if (layoutParams.x + viewW / 2 < m.widthPixels / 2) 0 else m.widthPixels - viewW
        val targetY = layoutParams.y.coerceIn(0, m.heightPixels - viewH)
        isOnLeftEdge = (targetX == 0)

        if (!animate) {
            layoutParams.x = targetX; layoutParams.y = targetY; safeUpdateLayout(view); return
        }
        val sx = layoutParams.x; val sy = layoutParams.y
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300; interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { a ->
                val t = 1f - (1f - a.animatedFraction).let { it * it * it }
                layoutParams.x = (sx + (targetX - sx) * t).toInt()
                layoutParams.y = (sy + (targetY - sy) * t).toInt()
                safeUpdateLayout(view)
            }
            start()
        }
    }

    @Suppress("DEPRECATION")
    private fun screenMetrics() = android.util.DisplayMetrics()
        .also { windowManager.defaultDisplay.getRealMetrics(it) }

    private fun safeUpdateLayout(view: android.view.View?) {
        view ?: return
        try { windowManager.updateViewLayout(view, layoutParams) } catch (_: Exception) {}
    }

    fun updateState() {
        isPausedState.value = ScreenRecordService.isPaused
        isRecordingState.value = ScreenRecordService.isRecording
    }
    private fun setInDismissZone(inZone: Boolean) {
        if (inDismissZone.value != inZone) {
            inDismissZone.value = inZone
            dismissGradientView?.postInvalidate()
        }
    }
    fun hideView()     { rootView?.visibility = android.view.View.GONE }
    fun showView()     { rootView?.visibility = android.view.View.VISIBLE }

    fun dismiss() {
        hideDismissGradient()
        lifecycleOwner?.onDestroy()
        rootView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        rootView = null; lifecycleOwner = null
    }

    fun bringToFront() {
        rootView?.let { view ->
            try {
                windowManager.removeViewImmediate(view)
                windowManager.addView(view, layoutParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ── Dismiss-zone circle: bottom-center circular trash target ──────
    @Suppress("DEPRECATION")
    private fun showDismissGradient() {
        if (dismissGradientView != null) return
        val view = object : android.view.View(context) {
            private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: android.graphics.Canvas) {
                val cx = width / 2f
                val cy = height - 90f * density
                val isHighlighted = inDismissZone.value
                val radius = if (isHighlighted) 34f * density else 30f * density

                // Draw a very subtle dark background overlay
                p.shader = null
                p.style = android.graphics.Paint.Style.FILL
                p.color = 0x22000000
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)

                // Draw the main circle background
                p.color = if (isHighlighted) 0xFFFF352A.toInt() else 0xCC1C1C1E.toInt()
                p.style = android.graphics.Paint.Style.FILL
                c.drawCircle(cx, cy, radius, p)

                // Draw the border of the circle
                p.color = if (isHighlighted) 0xFFFFFFFF.toInt() else 0xAAFF3B30.toInt()
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeWidth = 1.5f * density
                c.drawCircle(cx, cy, radius, p)

                // Draw the trash can icon in the center of the circle
                p.reset()
                p.isAntiAlias = true
                p.color = android.graphics.Color.WHITE
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeWidth = 2f * density
                p.strokeCap = android.graphics.Paint.Cap.ROUND

                val topY = cy - 5f * density
                val botY = cy + 6f * density
                
                // Draw bin outline (bottom of trash can)
                c.drawRoundRect(cx - 5f * density, topY + 2f * density, cx + 5f * density, botY, 1f * density, 1f * density, p)
                // Draw lid line
                c.drawLine(cx - 7f * density, topY + 1f * density, cx + 7f * density, topY + 1f * density, p)
                // Draw lid handle
                c.drawRoundRect(cx - 2.5f * density, topY - 2f * density, cx + 2.5f * density, topY + 1f * density, 0.5f * density, 0.5f * density, p)
                // Draw vertical lines inside
                c.drawLine(cx - 2f * density, topY + 4f * density, cx - 2f * density, botY - 2f * density, p)
                c.drawLine(cx + 2f * density, topY + 4f * density, cx + 2f * density, botY - 2f * density, p)
            }
        }
        val lp = WindowManager.LayoutParams().apply {
            type   = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                         WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                     else WindowManager.LayoutParams.TYPE_PHONE
            format = PixelFormat.TRANSLUCENT
            flags  = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                     WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width  = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }
        dismissGradientView = view
        try { windowManager.addView(view, lp) } catch (e: Exception) { dismissGradientView = null }
    }

    private fun hideDismissGradient() {
        dismissGradientView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        dismissGradientView = null
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    private class CustomLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lr   = LifecycleRegistry(this)
        private val ssrc = SavedStateRegistryController.create(this)
        private val vms  = ViewModelStore()
        override val lifecycle:          Lifecycle          = lr
        override val savedStateRegistry: SavedStateRegistry = ssrc.savedStateRegistry
        override val viewModelStore:     ViewModelStore     = vms
        fun onCreate()  { ssrc.performRestore(null); lr.currentState = Lifecycle.State.CREATED }
        fun onStart()   { lr.currentState = Lifecycle.State.STARTED }
        fun onResume()  { lr.currentState = Lifecycle.State.RESUMED }
        fun onDestroy() { lr.currentState = Lifecycle.State.DESTROYED; vms.clear() }
    }
}

// ── Glassmorphic button: simple icon with no individual background shape ──────
@Composable
private fun GlassBtn(
    visible: Boolean,
    icon:    ImageVector,
    tint:    Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue   = if (visible) 1f else 0.4f,
        animationSpec = spring(0.45f, 550f),
        label         = "bScale"
    )
    val alpha by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = tween(130),
        label         = "bAlpha"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(34.dp)
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
            .clickable(enabled = visible, onClick = onClick)
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}
