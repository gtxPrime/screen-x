package com.gxdevs.screenx.service

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.*
import com.gxdevs.screenx.MainActivity
import com.gxdevs.screenx.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ── Minimal Palette ───────────────────────────────────────────────────────────
private val Teal       = Color(0xFF00C9B7)
private val StopRed    = Color(0xFFFF3B30)
private val BrushCyan  = Color(0xFF5CE1E6)
private val CamGreen   = Color(0xFF34C759)
private val DismissRd  = Color(0xCCFF3B30)
private val DarkGlass  = Color(0x990A0A0A) // Minimal translucent dark glass
private val BtnGlass   = Color(0x22FFFFFF) // Translucent button background
private val HairlineBd = Color(0x28FFFFFF) // Minimal delicate border
// ─────────────────────────────────────────────────────────────────────────────

class FloatingControlOverlay(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val settingsManager = SettingsManager(context)
    private val overlayScope = CoroutineScope(Dispatchers.Main)

    // Pre-computed expanded pill width (dp math):
    // orb(34) + 4×btn(34) + 4×gap(4) + padding(8) ≈ 198dp
    private val EXPANDED_W_PX = (206 * density).toInt()

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: android.view.View? = null
    private var lifecycleOwner: CustomLifecycleOwner? = null
    private var onDismissCallback: (() -> Unit)? = null

    val isShowing: Boolean
        get() = rootView != null && rootView?.windowToken != null

    // Compose-observable states
    private val isExpandedState    = mutableStateOf(false)
    private val isPausedState      = mutableStateOf(false)
    private val isRecordingState   = mutableStateOf(false)
    private val isAdbRecordingState = mutableStateOf(false)
    private val inDismissZone      = mutableStateOf(false)
    private val lastTouchTime      = mutableStateOf(System.currentTimeMillis())

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
        x = -(11 * density).toInt() // Initially 30% hidden on left edge
        y = 600
    }

    // ── Public API ────────────────────────────────────────────────────────────
    @Suppress("DEPRECATION")
    fun show(
        onStop:        () -> Unit,
        onPauseToggle: () -> Unit,
        onBrushToggle: () -> Unit,
        onScreenshot:  () -> Unit,
        onDismiss:     (() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
            return
        }
        if (rootView != null) {
            showView()
            updateState()
            return
        }

        onDismissCallback = onDismiss
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
                        collapsePill()
                        rootView?.postDelayed({
                            hideView()
                            // 2. Trigger capture
                            rootView?.postDelayed({ onScreenshot() }, 200)
                        }, 100)
                    },
                    onDismiss     = { dismiss() }
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
                if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
                    lastTouchTime.value = System.currentTimeMillis()
                }

                if (isExpandedState.value) {
                    inDismissZone.value = false
                    return super.dispatchTouchEvent(ev)
                }

                val metrics = screenMetrics()

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
                            val targetX = (initialX + dx.toInt()).coerceIn(-this.width / 2, metrics.widthPixels - this.width / 2)
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
                            inDismissZone.value -> { inDismissZone.value = false; dismiss(fromUserDrag = true) }
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

        try {
            windowManager.addView(rootView, layoutParams)
            rootView?.post { snapToEdge(false) }
        } catch (e: Exception) {
            e.printStackTrace()
            rootView = null
        }
    }

    // ── Expand: undock to edge and grow ──────────────────────────────────────
    private fun expandPill() {
        isExpandedState.value = true
        val m = screenMetrics()
        val view = rootView ?: return
        if (isOnLeftEdge) {
            layoutParams.x = 0
            safeUpdateLayout(view)
        } else {
            val expandedW = view.width.takeIf { it > 0 } ?: EXPANDED_W_PX
            layoutParams.x = (m.widthPixels - expandedW).coerceAtLeast(0)
            safeUpdateLayout(view)
        }
        rootView?.post {
            val v = rootView ?: return@post
            val expandedW = v.width.takeIf { it > 0 } ?: EXPANDED_W_PX
            if (!isOnLeftEdge) {
                val targetX = (m.widthPixels - expandedW).coerceAtLeast(0)
                if (targetX != layoutParams.x) {
                    layoutParams.x = targetX
                    safeUpdateLayout(v)
                }
            }
        }
    }

    private fun collapsePill() {
        isExpandedState.value = false
        rootView?.postDelayed({ snapToEdge(true) }, 50)
    }

    // ── Compose UI ────────────────────────────────────────────────────────────
    @Composable
    private fun PillContent(
        onStop:        () -> Unit,
        onPauseToggle: () -> Unit,
        onBrushToggle: () -> Unit,
        onScreenshot:  () -> Unit,
        onDismiss:     () -> Unit
    ) {
        var isExpanded by remember { isExpandedState }
        val isPaused   by remember { isPausedState }
        val isRecording by remember { isRecordingState }
        val isAdbRecording by remember { isAdbRecordingState }
        val inDismiss  by remember { inDismissZone }
        val lastTouch  by remember { lastTouchTime }

        // ── Auto-dim and Auto-collapse ────────────────────────────────────────
        var isIdle by remember { mutableStateOf(false) }
        LaunchedEffect(lastTouch, isExpanded) {
            isIdle = false
            if (isExpanded) {
                // If user leaves the expanded pill untouched for 6 seconds, collapse back to ball
                delay(6000L)
                collapsePill()
            } else {
                delay(3000L)
                isIdle = true
            }
        }
        val pillAlpha by animateFloatAsState(
            targetValue   = if (isIdle) 0.65f else 1f,
            animationSpec = tween(400, easing = FastOutSlowInEasing),
            label         = "pillAlpha"
        )

        // ── Pill shape ────────────────────────────────────────────────────────
        val pillShape = CircleShape

        // ── Staggered button appearance ───────────────────────────────────────
        var b1 by remember { mutableStateOf(false) }
        var b2 by remember { mutableStateOf(false) }
        var b3 by remember { mutableStateOf(false) }
        var b4 by remember { mutableStateOf(false) }
        LaunchedEffect(isExpanded) {
            if (isExpanded) {
                delay(10L); b1 = true
                delay(35L); b2 = true
                delay(35L); b3 = true
                delay(35L); b4 = true
            } else {
                b4 = false; b3 = false; b2 = false; b1 = false
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer { alpha = pillAlpha }
                .wrapContentSize()
                .shadow(
                    elevation    = if (isExpanded) 12.dp else 6.dp,
                    shape        = pillShape,
                    ambientColor = Color.Black.copy(alpha = 0.5f),
                    spotColor    = if (inDismiss) StopRed else if (isRecording) StopRed.copy(0.4f) else Color.Black.copy(0.4f)
                )
        ) {
            // Semi-transparent black glass body
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(pillShape)
                    .background(
                        color = when {
                            inDismiss   -> DismissRd
                            isExpanded  -> DarkGlass
                            isRecording -> Color(0xB3140A0A)
                            else        -> Color(0xB3101010) // Black semi-transparent
                        }
                    )
            )
            // Delicate hairline border
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 0.6.dp,
                        color = when {
                            inDismiss   -> Color.White.copy(alpha = 0.8f)
                            isRecording -> StopRed.copy(alpha = 0.6f)
                            else        -> HairlineBd
                        },
                        shape = pillShape
                    )
            )

            // Content Row
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier              = Modifier.padding(2.dp)
            ) {
                // ── Handle / Orb ──
                val orbSize = if (isExpanded) 31.dp else 35.dp
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(orbSize)
                        .clip(CircleShape)
                        .then(
                            if (inDismiss) {
                                Modifier.background(Brush.radialGradient(listOf(StopRed, Color(0xCCAA1A10))))
                            } else {
                                Modifier.background(
                                    when {
                                        isExpanded  -> BtnGlass
                                        isRecording -> Color(0x44FF3B30)
                                        else        -> Color(0x1AFFFFFF)
                                    }
                                )
                            }
                        )
                        .clickable {
                            if (isExpanded) {
                                collapsePill()
                            } else {
                                expandPill()
                            }
                        }
                ) {
                    if (isRecording && !isExpanded && !inDismiss) {
                        // Pulsing red recording dot
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue  = 0.8f,
                            targetValue   = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation  = tween(700, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dotScale"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(StopRed)
                        )
                    } else {
                        val icon = when {
                            inDismiss   -> Icons.Outlined.Delete
                            isExpanded  -> if (isOnLeftEdge) Icons.Outlined.ChevronLeft else Icons.Outlined.ChevronRight
                            isRecording -> Icons.Outlined.FiberManualRecord
                            else        -> Icons.Outlined.Videocam
                        }
                        Icon(
                            imageVector        = icon,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(16.dp)
                        )
                    }
                }

                // ── Expanded action buttons ──
                if (isExpanded) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier              = Modifier.padding(start = 2.dp, end = 4.dp)
                    ) {
                        if (isAdbRecording) {
                            // ADB Recording Controls
                            GlassBtn(b1, Icons.Outlined.Stop, Color.White) {
                                collapsePill()
                                val intent = Intent(context, AdbRecordService::class.java).apply {
                                    action = AdbRecordService.ACTION_STOP_ADB
                                }
                                context.startService(intent)
                            }
                            GlassBtn(b2, Icons.Outlined.CameraAlt, Color.White) { onScreenshot() }
                            GlassBtn(b3, Icons.Outlined.Home, Color.White) {
                                collapsePill()
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                context.startActivity(intent)
                            }
                            GlassBtn(b4, Icons.Outlined.Close, Color.White) {
                                collapsePill()
                            }
                        } else if (isRecording) {
                            // Standard MediaProjection Controls
                            GlassBtn(b1, if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, Color.White) { onPauseToggle() }
                            GlassBtn(b2, Icons.Outlined.Stop, Color.White) {
                                collapsePill()
                                onStop()
                            }
                            GlassBtn(b3, Icons.Outlined.Brush, Color.White) { onBrushToggle() }
                            GlassBtn(b4, Icons.Outlined.CameraAlt, Color.White) { onScreenshot() }
                        } else {
                            // Standby Controls:
                            // 1. Record (respects ADB setting and handles unpaired gracefully!)
                            GlassBtn(b1, Icons.Outlined.RadioButtonChecked, Color.White) {
                                collapsePill()
                                overlayScope.launch {
                                    val adbEnabled = settingsManager.adbEnabledFlow.first()
                                    val captureMode = settingsManager.adbCaptureModeFlow.first()
                                    if (adbEnabled && captureMode == "adb") {
                                        val isAdbPaired = settingsManager.adbPairedFlow.first()
                                        if (!isAdbPaired) {
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                Toast.makeText(
                                                    context,
                                                    "Wireless ADB is not paired. Please open ScreenX to pair.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                            val intent = Intent(context, MainActivity::class.java).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                            }
                                            context.startActivity(intent)
                                            return@launch
                                        }
                                        val intent = Intent(context, AdbRecordService::class.java).apply {
                                            action = AdbRecordService.ACTION_START_ADB
                                        }
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                context.startForegroundService(intent)
                                            } else {
                                                context.startService(intent)
                                            }
                                        } catch (e: Exception) {
                                            Log.e("FloatingControlOverlay", "Failed to start ADB service", e)
                                        }
                                    } else {
                                        val intent = Intent(context, TileHelperActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            }
                            // 2. Open ScreenX Home
                            GlassBtn(b2, Icons.Outlined.Home, Color.White) {
                                collapsePill()
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                }
                                context.startActivity(intent)
                            }
                            // 3. Settings
                            GlassBtn(b3, Icons.Outlined.Settings, Color.White) {
                                collapsePill()
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
                                }
                                context.startActivity(intent)
                            }
                            // 4. Close (Collapse back to ball)
                            GlassBtn(b4, Icons.Outlined.Close, Color.White) {
                                collapsePill()
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

    // ── Snap to left or right edge with 30% hidden in closed form ──────────────
    @Suppress("DEPRECATION")
    private fun snapToEdge(animate: Boolean) {
        val view = rootView ?: return
        val m = screenMetrics()
        val viewW  = view.width.takeIf { it > 0 } ?: (39 * density).toInt()
        val viewH  = view.height.takeIf { it > 0 } ?: (39 * density).toInt()

        val isLeft = (layoutParams.x + viewW / 2) < (m.widthPixels / 2)
        isOnLeftEdge = isLeft

        // If collapsed: dock ~30% into the screen edge for a minimal, sleek handle
        // If expanded: stay fully on screen
        val targetX = if (isExpandedState.value) {
            if (isLeft) 0 else m.widthPixels - viewW
        } else {
            if (isLeft) -(viewW * 0.30f).toInt() else m.widthPixels - (viewW * 0.70f).toInt()
        }

        val minY = (50 * density).toInt()
        val maxY = (m.heightPixels - viewH - 60 * density).toInt()
        val targetY = layoutParams.y.coerceIn(minY, maxY)

        if (!animate) {
            layoutParams.x = targetX; layoutParams.y = targetY; safeUpdateLayout(view); return
        }
        val sx = layoutParams.x; val sy = layoutParams.y
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260; interpolator = DecelerateInterpolator(1.8f)
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
        isPausedState.value       = ScreenRecordService.isPaused
        isAdbRecordingState.value = AdbRecordService.isRecording
        isRecordingState.value    = ScreenRecordService.isRecording || AdbRecordService.isRecording
        collapsePill()
    }

    private fun setInDismissZone(inZone: Boolean) {
        if (inDismissZone.value != inZone) {
            inDismissZone.value = inZone
            dismissGradientView?.postInvalidate()
        }
    }

    fun hideView() { rootView?.visibility = android.view.View.GONE }
    fun showView() { rootView?.visibility = android.view.View.VISIBLE }

    fun dismiss(fromUserDrag: Boolean = false) {
        val callback = onDismissCallback
        onDismissCallback = null
        hideDismissGradient()
        lifecycleOwner?.onDestroy()
        rootView?.let { 
            try { 
                windowManager.removeView(it) 
            } catch (_: Exception) {} 
        }
        rootView = null
        lifecycleOwner = null
        if (fromUserDrag) {
            callback?.invoke()
        }
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

                p.shader = null
                p.style = android.graphics.Paint.Style.FILL
                p.color = 0x22000000
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)

                p.color = if (isHighlighted) 0xFFFF352A.toInt() else 0xCC1C1C1E.toInt()
                p.style = android.graphics.Paint.Style.FILL
                c.drawCircle(cx, cy, radius, p)

                p.color = if (isHighlighted) 0xFFFFFFFF.toInt() else 0xAAFF3B30.toInt()
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeWidth = 1.5f * density
                c.drawCircle(cx, cy, radius, p)

                p.reset()
                p.isAntiAlias = true
                p.color = android.graphics.Color.WHITE
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeWidth = 2f * density
                p.strokeCap = android.graphics.Paint.Cap.ROUND

                val topY = cy - 5f * density
                val botY = cy + 6f * density

                c.drawRoundRect(cx - 5f * density, topY + 2f * density, cx + 5f * density, botY, 1f * density, 1f * density, p)
                c.drawLine(cx - 7f * density, topY + 1f * density, cx + 7f * density, topY + 1f * density, p)
                c.drawRoundRect(cx - 2.5f * density, topY - 2f * density, cx + 2.5f * density, topY + 1f * density, 0.5f * density, 0.5f * density, p)
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

// ── Translucent Glassmorphic Button ──────────────────────────────────────────
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
            .size(32.dp)
            .scale(scale)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f)) // Translucent glass button
            .clickable(enabled = visible, onClick = onClick)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
    }
}
