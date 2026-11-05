package com.chahmat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChessReplayTest {
    @Test
    fun replaysLegalMovesAndBuildsPositions() {
        val pgn = """
            [Event "Unit test"]
            [White "White"]
            [Black "Black"]
            [Result "*"]

            1. e4 e5 2. Nf3 Nc6 *
        """.trimIndent()

        val game = PgnParser.parse(pgn)
        val positions = ChessReplay.build(game)

        assertEquals(5, positions.size)
        assertEquals("e4", positions[1].lastSan)
        assertEquals("Nc6", positions.last().lastSan)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnIllegalMove() {
        val pgn = """
            [Event "Illegal move test"]
            [Result "*"]

            1. e4 e5 2. Kf3 *
        """.trimIndent()

        ChessReplay.build(PgnParser.parse(pgn))
    }
}
