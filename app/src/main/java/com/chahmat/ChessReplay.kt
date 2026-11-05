package com.chahmat

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList

data class ChessPosition(
    val ply: Int,
    val fen: String,
    val lastSan: String? = null,
    val playedUci: String? = null,
    val moveNumber: Int = 0,
    val whiteMoved: Boolean = true
)

object ChessReplay {
    fun build(game: PgnGame): List<ChessPosition> {
        val board = Board()
        val positions = mutableListOf(ChessPosition(ply = 0, fen = board.fen))

        for (gameMove in game.moves) {
            val sanMoves = listOfNotNull(gameMove.white, gameMove.black)
            for ((index, rawSan) in sanMoves.withIndex()) {
                val san = rawSan.replace(Regex("[!?]+$"), "")
                val beforeFen = board.fen
                try {
                    val moveList = MoveList(beforeFen)
                    moveList.addSanMove(san, true, true)
                    val move = moveList.firstOrNull()
                        ?: throw IllegalArgumentException("Не удалось распознать ход $san")
                    if (!board.doMove(move, true)) {
                        throw IllegalArgumentException("Недопустимый ход $san")
                    }
                    positions += ChessPosition(
                        ply = positions.size,
                        fen = board.fen,
                        lastSan = san,
                        playedUci = move.toString(),
                        moveNumber = gameMove.number,
                        whiteMoved = index == 0
                    )
                } catch (error: Exception) {
                    throw IllegalArgumentException(
                        "Ошибка в ходе ${gameMove.number}${if (index == 0) "." else "..."} $san: ${error.message ?: "ход не прошёл проверку"}"
                    )
                }
            }
        }
        return positions
    }
}
