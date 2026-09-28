package com.analoganchor.app.service

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator

/**
 * SpotlightOverlayService
 *
 * Draws a full-screen semi-transparent dim curtain over the Accessibility Settings screen,
 * with a transparent "spotlight" cutout around the "Day Guard (App Monitor)" row.
 * A pulsing animated ring highlights the target row for at least 3 seconds,
 * hiding all other rows (including AppBlock) behind the curtain.
 *
 * Requires SYSTEM_ALERT_WINDOW permission (already in manifest).
 */
class SpotlightOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: SpotlightView? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SpotlightOverlay"
        private const val DISPLAY_DURATION_MS = 4000L   // Total visible time (≥ 3s)
        private const val FADE_DURATION_MS    = 600L    // Fade-out at end

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "SYSTEM_ALERT_WINDOW not granted; skipping spotlight overlay.")
                return
            }
            try {
                val intent = Intent(context, SpotlightOverlayService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpotlightOverlayService: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showOverlay()
        // Auto-dismiss after display duration
        handler.postDelayed({ fadeOutAndStop() }, DISPLAY_DURATION_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun showOverlay() {
        if (overlayView != null) return

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val dm = DisplayMetrics()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            dm.widthPixels = bounds.width()
            dm.heightPixels = bounds.height()
        } else {
            wm.defaultDisplay.getRealMetrics(dm)
        }

        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        // Estimate the "Day Guard" row position.
        // After the deep-link scroll, the target row lands roughly in the upper third
        // of the visible list area (below the system status bar + settings toolbar ~200dp).
        // The row itself is ~72dp tall on most devices.
        val density = dm.density
        val statusBarApprox   = (80  * density).toInt()
        val toolbarApprox     = (148 * density).toInt()
        val rowHeight         = (72  * density).toInt()
        val rowPaddingV       = (6   * density).toInt()

        // The "Day Guard" entry in the list after native scroll lands ~1st or 2nd item.
        // We target a band that covers that row generously.
        val targetTop    = statusBarApprox + toolbarApprox + rowPaddingV
        val targetBottom = targetTop + rowHeight
        val targetLeft   = (12 * density).toInt()
        val targetRight  = screenW - (12 * density).toInt()

        val view = SpotlightView(
            context         = this,
            screenWidth     = screenW,
            screenHeight    = screenH,
            spotlightLeft   = targetLeft.toFloat(),
            spotlightTop    = targetTop.toFloat(),
            spotlightRight  = targetRight.toFloat(),
            spotlightBottom = targetBottom.toFloat()
        )
        overlayView = view

        val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            screenW, screenH,
            0, 0,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = android.view.Gravity.TOP or android.view.Gravity.START

        try {
            wm.addView(view, params)
            view.startPulseAnimation()
        } catch (e: Exception) {
            Log.e(TAG, "Could not add overlay view: ${e.message}")
            stopSelf()
        }
    }

    private fun fadeOutAndStop() {
        val view = overlayView ?: run { stopSelf(); return }
        view.fadeOut(durationMs = FADE_DURATION_MS) {
            removeOverlay()
            stopSelf()
        }
    }

    private fun removeOverlay() {
        try {
            overlayView?.stopAnimations()
            windowManager?.removeView(overlayView)
        } catch (_: Exception) {}
        overlayView = null
    }

    // -------------------------------------------------------------------------
    // Custom view that draws the dim curtain + spotlight cutout + pulsing ring
    // -------------------------------------------------------------------------

    class SpotlightView(
        context: Context,
        private val screenWidth: Int,
        private val screenHeight: Int,
        private val spotlightLeft: Float,
        private val spotlightTop: Float,
        private val spotlightRight: Float,
        private val spotlightBottom: Float
    ) : View(context) {

        // Dim layer
        private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 0, 0, 0) // ~70% black
        }

        // Eraser: punches a transparent hole for the spotlight cutout
        private val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }

        // Ring / border around the cutout
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * resources.displayMetrics.density
            color = Color.argb(255, 255, 200, 50) // warm amber
        }

        // Label "Tap here to enable"
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * resources.displayMetrics.density
            isFakeBoldText = true
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }

        // Arrow / indicator paint
        private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(255, 255, 200, 50)
            style = Paint.Style.FILL
        }

        private var masterAlpha: Float = 1f       // For fade-out
        private var ringAlpha: Float = 1f          // For pulse animation
        private var ringExpand: Float = 0f         // Extra size during pulse

        private var pulseAnimator: ValueAnimator? = null
        private var fadeAnimator: ValueAnimator? = null

        private val spotRect = RectF(
            spotlightLeft, spotlightTop, spotlightRight, spotlightBottom
        )
        private val cornerRadius = 16f * resources.displayMetrics.density

        init {
            // Hardware layer for proper PorterDuff compositing
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        fun startPulseAnimation() {
            pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 700L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    val v = anim.animatedFraction
                    ringAlpha = 0.4f + (0.6f * v)
                    ringExpand = 6f * resources.displayMetrics.density * v
                    invalidate()
                }
                start()
            }
        }

        fun fadeOut(durationMs: Long, onDone: () -> Unit) {
            fadeAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = durationMs
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    masterAlpha = anim.animatedValue as Float
                    alpha = masterAlpha
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        onDone()
                    }
                })
                start()
            }
        }

        fun stopAnimations() {
            pulseAnimator?.cancel()
            fadeAnimator?.cancel()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // 1. Draw full-screen dim layer
            canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), dimPaint)

            // 2. Punch out the spotlight (transparent cutout over the target row)
            canvas.drawRoundRect(spotRect, cornerRadius, cornerRadius, erasePaint)

            // 3. Draw pulsing ring around the cutout
            val expandedRect = RectF(
                spotRect.left   - ringExpand,
                spotRect.top    - ringExpand,
                spotRect.right  + ringExpand,
                spotRect.bottom + ringExpand
            )
            ringPaint.alpha = (255 * ringAlpha * masterAlpha).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(expandedRect, cornerRadius + ringExpand, cornerRadius + ringExpand, ringPaint)

            // 4. Draw label below the spotlight
            val labelText = "Tap here → enable 'Day Guard (App Monitor)'"
            val textWidth = labelPaint.measureText(labelText)
            val textX = (screenWidth / 2f) - (textWidth / 2f)
            val textY = spotRect.bottom + (28f * resources.displayMetrics.density)
            labelPaint.alpha = (255 * masterAlpha).toInt().coerceIn(0, 255)
            canvas.drawText(labelText, textX, textY, labelPaint)

            // 5. Draw a small arrow pointing up toward the row
            val arrowCx = screenWidth / 2f
            val arrowTip = spotRect.bottom + (8f * resources.displayMetrics.density)
            val arrowSize = 10f * resources.displayMetrics.density
            val path = android.graphics.Path().apply {
                moveTo(arrowCx, arrowTip)
                lineTo(arrowCx - arrowSize, arrowTip + arrowSize * 1.5f)
                lineTo(arrowCx + arrowSize, arrowTip + arrowSize * 1.5f)
                close()
            }
            arrowPaint.alpha = (255 * masterAlpha).toInt().coerceIn(0, 255)
            canvas.drawPath(path, arrowPaint)
        }
    }
}
