package com.example.sleeptrack

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class SleepRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 40f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#2A2D3E")
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 40f
        strokeCap = Paint.Cap.ROUND
    }

    private val rectF = RectF()
    private var currentProgress = 0f
    private var targetProgress = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val padding = 40f
        rectF.set(padding, padding, w - padding, h - padding)
        
        val colors = intArrayOf(
            Color.parseColor("#00E5FF"), // Cyan
            Color.parseColor("#7C4DFF"), // Purple
            Color.parseColor("#00E5FF")  // Cyan loop
        )
        val positions = floatArrayOf(0f, 0.5f, 1f)
        progressPaint.shader = SweepGradient(w / 2f, h / 2f, colors, positions)
        
        // Add glow
        progressPaint.setShadowLayer(20f, 0f, 0f, Color.parseColor("#7C4DFF"))
        setLayerType(LAYER_TYPE_SOFTWARE, progressPaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw track
        canvas.drawArc(rectF, 135f, 270f, false, trackPaint)
        
        // Draw progress
        val sweepAngle = 270f * (currentProgress / 100f)
        canvas.drawArc(rectF, 135f, sweepAngle, false, progressPaint)
    }

    fun setProgress(progress: Float) {
        this.targetProgress = progress.coerceIn(0f, 100f)
        val animator = ValueAnimator.ofFloat(0f, targetProgress)
        animator.duration = 1500
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            currentProgress = animation.animatedValue as Float
            invalidate()
        }
        animator.start()
    }
}
