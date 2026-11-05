package com.chahmat

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList
import fr.axl_lvy.stockfish_multiplatform.Score
import fr.axl_lvy.stockfish_multiplatform.getStockfish

data class BestLine(
    val uci: String,
    val san: String,
    val scoreCp: Int?,
    val depth: Int?
)

data class PositionEvaluation(
    val bestMoveUci: String,
    val scoreCp: Int,
    val lines: List<BestLine>
)

data class MoveReview(
    val ply: Int,
    val moveNumber: Int,
    val side: String,
    val playedSan: String,
    val bestSan: String,
    val lossCp: Int,
    val verdict: String
)

object StockfishAnalyzer {
    suspend fun bestLines(fen: String, depth: Int = 15): List<BestLine> {
        val engine = getStockfish()
        engine.setOption("Threads", "2")
        engine.setOption("Hash", "64")
        engine.setOption("MultiPV", "3")
        engine.setPosition(fen = fen)
        val result = engine.search(depth = depth)
        val infos = result.info
            .filter { it.multiPV != null && it.pv.isNotEmpty() }
            .groupBy { it.multiPV ?: 1 }
            .toSortedMap()
            .values
            .mapNotNull { group -> group.maxByOrNull { it.depth ?: 0 } }

        if (infos.isEmpty()) {
            val score = result.info.lastOrNull { it.score != null }?.score
            return listOf(
                BestLine(
                    uci = result.bestMove,
                    san = toSan(fen, result.bestMove),
                    scoreCp = scoreToCp(score),
                    depth = result.info.lastOrNull()?.depth
                )
            )
        }

        return infos.map { info ->
            val uci = info.pv.first()
            BestLine(
                uci = uci,
                san = toSan(fen, uci),
                scoreCp = scoreToCp(info.score),
                depth = info.depth
            )
        }
    }

    suspend fun reviewGame(
        positions: List<ChessPosition>,
        onProgress: (Int, Int) -> Unit
    ): List<MoveReview> {
        if (positions.size < 2) return emptyList()
        val engine = getStockfish()
        engine.setOption("Threads", "2")
        engine.setOption("Hash", "64")
        engine.setOption("MultiPV", "1")

        val evaluations = mutableListOf<PositionEvaluation>()
        positions.forEachIndexed { index, position ->
            engine.setPosition(fen = position.fen)
            val result = engine.search(depth = 10)
            val score = result.info.lastOrNull { it.score != null }?.score
            evaluations += PositionEvaluation(
                bestMoveUci = result.bestMove,
                scoreCp = scoreToCp(score) ?: 0,
                lines = emptyList()
            )
            onProgress(index + 1, positions.size)
        }

        return positions.drop(1).mapIndexed { index, after ->
            val beforeEval = evaluations[index]
            val afterEval = evaluations[index + 1]
            val loss = if (after.playedUci == beforeEval.bestMoveUci) {
                0
            } else {
                (beforeEval.scoreCp + afterEval.scoreCp).coerceAtLeast(0)
            }
            val verdict = when {
                loss < 35 -> "Нормальный ход"
                loss < 90 -> "Неточность"
                loss < 180 -> "Ошибка"
                else -> "Грубая ошибка"
            }
            MoveReview(
                ply = after.ply,
                moveNumber = after.moveNumber,
                side = if (after.whiteMoved) "Белые" else "Чёрные",
                playedSan = after.lastSan ?: "?",
                bestSan = toSan(positions[index].fen, beforeEval.bestMoveUci),
                lossCp = loss,
                verdict = verdict
            )
        }
    }

    private fun toSan(fen: String, uci: String): String {
        return try {
            val board = Board()
            board.loadFromFen(fen)
            val move = Move(uci, board.sideToMove)
            val list = MoveList(fen)
            list.add(move)
            list.toSanArray()?.firstOrNull() ?: uci
        } catch (_: Exception) {
            uci
        }
    }

    private fun scoreToCp(score: Score?): Int? = when (score) {
        is Score.Cp -> score.centipawns
        is Score.Mate -> if (score.moves >= 0) 30000 - score.moves * 100 else -30000 - score.moves * 100
        null -> null
    }
}
