package pl.bbtv.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CursorView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    var cx = 200f
    var cy = 150f
    private val scale = resources.displayMetrics.density
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = 1.5f * scale }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 245, 245, 247) }
    fun move(dx: Float, dy: Float) { cx = (cx + dx).coerceIn(0f, (width - 1).coerceAtLeast(0).toFloat()); cy = (cy + dy).coerceIn(0f, (height - 1).coerceAtLeast(0).toFloat()); invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(cx, cy + 2 * scale, 13 * scale, shadow)
        canvas.drawCircle(cx, cy, 11 * scale, fill)
        canvas.drawCircle(cx, cy, 11 * scale, ring)
    }
}
