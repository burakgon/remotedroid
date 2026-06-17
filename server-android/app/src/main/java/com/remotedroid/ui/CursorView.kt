package com.remotedroid.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** TV'de gösterilen imleç görseli (içi dolu nokta + halka). Yalnızca görsel; dokunmayı geçirgen. */
class CursorView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2F6DF0")
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 2f
    }

    override fun onDraw(canvas: Canvas) {
        val r = width / 2f
        canvas.drawCircle(r, r, r * 0.55f, fill)
        canvas.drawCircle(r, r, r * 0.80f, ring)
    }
}
