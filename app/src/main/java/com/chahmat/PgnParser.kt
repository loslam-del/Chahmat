package com.chahmat

data class PgnMove(val number: Int, val white: String, val black: String?)

data class PgnGame(
    val event: String,
    val white: String,
    val black: String,
    val result: String,
    val moves: List<PgnMove>
)

object PgnParser {
    private val tagRegex = Regex("""(?m)^\s*\[([A-Za-z0-9_]+)\s+"(.*?)"\]\s*$""")

    fun parse(source: String): PgnGame {
        val tags = tagRegex.findAll(source).associate { it.groupValues[1] to it.groupValues[2] }
        var body = source.replace(Regex("""(?m)^\s*\[[^\]]*]\s*$"""), " ")
        body = body.replace(Regex("""\{[^}]*}"""), " ")
        body = body.replace(Regex(""";[^\r\n]*"""), " ")
        repeat(20) {
            val cleaned = body.replace(Regex("""\([^()]*\)"""), " ")
            if (cleaned == body) return@repeat
            body = cleaned
        }

        val san = mutableListOf<String>()
        for (raw in body.split(Regex("""\s+""")).filter { it.isNotBlank() }) {
            var token = raw
            if (token.startsWith("$")) continue
            token = token.replace(Regex("""^\d+\.{1,3}"""), "")
            if (token.isBlank()) continue
            if (token in setOf("1-0", "0-1", "1/2-1/2", "*")) break
            if (token.startsWith("$") || token == "e.p." || token == "ep") continue
            san += token
        }

        val moves = mutableListOf<PgnMove>()
        var i = 0
        var moveNumber = 1
        while (i < san.size) {
            val whiteMove = san[i++]
            val blackMove = if (i < san.size) san[i++] else null
            moves += PgnMove(moveNumber++, whiteMove, blackMove)
        }
        return PgnGame(
            event = tags["Event"] ?: "Партия из PGN",
            white = tags["White"] ?: "Белые",
            black = tags["Black"] ?: "Чёрные",
            result = tags["Result"] ?: "*",
            moves = moves
        )
    }
}
