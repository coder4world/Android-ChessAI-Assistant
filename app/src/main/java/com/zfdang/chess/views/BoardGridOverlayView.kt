package com.zfdang.chess.views

import android.content.Context
import android.graphics.*
import android.view.View
import com.zfdang.chess.utils.AutoBoardLocator
import com.zfdang.chess.utils.BoardConfig

class BoardGridOverlayView(
    context: Context,
    var grid: AutoBoardLocator.Grid
) : View(context) {

    // 网格线画笔：半透明红色
    private val linePaint = Paint().apply {
        color = 0x88FF0000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // 中心点画笔：亮绿色，方便对比
    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 文字标注画笔：显示行列号
    private val textPaint = Paint().apply {
        color = Color.YELLOW
        textSize = 24f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 始终获取最新的 Grid 
       // val currentGrid = BoardConfig.getGrid() ?: grid
        val currentGrid = grid
        val x = currentGrid.xLines
        val y = currentGrid.yLines

        // 1. 画纵向线
        for (i in x) {
            canvas.drawLine(i.toFloat(), y.first().toFloat(), i.toFloat(), y.last().toFloat(), linePaint)
        }

        // 2. 画横向线
        for (j in y) {
            canvas.drawLine(x.first().toFloat(), j.toFloat(), x.last().toFloat(), j.toFloat(), linePaint)
        }

        // 3. 关键：在每个交叉点画一个小圆点，这就是 TFLite 采样的几何中心
        for (r in y.indices) {
            for (c in x.indices) {
                val cx = x[c].toFloat()
                val cy = y[r].toFloat()
                
                // 画采样中心圆点
                canvas.drawCircle(cx, cy, 5f, pointPaint)
                
                // 只在边缘画行列号，方便你在 Adjust 数组里对号入座
                if (c == 0) canvas.drawText("r$r", cx - 40f, cy + 10f, textPaint)
                if (r == 0) canvas.drawText("c$c", cx - 15f, cy - 15f, textPaint)
            }
        }
    }

    // 提供一个外部调用方法，校准完成后刷新
    fun updateGrid(newGrid: AutoBoardLocator.Grid) {
        this.grid = newGrid
        invalidate()
    }
}