package com.chahmat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val examples = listOf(
        ReviewItem("12. Nf3", "Хороший ход", "Развивает фигуру и помогает контролировать центр.", "—"),
        ReviewItem("15. Nd5?", "Упущено взятие", "Конь ушёл на спокойное поле, хотя можно было забрать пешку.", "Nxe5 — выигрывало пешку"),
        ReviewItem("18. Qe2??", "Серьёзная ошибка", "Ферзь попал под темп соперника и потерял активность.", "Rad1 — безопаснее")
    )
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("♟ Chahmat Coach") }, actions = { Text("MVP 0.1", modifier = Modifier.padding(end = 16.dp)) }) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Text("◉") }, label = { Text("Панель") })
                    NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Text("♟") }, label = { Text("Разбор") })
                }
            }
        ) { padding ->
            if (selectedTab == 0) OverlayScreen(Modifier.padding(padding))
            else ReviewScreen(examples, Modifier.padding(padding))
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
                    Text("Белые  08:42"); Text("Чёрные  08:17")
                }
                Text("Ход 15 · Чёрные думают")
                Text("Подсказки движка во время рейтинговой партии выключены.")
            }
        }
        Text("Для настоящего плавающего окна Android понадобится разрешение «Поверх других окон».", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReviewScreen(items: List<ReviewItem>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text("Разбор партии", style = MaterialTheme.typography.headlineSmall)
            Text("Пока это демонстрационные данные. Импорт PGN и анализ Stockfish — следующий этап.")
            Spacer(Modifier.height(8.dp))
            Button(onClick = { }) { Text("Импортировать PGN — скоро") }
        }
        items(items) { item ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.move, style = MaterialTheme.typography.titleMedium)
                    Text(item.label, color = MaterialTheme.colorScheme.primary)
                    Text(item.explanation)
                    if (item.alternative != "—") Text("Вместо этого: ${item.alternative}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
