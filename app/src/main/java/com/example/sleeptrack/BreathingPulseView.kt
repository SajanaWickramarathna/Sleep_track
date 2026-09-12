package com.example.sleeptrack

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class BreathingPulseView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5")
        style = Paint.Style.FILL
        alpha = 150
    }

    private var currentScale = 0.5f
    private var currentAlpha = 150
    private var animatorSet: AnimatorSet? = null

    init {
        // Breathing cycle: 4 seconds inhale, 4 seconds exhale
        val scaleUp = ObjectAnimator.ofFloat(this, "pulseScale", 0.5f, 1.0f)
        scaleUp.duration = 4000
        
        val alphaDown = ObjectAnimator.ofInt(this, "pulseAlpha", 150, 50)
        alphaDown.duration = 4000

        val scaleDown = ObjectAnimator.ofFloat(this, "pulseScale", 1.0f, 0.5f)
        scaleDown.duration = 4000

        val alphaUp = ObjectAnimator.ofInt(this, "pulseAlpha", 50, 150)
        alphaUp.duration = 4000

        val inhale = AnimatorSet().apply { playTogether(scaleUp, alphaDown) }
        val exhale = AnimatorSet().apply { playTogether(scaleDown, alphaUp) }

        animatorSet = AnimatorSet().apply {
            playSequentially(inhale, exhale)
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    animation.start() // Loop infinite
                }
            })
        }
    }

    fun startBreathing() {
        if (animatorSet?.isStarted == false) {
            animatorSet?.start()
        }
    }

    fun stopBreathing() {
        animatorSet?.cancel()
    }

    fun setPulseScale(scale: Float) {
        currentScale = scale
        invalidate()
    }

    fun setPulseAlpha(alpha: Int) {
        currentAlpha = alpha
        circlePaint.alpha = alpha
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = (width.coerceAtMost(height) / 2f) * 0.8f
        
        canvas.drawCircle(cx, cy, baseRadius * currentScale, circlePaint)
    }
}
