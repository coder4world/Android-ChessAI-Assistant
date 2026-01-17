package com.zfdang.chess.utils

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class CalibrationOverlayView(context: Context, val onConfirm: (Rect) -> Unit) : View(context) {
    
    private val rect = RectF(200f, 500f, 800f, 1100f) // 初始位置
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val handlePaint = Paint().apply { color = Color.RED }
    private val handleRadius = 40f
    private var activeHandle = -1 // 0:左上, 1:右上, 2:右下, 3:左下, 4:中心拖动

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0x44000000) // 蒙层
        canvas.drawRect(rect, paint)
        // 画四个角的触控点
        canvas.drawCircle(rect.left, rect.top, handleRadius, handlePaint)
        canvas.drawCircle(rect.right, rect.top, handleRadius, handlePaint)
        canvas.drawCircle(rect.right, rect.bottom, handleRadius, handlePaint)
        canvas.drawCircle(rect.left, rect.bottom, handleRadius, handlePaint)
        
        // 提示文字
        paint.style = Paint.Style.FILL
        paint.textSize = 40f
        canvas.drawText("拖动四角对齐棋子中心, 双击屏幕确认", 100f, 100f, paint)
        paint.style = Paint.Style.STROKE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeHandle = when {
                    dist(x, y, rect.left, rect.top) < handleRadius * 2 -> 0
                    dist(x, y, rect.right, rect.top) < handleRadius * 2 -> 1
                    dist(x, y, rect.right, rect.bottom) < handleRadius * 2 -> 2
                    dist(x, y, rect.left, rect.bottom) < handleRadius * 2 -> 3
                    rect.contains(x, y) -> 4
                    else -> -1
                }
                // 双击检测确认
                if (event.eventTime - lastClickTime < 300) {
                    confirm()
                }
                lastClickTime = event.eventTime
            }
            MotionEvent.ACTION_MOVE -> {
                when (activeHandle) {
                    0 -> { rect.left = x; rect.top = y }
                    1 -> { rect.right = x; rect.top = y }
                    2 -> { rect.right = x; rect.bottom = y }
                    3 -> { rect.left = x; rect.bottom = y }
                    4 -> { /* 整体平移逻辑省略，保持简单先调四角 */ }
                }
                invalidate()
            }
        }
        return true
    }

    private var lastClickTime = 0L
    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float) = Math.sqrt(((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2)).toDouble())

    private fun confirm() {
        val finalRect = Rect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
        onConfirm(finalRect)
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(this)
    }
}