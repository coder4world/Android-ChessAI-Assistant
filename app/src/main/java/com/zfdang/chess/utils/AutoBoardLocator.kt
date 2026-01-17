package com.zfdang.chess.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import android.util.Log
object AutoBoardLocator {

    data class Grid(
        val xLines: IntArray,
        val yLines: IntArray
    ) : java.io.Serializable

    fun cacheFile(context: Context) =
        File(context.getExternalFilesDir(null), "jj_board_grid.cache")

    // ★ FIX：唯一 grid 生成逻辑
    private fun generateGrid(rect: Rect): Grid {
        val x = IntArray(9) { i -> rect.left + i * rect.width() / 8 }
        val y = IntArray(10) { i -> rect.top + i * rect.height() / 9 }
        return Grid(x, y)
    }

    // fun rectToGrid(rect: Rect): Grid = generateGrid(rect)

    fun rectToGrid(rect: Rect, context: Context): Grid {
        // 强制走统一的修正逻辑
        BoardConfig.setRect(rect, context)
        return BoardConfig.getGrid()!!
    }

    fun locate(context: Context, bitmap: Bitmap): Grid {
        loadCache(context)?.let { return it }

        val rect = detectBoardRect(bitmap)
        BoardConfig.setRect(rect, context) 
        return BoardConfig.getGrid()!!

        // val rect = detectBoardRect(bitmap)
        // val grid = generateGrid(rect)
        // saveCache(context, grid)
        // return grid
    }

    // fun loadCache(context: Context): Grid? {
    //     val f = cacheFile(context)
    //     if (!f.exists()) return null
    //     return try {
    //         ObjectInputStream(f.inputStream()).use {
    //             it.readObject() as Grid
    //         }
    //     } catch (e: Exception) {
    //         null
    //     }
    // }

    fun loadCache(context: Context): Grid? {
        // 1. 尝试从外部存储加载 (用户之前保存过的)
        val f = cacheFile(context)
        if (f.exists()) {
            try {
                ObjectInputStream(f.inputStream()).use {
                    return it.readObject() as Grid
                }
            } catch (e: Exception) {
                Log.e("Cache", "External cache read failed", e)
            }
        }

        // 2. 外部存储没有，尝试从 Assets (APK内置默认值) 加载
        return try {
            context.assets.open("jj_board_grid.cache").use { inputStream ->
                ObjectInputStream(inputStream).use {
                    Log.i("Cache", "Loaded default grid from Assets")
                    it.readObject() as Grid
                }
            }
        } catch (e: Exception) {
            // 如果 assets 里也没有，就返回 null，走 locate 逻辑
            Log.d("Cache", "No default cache in assets")
            null
        }
    }

    fun saveCache(context: Context, grid: Grid) {
        try {
            ObjectOutputStream(cacheFile(context).outputStream()).use {
                it.writeObject(grid)
            }
        } catch (_: Exception) {}
    }

    private fun detectBoardRect(bitmap: Bitmap): Rect {
        val w = bitmap.width
        val h = bitmap.height
        return Rect(
            (w * 0.15f).toInt(),
            (h * 0.18f).toInt(),
            (w * 0.85f).toInt(),
            (h * 0.85f).toInt()
        )
    }
}
