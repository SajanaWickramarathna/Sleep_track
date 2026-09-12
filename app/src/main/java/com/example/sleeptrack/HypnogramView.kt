package com.example.sleeptrack

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class HypnogramView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#00E5FF")
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#888888")
        textSize = 24f
    }

    private var data: List<Int> = emptyList()

    fun setData(movementData: List<Int>) {
        this.data = movementData
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val gradient = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.parseColor("#4400E5FF"),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        
        // Draw labels
        canvas.drawText("Awake", 10f, 40f, textPaint)
        canvas.drawText("Light", 10f, height / 2f + 10f, textPaint)
        canvas.drawText("Deep", 10f, height - 20f, textPaint)
        
        if (data.isEmpty()) {
            canvas.drawText("No data available", width / 2f - 100f, height / 2f, textPaint)
            return
        }

        val path = Path()
        val fillPath = Path()
        
        val paddingLeft = 100f
        val chartWidth = width - paddingLeft
        
        val maxMovement = (data.maxOrNull() ?: 10).coerceAtLeast(10)
        
        val stepX = chartWidth / (data.size - 1).coerceAtLeast(1)
        
        var firstX = paddingLeft
        var firstY = height
        
        for (i in data.indices) {
            val movement = data[i]
            // High movement -> Y is low (near top), Low movement -> Y is high (near bottom)
            val normalized = (movement.toFloat() / maxMovement.toFloat()).coerceIn(0f, 1f)
            val x = paddingLeft + (i * stepX)
            // padding top/bottom
            val y = height - 40f - (normalized * (height - 80f))
            
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height)
                fillPath.lineTo(x, y)
                firstX = x
                firstY = y
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        
        fillPath.lineTo(width, height)
        fillPath.lineTo(firstX, height)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }
}
