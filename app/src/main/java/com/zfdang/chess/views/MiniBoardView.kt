package com.zfdang.chess.views
import android.util.Log
import android.content.Context
import android.graphics.*
import android.view.View
private const val TAG = "MiniBoardView"
/**
 * 专门用于悬浮窗预览的轻量级中文棋盘
 */
class MiniBoardView(context: Context) : View(context) {
    private var fen: String = "9/9/9/9/9/9/9/9/9/9 w"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 最优走法建议
    private var moveRed: String? = null    // 红方最优
    private var moveBlack: String? = null  // 黑方最优
    private var bestMoveUCI: String? = null // 综合最优 (单箭头模式)

    // --- 数据设置方法 ---

    fun setBestMoves(red: String?, black: String?) {
        this.moveRed = red
        this.moveBlack = black
        postInvalidate()
    }

    fun setBestMove(uci: String?) {
        Log.d(TAG, "setBestMove:$uci")
        this.bestMoveUCI = uci
        postInvalidate()
    }

    fun setFen(newFen: String) {
        Log.d(TAG, "setFen:$newFen")
        this.fen = newFen.split(" ")[0]
        
        // --- 建议添加下面这两行 ---
        this.bestMoveUCI = null
        this.moveRed = null
        // ------------------------
        
        postInvalidate()
    }
    // --- 辅助工具 ---

    private fun pieceToChinese(piece: Char): String {
        return when (piece) {
            'R' -> "车"; 'N' -> "马"; 'B' -> "相"; 'A' -> "仕"; 'K' -> "帅"; 'C' -> "炮"; 'P' -> "兵"
            'r' -> "车"; 'n' -> "马"; 'b' -> "象"; 'a' -> "士"; 'k' -> "将"; 'c' -> "炮"; 'p' -> "卒"
            else -> ""
        }
    }

    /**
     * 将 UCI (如 h2e2) 绘制成带箭头的直线
     */
    private fun drawArrow(canvas: Canvas, uci: String, color: Int, cellW: Float, cellH: Float) {
        if (uci.length < 4) return
        
        // 坐标转换逻辑：a-i 对应 0-8 列，'9'-'0' 对应 0-9 行
        val startCol = uci[0] - 'a'
        val startRow = '9' - uci[1]
        val endCol = uci[2] - 'a'
        val endRow = '9' - uci[3]

        val startX = startCol * cellW + cellW / 2
        val startY = startRow * cellH + cellH / 2
        val endX = endCol * cellW + cellW / 2
        val endY = endRow * cellH + cellH / 2

        // 画直线
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.color = color
        paint.alpha = 200
        canvas.drawLine(startX, startY, endX, endY, paint)
        
        // 画终点小圆点标识
        paint.style = Paint.Style.FILL
        canvas.drawCircle(endX, endY, 12f, paint)
    }

    // --- 核心绘图 ---

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cellW = w / 9
        val cellH = h / 10

        // 1. 画半透明深色背景
        paint.style = Paint.Style.FILL
        paint.color = 0xCC222222.toInt() 
        canvas.drawRoundRect(0f, 0f, w, h, 24f, 24f, paint)

        // 2. 画网格线 (仅显示参考线)
        paint.color = Color.DKGRAY
        paint.strokeWidth = 2f
        for (i in 0 until 9) canvas.drawLine(i * cellW + cellW / 2, cellH / 2, i * cellW + cellW / 2, h - cellH / 2, paint)
        for (i in 0 until 10) canvas.drawLine(cellW / 2, i * cellH + cellH / 2, w - cellW / 2, i * cellH + cellH / 2, paint)

        // 3. 遍历 FEN 绘制中文棋子
        val rows = fen.split("/")
        for (r in rows.indices) {
            if (r >= 10) break 
            var c = 0
            for (char in rows[r]) {
                if (char.isDigit()) {
                    c += char.toString().toInt()
                } else {
                    val cx = c * cellW + cellW / 2
                    val cy = r * cellH + cellH / 2
                    
                    // A. 画棋子背景圆饼
                    val isRed = char.isUpperCase()
                    paint.style = Paint.Style.FILL
                    paint.color = if (isRed) Color.parseColor("#CC0000") else Color.BLACK
                    canvas.drawCircle(cx, cy, cellW * 0.45f, paint)

                    // B. 画棋子边框（增加立体感）
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 3f
                    paint.color = Color.WHITE
                    canvas.drawCircle(cx, cy, cellW * 0.45f, paint)

                    // C. 画中文棋子文字
                    paint.style = Paint.Style.FILL
                    paint.textSize = cellW * 0.6f
                    paint.isFakeBoldText = true
                    paint.textAlign = Paint.Align.CENTER
                    
                    val text = pieceToChinese(char)
                    val fontMetrics = paint.fontMetrics
                    val offset = (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom
                    canvas.drawText(text, cx, cy + offset, paint)
                    
                    c++
                }
            }
        }

        // 4. 绘制提示箭头
        // 绘制单箭头 (如有)
        bestMoveUCI?.let { drawArrow(canvas, it, Color.YELLOW, cellW, cellH) }
        
        // 绘制双箭头 (红方建议用红线，黑方用青/蓝线)
        moveRed?.let { drawArrow(canvas, it, Color.RED, cellW, cellH) }
        moveBlack?.let { drawArrow(canvas, it, Color.CYAN, cellW, cellH) }
    }
}