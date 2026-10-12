package com.example.presencamobile.ui.scanner

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import com.journeyapps.barcodescanner.BarcodeView
import kotlin.math.min

/** Mantém a área visível e a região realmente decodificada sempre quadradas. */
class QrBarcodeView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : BarcodeView(context, attrs) {
    override fun calculateFramingRect(container: Rect, surface: Rect): Rect {
        val visible = Rect(container)
        if (!visible.intersect(surface)) return Rect()
        val side = (min(visible.width(), visible.height()) * 0.72f).toInt()
        val left = visible.centerX() - side / 2
        val top = visible.centerY() - side / 2
        return Rect(left, top, left + side, top + side)
    }
}
