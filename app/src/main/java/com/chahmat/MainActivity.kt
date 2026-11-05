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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
    var currentPly by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val pgnPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Не удалось открыть файл")
                val parsed = PgnParser.parse(text)
                if (parsed.moves.isEmpty()) {
                    Toast.makeText(context, "В файле не найдены ходы PGN", Toast.LENGTH_LONG).show()
                } else {
                    importedGame = parsed
                    currentPly = 0
                    selectedTab = 1
                }
            } catch (error: Exception) {
                Toast.makeText(context, "Ошибка чтения PGN: ${error.localizedMessage ?: "неизвестная ошибка"}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val examples = listOf(
        ReviewItem("12. Nf3", "Хороший ход", "Развивает фигуру и помогает контролировать центр.", "—"),
        ReviewItem("15. Nd5?", "Упущено взятие", "Конь ушёл на спокойное поле, хотя можно было забрать пешку.", "Nxe5 — выигрывало пешку"),
        ReviewItem("18. Qe2??", "Серьёзная ошибка", "Ферзь попал под темп соперника и потерял активность.", "Rad1 — безопаснее")
    )

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("♟ Chahmat Coach") }, actions = { Text("MVP 0.2", modifier = Modifier.padding(end = 16.dp)) }) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Text("◉") }, label = { Text("Панель") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Text("♟") }, label = { Text("Разбор") })
                }
            }
        ) { padding ->
            if (selectedTab == 0) OverlayScreen(Modifier.padding(padding))
            else ReviewScreen(
                examples = examples,
                game = importedGame,
                currentPly = currentPly,
                onPlyChange = { currentPly = it },
                onImport = { pgnPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun OverlayScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Плавающая панель", style = MaterialTheme.typography.headlineSmall)
        Text("Компактный режим для отображения часов, номера хода и заметок во время партии.")
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
        Text("Для настоящего плавающего окна Android понадобится разрешение «Поверх других окон».", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReviewScreen(
    examples: List<ReviewItem>,
    game: PgnGame?,
    currentPly: Int,
    onPlyChange: (Int) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (game == null) {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Разбор партии", style = MaterialTheme.typography.headlineSmall)
                Text("Загрузите PGN-файл, чтобы просматривать ходы по порядку.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onImport) { Text("Импортировать PGN") }
                Text("Пример того, как будет выглядеть анализ:", style = MaterialTheme.typography.titleSmall)
            }
            items(examples) { item -> ReviewCard(item) }
        }
        return
    }

    val totalPlies = game.moves.size * 2 - if (game.moves.lastOrNull()?.black == null) 1 else 0
    val safePly = currentPly.coerceIn(0, totalPlies)
    val currentMoveText = if (safePly == 0) {
        "Начальная позиция"
    } else {
        val move = game.moves[(safePly - 1) / 2]
        if (safePly % 2 == 1) "${move.number}. ${move.white}" else "${move.number}... ${move.black.orEmpty()}"
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(game.event, style = MaterialTheme.typography.headlineSmall)
        Text("${game.white} — ${game.black}   ·   ${game.result}", style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ПРОСМОТР ХОДОВ", style = MaterialTheme.typography.labelMedium)
                Text(currentMoveText, style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(
                    progress = { if (totalPlies == 0) 0f else safePly.toFloat() / totalPlies },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = { onPlyChange(0) }, enabled = safePly > 0) { Text("В начало") }
                    OutlinedButton(onClick = { onPlyChange((safePly - 1).coerceAtLeast(0)) }, enabled = safePly > 0) { Text("Назад") }
                    Button(onClick = { onPlyChange((safePly + 1).coerceAtMost(totalPlies)) }, enabled = safePly < totalPlies) { Text("Вперёд") }
                }
            }
        }
        Text("Список ходов", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(game.moves) { move ->
                val whitePly = move.number * 2 - 1
                val blackPly = move.number * 2
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${move.number}.", modifier = Modifier.width(34.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(move.white, Modifier.weight(1f).clickable { onPlyChange(whitePly.coerceAtMost(totalPlies)) })
                    Text(move.black ?: "…", Modifier.weight(1f).clickable(enabled = move.black != null) { onPlyChange(blackPly.coerceAtMost(totalPlies)) })
                }
            }
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Открыть другой PGN") }
        Text("Это просмотр SAN-ходов. Доска с реальными позициями и движок будут добавлены следующим этапом.", style = MaterialTheme.typography.bodySmall)
    }
}

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
