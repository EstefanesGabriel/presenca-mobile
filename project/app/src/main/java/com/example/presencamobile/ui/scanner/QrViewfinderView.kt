package com.example.presencamobile.ui.scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import com.journeyapps.barcodescanner.ViewfinderView

/** Moldura desenhada sobre a mesma região quadrada usada pelo decodificador. */
class QrViewfinderView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewfinderView(context, attrs) {
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    init { setLaserVisibility(false) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val frame = framingRect ?: return
        val length = minOf(frame.width() * 0.16f, 28f * resources.displayMetrics.density)
        val left = frame.left.toFloat()
        val top = frame.top.toFloat()
        val right = frame.right.toFloat()
        val bottom = frame.bottom.toFloat()
        canvas.drawLine(left, top, left + length, top, cornerPaint)
        canvas.drawLine(left, top, left, top + length, cornerPaint)
        canvas.drawLine(right - length, top, right, top, cornerPaint)
        canvas.drawLine(right, top, right, top + length, cornerPaint)
        canvas.drawLine(left, bottom, left + length, bottom, cornerPaint)
        canvas.drawLine(left, bottom - length, left, bottom, cornerPaint)
        canvas.drawLine(right - length, bottom, right, bottom, cornerPaint)
        canvas.drawLine(right, bottom - length, right, bottom, cornerPaint)
    }
}
