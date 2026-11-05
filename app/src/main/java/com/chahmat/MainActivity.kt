package com.chahmat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class MoveComparison(val before: ChessPosition, val actual: ChessPosition, val best: ChessPosition, val review: MoveReview)

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
    var comparison by remember { mutableStateOf<MoveComparison?>(null) }
    var comparisonPhase by remember { mutableIntStateOf(1) }
    var hintDialogOpen by remember { mutableStateOf(false) }
    var hintLevel by remember { mutableIntStateOf(0) }
    var pasteDialogOpen by remember { mutableStateOf(false) }
    var pastedPgn by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun importPgn(source: String) {
        try {
            val parsed = PgnParser.parse(source)
            val replay = ChessReplay.build(parsed)
            if (replay.size < 2) error("В тексте не найдены ходы партии")
            importedGame = parsed
            positions = replay
            currentPly = 0
            bestLines = emptyList()
            moveReviews = emptyList()
            analysisStatus = "Партия загружена. Можно проверять ходы."
            selectedTab = 1
            pasteDialogOpen = false
            pastedPgn = ""
        } catch (error: Exception) {
            Toast.makeText(context, error.localizedMessage ?: "Не удалось прочитать PGN", Toast.LENGTH_LONG).show()
        }
    }

    val pgnPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Не удалось открыть файл")
                importPgn(text)
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
                    hintDialogOpen = hintDialogOpen,
                    hintLevel = hintLevel,
                    onHints = { hintLevel = 0; hintDialogOpen = true },
                    onHintLevelChange = { hintLevel = it },
                    onHintDialogClose = { hintDialogOpen = false },
                    analysisStatus = analysisStatus,
                    isAnalyzing = isAnalyzing,
                    onPlyChange = {
                        currentPly = it.coerceIn(0, positions.lastIndex.coerceAtLeast(0))
                        bestLines = emptyList()
                    },
                    onImport = { pgnPicker.launch(arrayOf("*/*")) },
                    onOpenPaste = { pasteDialogOpen = true },
                    onPastePgn = { importPgn(it) },
                    pasteDialogOpen = pasteDialogOpen,
                    pastedPgn = pastedPgn,
                    onPastedPgnChange = { pastedPgn = it },
                    onClosePaste = { pasteDialogOpen = false },
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
                    comparison = comparison,
                    comparisonPhase = comparisonPhase,
                    onComparisonPhaseChange = { comparisonPhase = it },
                    onPlayComparison = {
                        scope.launch {
                            comparisonPhase = 0
                            delay(100)
                            comparisonPhase = 1
                            delay(950)
                            comparisonPhase = 2
                        }
                    },
                    onShowComparison = { review ->
                        val before = positions.getOrNull(review.ply - 1)
                        val actual = positions.getOrNull(review.ply)
                        if (before != null && actual != null) {
                            try {
                                val best = ChessReplay.applySan(before.fen, review.bestSan)
                                comparison = MoveComparison(before, actual, best, review)
                                comparisonPhase = 1
                            } catch (error: Exception) {
                                Toast.makeText(context, "Не удалось построить лучший вариант: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
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
    hintDialogOpen: Boolean,
    hintLevel: Int,
    onHints: () -> Unit,
    onHintLevelChange: (Int) -> Unit,
    onHintDialogClose: () -> Unit,
    analysisStatus: String,
    isAnalyzing: Boolean,
    onPlyChange: (Int) -> Unit,
    onImport: () -> Unit,
    onOpenPaste: () -> Unit,
    onPastePgn: (String) -> Unit,
    pasteDialogOpen: Boolean,
    pastedPgn: String,
    onPastedPgnChange: (String) -> Unit,
    onClosePaste: () -> Unit,
    onBestMoves: () -> Unit,
    onReviewGame: () -> Unit,
    onOpenDemo: () -> Unit,
    demoItems: List<ReviewItem>,
    comparison: MoveComparison?,
    comparisonPhase: Int,
    onComparisonPhaseChange: (Int) -> Unit,
    onPlayComparison: () -> Unit,
    onShowComparison: (MoveReview) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pasteDialogOpen) {
        AlertDialog(
            onDismissRequest = onClosePaste,
            title = { Text("Вставить PGN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Скопируй запись партии с Chess.com и вставь её сюда целиком.")
                    OutlinedTextField(
                        value = pastedPgn,
                        onValueChange = onPastedPgnChange,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 300.dp),
                        placeholder = { Text("[Event ...]\\n1. e4 e5 ...") },
                        minLines = 6,
                        maxLines = 12
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onPastePgn(pastedPgn) }, enabled = pastedPgn.isNotBlank()) {
                    Text("Загрузить партию")
                }
            },
            dismissButton = { TextButton(onClick = onClosePaste) { Text("Отмена") } }
        )
    }
    if (hintDialogOpen) {
        val hints = listOf(
            "Не спеши ходить. Сначала проверь, какие фигуры сейчас атакованы и не оставлены ли они без защиты.",
            "Посмотри на центр и на фигуры соперника: есть ли возможность создать угрозу, улучшить активность фигуры или защитить слабое место?",
            "Проверь кандидаты по очереди: шахи, взятия и прямые угрозы. После каждого варианта спроси себя, какой будет самый сильный ответ соперника."
        )
        AlertDialog(
            onDismissRequest = onHintDialogClose,
            title = { Text("♞ Подсказки") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Здесь нет готового хода — только наводящие вопросы, чтобы ты сам нашёл решение.")
                    if (hintLevel > 0) Text(hints[hintLevel - 1], fontWeight = FontWeight.Medium)
                    Text("Открыто подсказок: $hintLevel из 3", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { if (hintLevel < hints.size) onHintLevelChange(hintLevel + 1) else onHintDialogClose() }) {
                    Text(if (hintLevel < hints.size) "Следующая подсказка" else "Готово")
                }
            },
            dismissButton = { TextButton(onClick = onHintDialogClose) { Text("Закрыть") } }
        )
    }
    if (game == null || positions.isEmpty()) {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Разбор партии", style = MaterialTheme.typography.headlineSmall)
                Text("Импортируйте PGN. Приложение проверит легальность каждого хода, а Stockfish найдёт сильные продолжения.")
                Button(onClick = onOpenPaste, modifier = Modifier.fillMaxWidth()) { Text("Вставить PGN") }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Открыть файл PGN") }
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
            if (comparison == null) {
                ChessBoard(position.fen)
            } else {
                Text("Сравнение ходов · ${comparison.review.moveNumber}. ${comparison.review.side}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Button(onClick = onPlayComparison, modifier = Modifier.fillMaxWidth()) {
                    Text("▶ Авто: показать оба варианта")
                }
                AnimatedChessBoard(
                    fromFen = comparison.before.fen,
                    toFen = if (comparisonPhase == 2) comparison.best.fen else comparison.actual.fen,
                    uci = if (comparisonPhase == 2) comparison.best.playedUci else comparison.actual.playedUci,
                    animationKey = comparisonPhase
                )
                Text(
                    if (comparisonPhase == 2) "Лучший ход: ${comparison.review.bestSan}" else "Сыграно в партии: ${comparison.review.playedSan}",
                    fontWeight = FontWeight.SemiBold
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onComparisonPhaseChange(1) }, modifier = Modifier.weight(1f)) { Text("Как было") }
                    Button(onClick = { onComparisonPhaseChange(2) }, modifier = Modifier.weight(1f)) { Text("Лучший ход") }
                }
            }
            Text(moveLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { onPlyChange(0) }, enabled = safePly > 0) { Text("В начало") }
                OutlinedButton(onClick = { onPlyChange(safePly - 1) }, enabled = safePly > 0) { Text("← Назад") }
                Button(onClick = { onPlyChange(safePly + 1) }, enabled = safePly < positions.lastIndex) { Text("Вперёд →") }
            }
            Text("Позиция ${safePly} / ${positions.lastIndex}", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onHints, enabled = !isAnalyzing, modifier = Modifier.weight(1f)) {
                    Text("♞ Подсказки")
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
                        OutlinedButton(onClick = { onShowComparison(review) }) { Text("▶ Показать: как было / как лучше") }
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
            Button(onClick = onOpenPaste, modifier = Modifier.fillMaxWidth()) { Text("Вставить другой PGN") }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Открыть файл PGN") }
            Text("Stockfish анализирует партии после их завершения. Во время рейтинговой игры подсказки не показываются.", style = MaterialTheme.typography.bodySmall)
        }
    }
}


@Composable
private fun AnimatedChessBoard(fromFen: String, toFen: String, uci: String?, animationKey: Int) {
    val progress = remember(animationKey) { Animatable(0f) }
    LaunchedEffect(animationKey) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 700))
    }
    val targetRows = remember(toFen) { parseFenBoard(toFen) }
    val sourceRows = remember(fromFen) { parseFenBoard(fromFen) }
    val fromCol = uci?.getOrNull(0)?.minus('a')?.coerceIn(0, 7) ?: -1
    val fromRow = uci?.getOrNull(1)?.digitToIntOrNull()?.let { 8 - it } ?: -1
    val toCol = uci?.getOrNull(2)?.minus('a')?.coerceIn(0, 7) ?: -1
    val toRow = uci?.getOrNull(3)?.digitToIntOrNull()?.let { 8 - it } ?: -1
    val movingPiece = if (fromRow in 0..7 && fromCol in 0..7) sourceRows[fromRow][fromCol] else null
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val cellPx = with(androidx.compose.ui.platform.LocalDensity.current) { (maxWidth / 8).toPx() }
        Column(Modifier.fillMaxSize()) {
            targetRows.forEachIndexed { rowIndex, row ->
                Row(Modifier.weight(1f)) {
                    row.forEachIndexed { colIndex, piece ->
                        val isDestination = rowIndex == toRow && colIndex == toCol && movingPiece != null
                        val light = (rowIndex + colIndex) % 2 == 0
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(if (light) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (isDestination) "" else piece?.let { pieceGlyph(it) } ?: "", fontSize = 27.sp)
                        }
                    }
                }
            }
        }
        if (movingPiece != null && fromRow in 0..7 && toRow in 0..7 && fromCol in 0..7 && toCol in 0..7) {
            val p = progress.value
            Box(
                Modifier.offset {
                    IntOffset(
                        ((fromCol + (toCol - fromCol) * p) * cellPx).roundToInt(),
                        ((fromRow + (toRow - fromRow) * p) * cellPx).roundToInt()
                    )
                }.size(maxWidth / 8),
                contentAlignment = Alignment.Center
            ) {
                Text(pieceGlyph(movingPiece), fontSize = 27.sp)
            }
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
