package com.zfdang.chess.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.nio.MappedByteBuffer

object BoardRecognizer {

    private const val TAG = "BoardRecognizer"

    @Volatile
    private var initialized = false

    private var interpreter: Interpreter? = null
    private lateinit var labels: List<String>

    private var interpreterFP32: Interpreter? = null
    private var interpreterINT8: Interpreter? = null
    private val imageProcessorForFP32: ImageProcessor by lazy {
        ImageProcessor.Builder()
            .add(ResizeOp(64, 64, ResizeOp.ResizeMethod.BILINEAR))
            //.add(NormalizeOp(0f, 255f)) // 在模型里统一归一化到 
            .add(org.tensorflow.lite.support.common.ops.CastOp(org.tensorflow.lite.DataType.FLOAT32)) // FP32模型
            .build()
    }

    private val imageProcessorForINT8: ImageProcessor by lazy {
        ImageProcessor.Builder()
            .add(ResizeOp(64, 64, ResizeOp.ResizeMethod.BILINEAR))
            //.add(NormalizeOp(0f, 255f))
            .add(org.tensorflow.lite.support.common.ops.CastOp(org.tensorflow.lite.DataType.UINT8)) // INT8模型
            .build()
    }

    @Synchronized
    fun init(context: Context) {
        if (initialized) return

        try {

            val modelFP32: MappedByteBuffer = FileUtil.loadMappedFile(context, "chess_model.tflite")
            val modelINT8: MappedByteBuffer = FileUtil.loadMappedFile(context, "chess_model_int8.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }

            // val fd = context.assets.openFd("chess_model.tflite")
            // Log.i(TAG, "model asset length=${fd.length}")

            
            interpreterFP32 = Interpreter(modelFP32, options)
            interpreterINT8 = Interpreter(modelINT8, options)
            // if(useFP32) {
            //     val tempInterpreter = interpreterFP32
            // } else {
            //     val tempInterpreter = interpreterINT8
            // }


            // // 关键：获取并格式化形状
            // val inputShape = tempInterpreter.getInputTensor(0).shape()
            // val shapeInfo = inputShape.contentToString() 

            labels = FileUtil.loadLabels(context, "labels.txt")

            // interpreter = tempInterpreter
            initialized = true

            //Log.i(TAG, "Model Loaded. Input Shape: $shapeInfo, Labels Count: ${labels.size}")

        } catch (e: Exception) {
            Log.e(TAG, "Init failed", e)
        }
    }
    fun getActiveSide(bitmap: Bitmap, grid: AutoBoardLocator.Grid): String {
        if (!initialized) return "w"

        var redScore = 0
        var blackScore = 0

        // for (r in 0 until 10) {
        //     for (c in 0 until 9) {
        //         val cx = grid.xLines[c]
        //         val cy = grid.yLines[r]
                
        //         val color = bitmap.getPixel(cx, cy)
        //         val brightness = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000

        //         // 只有极高亮度的才参与判断
        //         if (brightness > 210) { 
        //             val label = classify(cropCell(bitmap, grid, r, c), r, c)
                    
        //             // 如果高亮处是红棋，增加红方分值
        //             if (label.startsWith("red")) redScore++
        //             // 如果高亮处是黑棋，增加黑方分值
        //             if (label.startsWith("black")) blackScore++
                    
        //             // 调试信息
        //             Log.d(TAG, "🔎 Trace: [$r,$c] L:$label B:$brightness")
        //         }
        //     }
        // }

        return when {
            blackScore > redScore -> "w"
            redScore > blackScore -> "b"
            else -> "w" 
        }
    }

    fun recognize(bitmap: Bitmap,  useFP32: Boolean): String {

        Log.d(" debug", Thread.currentThread().getStackTrace()[2].getMethodName() + "( debug)  "+Thread.currentThread().getStackTrace()[2].getFileName()  + "(line):" + Thread.currentThread().getStackTrace()[2].getLineNumber());
        if (!initialized) {
            Log.e(TAG, "recognize() called before init")
            return "9/9/9/9/9/9/9/9/9/9 w - - 0 1"
        }

        val grid = BoardConfig.getGrid() ?: return "9/9/9/9/9/9/9/9/9/9 w - - 0 1"
        val rows = mutableListOf<String>()

        for (r in 0 until 10) {
            var rowText = ""
            var emptyCount = 0
            
            for (c in 0 until 9) {
                val cell = cropCell(bitmap, grid, r, c)
                val label = classify(cell, r, c, useFP32) // 增加 r, c
                val piece = labelToFenChar(label)

                // 打印每一格的识别情况，方便你校对
                // Log.d("BoardCheck", "Row $r, Col $c -> $label")

                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        rowText += emptyCount.toString()
                        emptyCount = 0
                    }
                    rowText += piece
                }
            }
            if (emptyCount > 0) {
                rowText += emptyCount.toString()
            }
            rows.add(rowText)
        }

        val fenBoard = rows.joinToString("/")
        // 根据你的需求，这里可以动态判断当前该谁走，暂时默认为红方(w)
        return "$fenBoard w - - 0 1"
    }

    private fun classify(cell: Bitmap, r: Int, c: Int, useFP32: Boolean): String {
        val interpreter = (if (useFP32) interpreterFP32 else interpreterINT8) ?: return "empty"
        val imageProcessor = if (useFP32) imageProcessorForFP32 else imageProcessorForINT8

        // 1. 预处理
        val tensorImage = TensorImage.fromBitmap(cell)
                    // 打印出图片尺寸
        Log.d(TAG, "Image size: ${tensorImage.width} x ${tensorImage.height}")
        val processed = imageProcessor.process(tensorImage)
        // 打印出处理后图像的尺寸
        Log.d(TAG, "Processed image size: ${processed.width} x ${processed.height}")


        // 2. 准备输出容器 (根据模型选择不同的处理逻辑)
        val maxIdx: Int
        val confidence: Float

        if (useFP32) {
            // FP32 逻辑
            val output = Array(1) { FloatArray(labels.size) }
            val start = android.os.SystemClock.elapsedRealtimeNanos()
            interpreter.run(processed.buffer, output)
            val cost = (android.os.SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
            //Log.d("AI-Benchmark", "FP32 Inference cost: ${cost}ms")
            val scores = output[0]
            maxIdx = scores.indices.maxByOrNull { scores[it] } ?: -1
            confidence = if (maxIdx != -1) scores[maxIdx] else 0f
            Log.d("AI-Benchmark", "Model: FP32 | Inference cost: ${cost}ms | Predicted: ${labels[maxIdx]} | Confidence: ${confidence}")

        } else {
            // INT8 逻辑
            val output = Array(1) { ByteArray(labels.size) }
            val start = android.os.SystemClock.elapsedRealtimeNanos()
            interpreter.run(processed.buffer, output)
            val cost = (android.os.SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
            //Log.d("AI-Benchmark", "INT8 Inference cost: ${cost}ms")

            // 将 Byte (-128~127) 映射回无符号概率 (0~255)
            val scores = output[0]
            maxIdx = scores.indices.maxByOrNull { scores[it].toInt() and 0xFF } ?: -1
            // 注意：INT8 的 0.6 对应量化后的值大约是 255 * 0.6 ≈ 153
            confidence = if (maxIdx != -1) (scores[maxIdx].toInt() and 0xFF) / 255.0f else 0f
            Log.d("AI-Benchmark", "Model: INT8 | Inference cost: ${cost}ms | Predicted: ${labels[maxIdx]} | Confidence: ${confidence}")

        }

        // 3. 结果返回
        if (maxIdx == -1 || confidence < 0.6f) return "empty"
        return labels[maxIdx]
    }


    /**
     * ★ 修改 2：同步裁剪逻辑，确保与 SampleCollector 的 cropSize 计算完全一致
     */
    private fun cropCell(
        bm: Bitmap,
        grid: AutoBoardLocator.Grid,
        r: Int,
        c: Int
    ): Bitmap {
        val cx = grid.xLines[c]
        val cy = grid.yLines[r]
        
        // 使用与 SampleCollector 一致的 0.92f 比例
        val cellW = (grid.xLines[1] - grid.xLines[0])
        val cropSize = (cellW * 0.92f).toInt()
        val half = cropSize / 2

        // 使用 coerceIn 确保不越界，且大小固定为 cropSize
        val left = (cx - half).coerceIn(0, bm.width - cropSize)
        val top = (cy - half).coerceIn(0, bm.height - cropSize)

        return Bitmap.createBitmap(bm, left, top, cropSize, cropSize)
    }

/**
     * label → FEN
     * 必须涵盖 labels.txt 中的所有有效棋子
     */
    private fun labelToFenChar(label: String): Char? =
        when (label) {
            "black_jiang" -> 'k'
            "black_ju"    -> 'r'
            "black_ma"    -> 'n'
            "black_pao"   -> 'c'
            "black_shi"   -> 'a'
            "black_xiang" -> 'b'
            "black_zu"    -> 'p'
            "red_shuai"   -> 'K'
            "red_ju"      -> 'R'
            "red_ma"      -> 'N'
            "red_pao"     -> 'C'
            "red_shi"     -> 'A'
            "red_xiang"   -> 'B'
            "red_bing"    -> 'P'
            "empty"       -> null  // 模型认为这是空格，对应 FEN 里的数字增加
            else          -> null
        }
}