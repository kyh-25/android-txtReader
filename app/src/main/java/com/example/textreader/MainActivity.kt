package com.example.textreader

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.BufferedReader
import java.io.InputStreamReader

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class MainActivity : ComponentActivity() {
    private var lines by mutableStateOf(listOf("파일을 불러오세요"))
    private var currentUri: Uri? = null
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeMode = loadTheme()

        setContent {
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MaterialTheme(colorScheme = if (isDark) darkColorScheme() else lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var startLine by remember { mutableStateOf(0) }

                    LaunchedEffect(lines) {
                        currentUri?.let { startLine = loadPosition(it) }
                    }

                    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        uri?.let { loadFile(it) }
                    }

                    ReaderScreen(
                        lines = lines,
                        startLine = startLine,
                        themeMode = themeMode,
                        onThemeChange = { themeMode = it; saveTheme(it) },
                        onLineChanged = { currentUri?.let { uri -> savePosition(uri, it) } },
                        onOpenClick = { picker.launch(arrayOf("text/plain")) }
                    )
                }
            }
        }
    }

    private fun loadFile(uri: Uri) {
        currentUri = uri
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val reader = BufferedReader(InputStreamReader(input, "UTF-8"))
                lines = reader.readLines()
            }
        } catch (e: Exception) { lines = listOf("오류 발생: ${e.message}") }
    }

    private fun savePosition(uri: Uri, line: Int) = getSharedPreferences("reader", Context.MODE_PRIVATE).edit().putInt(uri.toString(), line).apply()
    private fun loadPosition(uri: Uri): Int = getSharedPreferences("reader", Context.MODE_PRIVATE).getInt(uri.toString(), 0)
    private fun saveTheme(mode: ThemeMode) = getSharedPreferences("reader", Context.MODE_PRIVATE).edit().putString("theme_mode", mode.name).apply()
    private fun loadTheme(): ThemeMode {
        val name = getSharedPreferences("reader", Context.MODE_PRIVATE).getString("theme_mode", ThemeMode.SYSTEM.name)
        return ThemeMode.valueOf(name ?: ThemeMode.SYSTEM.name)
    }
}

@Composable
fun ReaderScreen(
    lines: List<String>,
    startLine: Int,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onLineChanged: (Int) -> Unit,
    onOpenClick: () -> Unit
) {
    var current by rememberSaveable(startLine) { mutableStateOf(startLine) }
    var fontSize by rememberSaveable { mutableStateOf(18f) }
    val listState = rememberLazyListState()
    val scrollBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    LaunchedEffect(current) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem((current - 3).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 상단바 영역
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Button(onClick = onOpenClick) { Text("파일 열기") }
                    IconButton(onClick = {
                        onThemeChange(when(themeMode){
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        })
                    }) {
                        Text(when(themeMode){ ThemeMode.SYSTEM -> "🌓"; ThemeMode.LIGHT -> "☀️"; else -> "🌙" }, fontSize = 20.sp)
                    }
                }
                Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 12f..35f)
            }
        }

        // 본문 및 스크롤바 영역
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    // ⭐ 스크롤바 그리기 로직
                    .drawWithContent {
                        drawContent() // 본문 먼저 그림
                        val firstVisibleIndex = listState.firstVisibleItemIndex
                        val totalItems = listState.layoutInfo.totalItemsCount
                        if (totalItems > 0) {
                            val elementHeight = size.height / totalItems
                            val scrollbarHeight = size.height * (listState.layoutInfo.visibleItemsInfo.size.toFloat() / totalItems)
                            val scrollbarOffsetY = firstVisibleIndex * elementHeight

                            drawRect(
                                color = scrollBarColor,
                                topLeft = Offset(size.width - 8.dp.toPx(), scrollbarOffsetY),
                                size = Size(4.dp.toPx(), scrollbarHeight)
                            )
                        }
                    }
            ) {
                itemsIndexed(lines) { i, line ->
                    Text(
                        text = line,
                        fontSize = fontSize.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (i == current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    )
                }
            }
        }

        // 하단바
        BottomAppBar {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                FilledTonalButton(onClick = { current = (current - 1).coerceAtLeast(0); onLineChanged(current) }) { Text("이전") }
                Text("${current + 1} / ${lines.size}")
                FilledTonalButton(onClick = { current = (current + 1).coerceAtMost(lines.lastIndex); onLineChanged(current) }) { Text("다음") }
            }
        }
    }
}