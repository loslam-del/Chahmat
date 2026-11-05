package com.chahmat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReviewItem(val move: String, val label: String, val explanation: String, val alternative: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChahmatApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChahmatApp() {
    var selectedTab by remember { mutableIntStateOf(1) }
    var importedGame by remember { mutableStateOf<PgnGame?>(null) }
    var positions by remember { mutableStateOf<List<ChessPosition>>(emptyList()) }
    var currentPly by remember { mutableIntStateOf(0) }
    var bestLines by remember { mutableStateOf<List<BestLine>>(emptyList()) }
    var moveReviews by remember { mutableStateOf<List<MoveReview>>(emptyList()) }
    var analysisStatus by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pgnPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Не удалось открыть файл")
                val parsed = PgnParser.parse(text)
                val replay = ChessReplay.build(parsed)
                if (replay.size < 2) error("В файле не найдены ходы партии")
                importedGame = parsed
                positions = replay
                currentPly = 0
                bestLines = emptyList()
                moveReviews = emptyList()
                analysisStatus = "Партия загружена. Можно проверять ходы."
                selectedTab = 1
            } catch (error: Exception) {
                Toast.makeText(context, error.localizedMessage ?: "Не удалось прочитать PGN", Toast.LENGTH_LONG).show()
            }
        }
    }

    val examples = listOf(
        ReviewItem("12. Nf3", "Хороший ход", "Развивает фигуру и помогает контролировать центр.", "—"),
        ReviewItem("15. Nd5?", "Упущено взятие", "Конь ушёл на спокойное поле, хотя можно было забрать пешку.", "Nxe5 — пример альтернативы")
    )

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("♟ Chahmat Coach") },
                    actions = { Text("MVP 0.3", modifier = Modifier.padding(end = 16.dp)) }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Text("◉") }, label = { Text("Панель") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Text("♟") }, label = { Text("Разбор") })
                }
            }
        ) { padding ->
            if (selectedTab == 0) {
                OverlayScreen(Modifier.padding(padding))
            } else {
                ReviewScreen(
                    game = importedGame,
                    positions = positions,
                    currentPly = currentPly,
                    bestLines = bestLines,
                    moveReviews = moveReviews,
                    analysisStatus = analysisStatus,
                    isAnalyzing = isAnalyzing,
                    onPlyChange = {
                        currentPly = it.coerceIn(0, positions.lastIndex.coerceAtLeast(0))
                        bestLines = emptyList()
                    },
                    onImport = { pgnPicker.launch(arrayOf("*/*")) },
                    onBestMoves = {
                        val position = positions.getOrNull(currentPly)
                        if (position != null) scope.launch {
                            isAnalyzing = true
                            analysisStatus = "Stockfish ищет лучшие варианты…"
                            try {
                                bestLines = withContext(Dispatchers.Default) {
                                    StockfishAnalyzer.bestLines(position.fen, depth = 15)
                                }
                                analysisStatus = "Найдено ${bestLines.size} лучших вариантов."
                            } catch (error: Exception) {
                                analysisStatus = "Ошибка движка: ${error.localizedMessage ?: "неизвестная ошибка"}"
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    },
                    onReviewGame = {
                        if (positions.size >= 2) scope.launch {
                            isAnalyzing = true
                            moveReviews = emptyList()
                            bestLines = emptyList()
                            try {
                                moveReviews = withContext(Dispatchers.Default) {
                                    StockfishAnalyzer.reviewGame(positions) { done, total ->
                                        analysisStatus = "Проверка позиции $done из $total…"
                                    }
                                }
                                val errors = moveReviews.count { it.lossCp >= 90 }
                                analysisStatus = "Проверка завершена: найдено ${moveReviews.size} ходов, требующих внимания: $errors."
                            } catch (error: Exception) {
                                analysisStatus = "Ошибка анализа: ${error.localizedMessage ?: "неизвестная ошибка"}"
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    },
                    onOpenDemo = { importedGame = null; positions = emptyList(); currentPly = 0 },
                    demoItems = examples,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun OverlayScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Плавающая панель", style = MaterialTheme.typography.headlineSmall)
        Text("Компактный режим для часов, номера хода и заметок.")
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ПРИМЕР ПАНЕЛИ", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Белые  08:42")
                    Text("Чёрные  08:17")
                }
                Text("Ход 15 · Чёрные думают")
                Text("Подсказки движка во время рейтинговой партии выключены.")
            }
        }
        Text("Настоящее плавающее окно Android — отдельный следующий этап.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReviewScreen(
    game: PgnGame?,
    positions: List<ChessPosition>,
    currentPly: Int,
    bestLines: List<BestLine>,
    moveReviews: List<MoveReview>,
    analysisStatus: String,
    isAnalyzing: Boolean,
    onPlyChange: (Int) -> Unit,
    onImport: () -> Unit,
    onBestMoves: () -> Unit,
    onReviewGame: () -> Unit,
    onOpenDemo: () -> Unit,
    demoItems: List<ReviewItem>,
    modifier: Modifier = Modifier
) {
    if (game == null || positions.isEmpty()) {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Разбор партии", style = MaterialTheme.typography.headlineSmall)
                Text("Импортируйте PGN. Приложение проверит легальность каждого хода, а Stockfish найдёт сильные продолжения.")
                Button(onClick = onImport) { Text("Импортировать PGN") }
                Text("Пример интерфейса", style = MaterialTheme.typography.titleMedium)
            }
            items(demoItems) { item -> ReviewCard(item) }
        }
        return
    }

    val safePly = currentPly.coerceIn(0, positions.lastIndex)
    val position = positions[safePly]
    val moveLabel = if (safePly == 0) "Начальная позиция" else {
        val previous = positions[safePly]
        "${previous.moveNumber}${if (previous.whiteMoved) "." else "..."} ${previous.lastSan}"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(game.event, style = MaterialTheme.typography.headlineSmall)
            Text("${game.white} — ${game.black} · ${game.result}", style = MaterialTheme.typography.bodyMedium)
            ChessBoard(position.fen)
            Text(moveLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { onPlyChange(0) }, enabled = safePly > 0) { Text("В начало") }
                OutlinedButton(onClick = { onPlyChange(safePly - 1) }, enabled = safePly > 0) { Text("← Назад") }
                Button(onClick = { onPlyChange(safePly + 1) }, enabled = safePly < positions.lastIndex) { Text("Вперёд →") }
            }
            Text("Позиция ${safePly} / ${positions.lastIndex}", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onBestMoves, enabled = !isAnalyzing, modifier = Modifier.weight(1f)) {
                    Text("Лучшие ходы")
                }
                OutlinedButton(onClick = onReviewGame, enabled = !isAnalyzing, modifier = Modifier.weight(1f)) {
                    Text("Проверить партию")
                }
            }
            if (isAnalyzing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (analysisStatus.isNotBlank()) Text(analysisStatus, style = MaterialTheme.typography.bodySmall)
            if (bestLines.isNotEmpty()) {
                Text("Лучшие продолжения Stockfish", style = MaterialTheme.typography.titleMedium)
            }
        }

        items(bestLines) { line ->
            Card {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(line.san, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(line.scoreCp?.let { formatScore(it) } ?: "—")
                }
            }
        }

        if (moveReviews.isNotEmpty()) {
            item { Text("Ошибки и альтернативы", style = MaterialTheme.typography.titleMedium) }
            items(moveReviews) { review ->
                Card {
                    Column(Modifier.fillMaxWidth().clickable { onPlyChange(review.ply) }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${review.moveNumber}. ${review.side}: ${review.playedSan}", fontWeight = FontWeight.SemiBold)
                        Text(review.verdict, color = if (review.lossCp >= 90) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        Text("Лучше: ${review.bestSan} · потеря оценки примерно ${formatScore(review.lossCp)}")
                        Text("Нажмите, чтобы открыть эту позицию", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Text("Ходы партии", style = MaterialTheme.typography.titleMedium)
            Text("Нажмите на ход, чтобы перейти к позиции после него.", style = MaterialTheme.typography.bodySmall)
        }
        items(positions.drop(1)) { ply ->
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .clickable { onPlyChange(ply.ply) }.padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${ply.moveNumber}${if (ply.whiteMoved) "." else "..."} ${ply.lastSan}")
                Text(if (ply.ply == safePly) "Выбран" else "Открыть", color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Открыть другой PGN") }
            Text("Stockfish анализирует партии после их завершения. Во время рейтинговой игры подсказки не показываются.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChessBoard(fen: String) {
    val rows = remember(fen) { parseFenBoard(fen) }
    Column(Modifier.fillMaxWidth().aspectRatio(1f)) {
        rows.forEachIndexed { rowIndex, row ->
            Row(Modifier.weight(1f)) {
                row.forEachIndexed { colIndex, piece ->
                    val light = (rowIndex + colIndex) % 2 == 0
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(if (light) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(piece?.let { pieceGlyph(it) } ?: "", fontSize = 27.sp)
                    }
                }
            }
        }
    }
}

private fun parseFenBoard(fen: String): List<List<Char?>> {
    return fen.substringBefore(' ').split('/').map { rank ->
        buildList {
            rank.forEach { char ->
                if (char.isDigit()) repeat(char.digitToInt()) { add(null) } else add(char)
            }
        }.take(8)
    }.take(8)
}

private fun pieceGlyph(piece: Char): String = when (piece) {
    'K' -> "♔"; 'Q' -> "♕"; 'R' -> "♖"; 'B' -> "♗"; 'N' -> "♘"; 'P' -> "♙"
    'k' -> "♚"; 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; 'n' -> "♞"; 'p' -> "♟"
    else -> ""
}

private fun formatScore(cp: Int): String = if (cp >= 0) "+%.2f".format(cp / 100.0) else "%.2f".format(cp / 100.0)

@Composable
private fun ReviewCard(item: ReviewItem) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.move, style = MaterialTheme.typography.titleMedium)
            Text(item.label, color = MaterialTheme.colorScheme.primary)
            Text(item.explanation)
            if (item.alternative != "—") Text("Вместо этого: ${item.alternative}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
