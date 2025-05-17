package com.ewan.wallscheduler.util

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/***
 * applies a custom typeface to a span of text.
 * extends metric affecting span so that the font affects both measurement and drawing.
 * @param typeface the font to apply to the span.
 */
class CustomTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {

    /*** applies the font when measuring text layout ***/
    override fun updateMeasureState(paint: TextPaint) {
        apply(paint)
    }

    /*** applies the font when drawing text ***/
    override fun updateDrawState(paint: TextPaint) {
        apply(paint)
    }

    /*** helper function to apply the custom typeface ***/
    private fun apply(paint: Paint) {
        paint.typeface = typeface
    }
}
