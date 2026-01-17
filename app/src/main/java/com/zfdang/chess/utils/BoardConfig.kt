package com.zfdang.chess.utils

import android.content.Context
import android.graphics.Rect
import android.widget.Toast

object BoardConfig {
    private var grid: AutoBoardLocator.Grid? = null

    // 状态属性
    val isReady: Boolean get() = grid != null

    // 获取当前网格数据
    fun getGrid(): AutoBoardLocator.Grid? = grid

    /**
     * 从文件加载缓存到内存
     * 解决“ Unresolved reference: loadFromCache ”的关键点
     */
    fun loadFromCache(context: Context): Boolean {
        val cachedGrid = AutoBoardLocator.loadCache(context)
        if (cachedGrid != null) {
            this.grid = cachedGrid
            return true
        }
        return false
    }

    /**
     * 直接设置内存中的 Grid，并同步到磁盘缓存
     */
    fun setGrid(g: AutoBoardLocator.Grid, context: Context) {
        grid = g
        try {
            AutoBoardLocator.saveCache(context, g)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 基于校准框计算网格
     */
    fun setRect(rect: Rect, context: Context) {
        // 1. 获取状态栏的真实高度
        var statusBarHeight = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = context.resources.getDimensionPixelSize(resourceId)
        }

        val left = rect.left.toFloat()
        val right = rect.right.toFloat()

        // 2. 核心修正：给 top 和 bottom 加上这个高度差
        val top = rect.top.toFloat() + statusBarHeight
        val bottom = rect.bottom.toFloat() + statusBarHeight

        val width = right - left
        val height = bottom - top

        // 提示信息
        // Toast.makeText(context, "sBar:$statusBarHeight, ht:${height/9f}", Toast.LENGTH_SHORT).show()

        // --- 坐标偏移修正参数 ---
        val xOffsets = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        val yOffsets = floatArrayOf(
            0f,   // r0
            2f,   // r1
            2f,   // r2
            0f,   // r3
            0f,   // r4
            0f,   // r5
            0f,   // r6
            -3f,  // r7
            -3f,  // r8
            -7f   // r9  棋子偏上调小
        )

        val xLines = IntArray(9) { i ->
            (left + (i * width / 8f) + xOffsets[i]).toInt()
        }

        val yLines = IntArray(10) { i ->
            (top + (i * height / 9f) + yOffsets[i]).toInt()
        }

        val g = AutoBoardLocator.Grid(xLines, yLines)
        
        // 调用 setGrid 同时保存缓存
        setGrid(g, context)
    }
}