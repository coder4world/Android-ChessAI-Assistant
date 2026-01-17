package com.zfdang.chess.utils

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream


// object SampleCollector {
//     fun saveSamples(context: Context, bitmap: Bitmap, grid: AutoBoardLocator.Grid) {
//         val root = context.getExternalFilesDir(null) ?: return
//         val sampleDir = File(root, "Samples_TFLite")
//         if (!sampleDir.exists()) sampleDir.mkdirs()

//         val timestamp = System.currentTimeMillis()
        
//         // 计算格子宽度
//         val cellW = (grid.xLines[1] - grid.xLines[0])
//         // 缩减采样范围到 0.9f，这样切图会更干净，只包含棋子核心部分
//        // val cropSize = (cellW * 0.9f).toInt() 
//        // 稍微调小 cropSize，提高容错率
//         val cropSize = (cellW * 0.92f).toInt() //cellW它是两个相邻棋子中心点之间的横向距离（即一个格子的宽度）。它决定了我们要切多大的方块如果这个值太大切出来的图就会包含隔壁棋子的边缘或过多的棋盘线

//         // 确认这里修改为 0.82f - 0.85f
//         // 理由：JJ象棋棋子占格子的比例很大，如果 cropSize 太大，
//         // 一旦定位稍有偏差，就会把棋盘的“田字格”或“兵位十字”切进去，干扰 TFLite。

//         val half = cropSize / 2

//         for (r in 0 until 10) {
//             for (c in 0 until 9) {
//                 // 如果发现所有棋子都往右下偏了 1 像素，这里就减去 1
//                 val offsetX = 0 
//                 val offsetY = 0
                
//                 val cx = grid.xLines[c] + offsetX
//                 val cy = grid.yLines[r] + offsetY
                
//                 try {
//                     // 定义并计算 safe 坐标
//                     val left = cx - half
//                     val top = cy - half
                    
//                     val safeLeft = left.coerceIn(0, bitmap.width - cropSize)
//                     val safeTop = top.coerceIn(0, bitmap.height - cropSize)
                    
//                     val cell = Bitmap.createBitmap(bitmap, safeLeft, safeTop, cropSize, cropSize)
//                     val scaled = Bitmap.createScaledBitmap(cell, 64, 64, true)
                    
//                     val file = File(sampleDir, "cell_${timestamp}_r${r}_c${c}.png")
//                     FileOutputStream(file).use { out ->
//                         scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
//                     }
//                 } catch (e: Exception) { e.printStackTrace() }
//             }
//         }
//     }
// }



object SampleCollector {

    /**
     * 判断当前截图是否包含棋子（有效对局）
     * 原理：统计图中红子（帅/兵等）和黑子（将/卒等）典型颜色的像素占比
     */
    private fun isEffectiveScreenshot(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height

        // 1. 缩小检测范围：只检测屏幕中间 50% 的区域，过滤掉顶部的状态栏和底部的广告/按钮
        val scanTop = (height * 0.25).toInt()
        val scanBottom = (height * 0.75).toInt()
        val scanLeft = (width * 0.1).toInt()
        val scanRight = (width * 0.9).toInt()

        val step = 15 // 稍微加大步长，进一步提升性能
        var redCount = 0
        var blackCount = 0
        var totalPoints = 0

        for (y in scanTop until scanBottom step step) {
            for (x in scanLeft until scanRight step step) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalPoints++

                // --- 优化后的判定逻辑 ---
                
                // 红色判定：红色分量显著高于绿蓝分量 (JJ 象棋红子特征)
                // 不再使用固定值 150，而是使用相对比例，容错性更高
                if (r > 120 && r > g * 1.5 && r > b * 1.5) {
                    redCount++
                } 
                // 黑色判定：RGB 三者都很接近且都很低 (JJ 象棋黑子/深色木纹边缘特征)
                else if (r < 60 && g < 60 && b < 60) {
                    blackCount++
                }
            }
        }

        // 2. 打印日志调试 (非常重要！你在 Logcat 里过滤 "SampleCollector" 就能看到数值)
        val redRatio = redCount.toFloat() / totalPoints
        val blackRatio = blackCount.toFloat() / totalPoints
        android.util.Log.d("SampleCollector", "检测结果: 红比例=${String.format("%.4f", redRatio)}, 黑比例=${String.format("%.4f", blackRatio)}")

        // 3. 判定门槛
        // 象棋对局中，红黑棋子总数很多，即使只有一半在屏幕内，比例也会超过 0.01 (1%)
        // 如果你发现还是没截到图，把 0.01 调低到 0.005
        return redRatio > 0.01f && blackRatio > 0.01f
    }

    fun saveSamples(context: Context, bitmap: Bitmap, grid: AutoBoardLocator.Grid) {
        // --- 核心逻辑插入 ---
        if (!isEffectiveScreenshot(bitmap)) {
            // 如果不是有效截图，直接返回，不保存任何图片
            return 
        }

        val root = context.getExternalFilesDir(null) ?: return
        val sampleDir = File(root, "Samples_TFLite")
        if (!sampleDir.exists()) sampleDir.mkdirs()

        val timestamp = System.currentTimeMillis()
        
        // 计算格子宽度
        val cellW = (grid.xLines[1] - grid.xLines[0])
        // 缩减采样范围到 0.9f，这样切图会更干净，只包含棋子核心部分
       // val cropSize = (cellW * 0.9f).toInt() 
       // 稍微调小 cropSize，提高容错率
        val cropSize = (cellW * 0.92f).toInt() //cellW它是两个相邻棋子中心点之间的横向距离（即一个格子的宽度）。它决定了我们要切多大的方块如果这个值太大切出来的图就会包含隔壁棋子的边缘或过多的棋盘线

        // 确认这里修改为 0.82f - 0.85f
        // 理由：JJ象棋棋子占格子的比例很大，如果 cropSize 太大，
        // 一旦定位稍有偏差，就会把棋盘的“田字格”或“兵位十字”切进去，干扰 TFLite。

        val half = cropSize / 2

        for (r in 0 until 10) {
            for (c in 0 until 9) {
                // 如果发现所有棋子都往右下偏了 1 像素，这里就减去 1
                val offsetX = 0 
                val offsetY = 0
                
                val cx = grid.xLines[c] + offsetX
                val cy = grid.yLines[r] + offsetY
                
                try {
                    // 定义并计算 safe 坐标
                    val left = cx - half
                    val top = cy - half
                    
                    val safeLeft = left.coerceIn(0, bitmap.width - cropSize)
                    val safeTop = top.coerceIn(0, bitmap.height - cropSize)
                    
                    val cell = Bitmap.createBitmap(bitmap, safeLeft, safeTop, cropSize, cropSize)
                    val scaled = Bitmap.createScaledBitmap(cell, 64, 64, true)
                    
                    val file = File(sampleDir, "cell_${timestamp}_r${r}_c${c}.png")
                    FileOutputStream(file).use { out ->
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }
}
