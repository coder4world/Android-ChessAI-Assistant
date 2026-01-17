package com.zfdang.chess.utils

import android.util.Log
import com.zfdang.chess.gamelogic.Board
import com.zfdang.chess.gamelogic.PvInfo
import org.petero.droidfish.player.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * PikafishEngine 劫持工具类
 * 优化：支持流式输出 PV，实现丝滑更新
 */
object PikafishEngine {
    private const val TAG = "PikafishEngine"
    private var activePlayer: ComputerPlayer? = null
    private var lastBestMove: String? = null
    private var latch: CountDownLatch? = null
    
    // 异步分析的回调：(moveUCI) -> Unit
    private var updateCallback: ((String) -> Unit)? = null
    private const val HIJACK_SEARCH_ID = 8888 

    fun bindPlayer(player: ComputerPlayer) {
        this.activePlayer = player
    }

    fun stopPreviousSearch() {
        activePlayer?.stopSearch()
    }

    /**
     * 被 GameController 的 notifyPV 转发调用
     */
    // fun onSearchPV(searchId: Int, pvInfos: ArrayList<PvInfo>) {
    //     if (searchId == HIJACK_SEARCH_ID && pvInfos.isNotEmpty()) {
    //         val topPv = pvInfos[0]
    //         if (topPv.pv.isNotEmpty()) {
    //             // 拿到当前深度搜索出的第一个走法
    //             val moveUCI = topPv.pv[0].getUCCIString()
    //             updateCallback?.invoke(moveUCI)
    //         }
    //     }
    // }


    fun onSearchPV(searchId: Int, pvInfos: ArrayList<PvInfo>) {
    if (searchId == HIJACK_SEARCH_ID && pvInfos.isNotEmpty()) {
        val topPv = pvInfos[0]
        // 只有深度达到 10 层以上才更新 UI，过滤掉前几层的低质走法
        if (topPv.depth >= 16 && topPv.pv.isNotEmpty()) {
            val moveUCI = topPv.pv[0].getUCCIString()
            updateCallback?.invoke(moveUCI)
        }
    }
}

    fun startAnalysis(fen: String, onUpdate: (String) -> Unit) {
        this.updateCallback = onUpdate
        val player = activePlayer ?: return

        // 停止之前的分析任务
        player.stopSearch()

        val tempBoard = Board()
        if (!tempBoard.restoreFromFEN(fen)) return

        val sr = SearchRequest.searchRequest(
            HIJACK_SEARCH_ID,
            tempBoard,
            ArrayList(),
            tempBoard, // 当前局面
            null,
            true, // 允许持续输出 PV
            "pikafish",
            1
        )
        player.queueSearchRequest(sr)
    }

    fun onSearchResult(searchId: Int, bestMove: String?): Boolean {
        if (searchId == HIJACK_SEARCH_ID) {
            bestMove?.let { updateCallback?.invoke(it) }
            latch?.countDown()
            return true 
        }
        return false
    }
}