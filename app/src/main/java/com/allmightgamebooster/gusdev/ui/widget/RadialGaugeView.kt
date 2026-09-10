package com.allmightgamebooster.gusdev.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.allmightgamebooster.gusdev.R

class RadialGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentValue: Float = 0f
    private var maxValue: Float = 100f
    private var label: String = ""
    private var unit: String = ""
    private var displayValue: String = "0"

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2D30")
        style = Paint.Style.FILL
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C97A4A")
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
    }

    private val redlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C13B3B")
        style = Paint.Style.STROKE
        strokeWidth = 16f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EDEBE6")
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8C949C")
        textAlign = Paint.Align.CENTER
        textSize = 28f
    }

    private val arcRect = RectF()

    fun setValue(value: Float, max: Float, label: String, unit: String) {
        this.currentValue = value
        this.maxValue = max
        this.label = label
        this.unit = unit
        this.displayValue = if (value >= 100) String.format("%.0f", value)
        else String.format("%.1f", value)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val radius = (minOf(w, h) / 2f) - 24f
        val strokeWidth = radius * 0.12f

        arcPaint.strokeWidth = strokeWidth
        redlinePaint.strokeWidth = strokeWidth

        arcRect.set(
            cx - radius, cy - radius,
            cx + radius, cy + radius
        )

        val startAngle = 135f
        val sweepAngle = 270f
        val ratio = (currentValue / maxValue).coerceIn(0f, 1f)
        val valueSweep = sweepAngle * ratio

        canvas.drawArc(arcRect, startAngle, sweepAngle, false, bgPaint)

        val redlineThreshold = 0.8f
        if (ratio > redlineThreshold) {
            val redlineStart = startAngle + sweepAngle * redlineThreshold
            val redlineSweep = valueSweep - sweepAngle * redlineThreshold
            canvas.drawArc(arcRect, startAngle, sweepAngle * redlineThreshold, false, arcPaint)
            canvas.drawArc(arcRect, redlineStart, redlineSweep, false, redlinePaint)
        } else {
            canvas.drawArc(arcRect, startAngle, valueSweep, false, arcPaint)
        }

        textPaint.textSize = radius * 0.45f
        canvas.drawText(displayValue, cx, cy + textPaint.textSize * 0.15f, textPaint)

        labelPaint.textSize = radius * 0.18f
        canvas.drawText(label, cx, cy - radius * 0.1f, labelPaint)

        textPaint.textSize = radius * 0.15f
        canvas.drawText(unit, cx, cy + radius * 0.25f, textPaint)
    }
}
