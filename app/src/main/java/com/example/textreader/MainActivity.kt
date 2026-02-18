package com.example.textreader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalDensity

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


import androidx.compose.foundation.Canvas

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch


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
    var current by rememberSaveable(startLine, lines) { mutableIntStateOf(startLine) }
    var fontSize by rememberSaveable { mutableFloatStateOf(18f) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val scrollBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)

    // [페이지 이동 다이얼로그 상태]
    var showJumpDialog by remember { mutableStateOf(false) }
    var inputPage by remember { mutableStateOf("") }

    // [페이지 이동 로직]
    val goToNext = { if (current < lines.lastIndex) { current++; onLineChanged(current) } }
    val goToPrev = { if (current > 0) { current--; onLineChanged(current) } }

    LaunchedEffect(current) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem((current - 3).coerceAtLeast(0))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- 상단바 (기존과 동일) ---
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

        // --- 본문 및 스크롤바 영역 ---
        Box(modifier = Modifier.weight(1f)) {
            // 1. 텍스트 리스트
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
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
                            .clickable { current = i; onLineChanged(i) } // 줄 클릭 시 이동
                    )
                }
            }

            // 2. 좌우 터치 존
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxHeight().weight(1f).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { goToPrev() })
                Box(
                    modifier = Modifier.fillMaxHeight().weight(1f).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { goToNext() })
            }

            // 3.  스크롤바
            if (lines.isNotEmpty()) {
                val density = LocalDensity.current // Density 스코프 확보
                val handleHeightDp = 60.dp

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .width(40.dp)
                        .pointerInput(lines.size) {
                            detectDragGestures { change, _ ->
                                val scrollPercentage =
                                    (change.position.y / size.height).coerceIn(0f, 1f)
                                val targetIndex = (lines.size * scrollPercentage).toInt()
                                    .coerceIn(0, lines.lastIndex)
                                coroutineScope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                                change.consume()
                            }
                        }
                        .pointerInput(lines.size) {
                            detectTapGestures { offset ->
                                val scrollPercentage = (offset.y / size.height).coerceIn(0f, 1f)
                                val targetIndex = (lines.size * scrollPercentage).toInt()
                                    .coerceIn(0, lines.lastIndex)
                                coroutineScope.launch { listState.animateScrollToItem(targetIndex) }
                            }
                        }
                ) {
                    val totalItems = lines.size
                    val firstVisibleIndex = listState.firstVisibleItemIndex
                    val firstVisibleOffset = listState.firstVisibleItemScrollOffset

                    // Density 환경에서 픽셀 값 계산
                    val handleHeightPx = with(density) { handleHeightDp.toPx() }
                    val scrollbarWidthPx = with(density) { 8.dp.toPx() }
                    val marginEndPx = with(density) { 15.dp.toPx() }
                    val cornerRadiusPx = with(density) { 10.dp.toPx() }

                    val scrollFraction = if (totalItems > 0) {
                        val itemHeight =
                            listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
                        (firstVisibleIndex.toFloat() + (firstVisibleOffset.toFloat() / itemHeight)) / totalItems
                    } else 0f

                    val maxScrollY = constraints.maxHeight.toFloat() - handleHeightPx
                    val scrollbarOffsetY = (maxScrollY * scrollFraction).coerceIn(0f, maxScrollY)

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            color = scrollBarColor.copy(alpha = 0.7f),
                            topLeft = Offset(size.width - marginEndPx, scrollbarOffsetY),
                            size = Size(scrollbarWidthPx, handleHeightPx),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx)
                        )
                    }
                }
            }
        }

        // --- 하단바 (기존 유지) ---
        BottomAppBar {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
                FilledTonalButton(onClick = goToPrev) { Text("이전") }
                TextButton(onClick = { inputPage = (current + 1).toString(); showJumpDialog = true }) {
                    Text("${current + 1} / ${lines.size}")
                }
                FilledTonalButton(onClick = goToNext) { Text("다음") }
            }
        }
    }

    // --- 페이지 이동 다이얼로그 (기존 유지) ---
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("페이지 이동") },
            text = {
                OutlinedTextField(
                    value = inputPage,
                    onValueChange = { if (it.all { c -> c.isDigit() }) inputPage = it },
                    label = { Text("이동할 라인 (1~${lines.size})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val page = inputPage.toIntOrNull()
                    if (page != null && page in 1..lines.size) {
                        current = page - 1
                        onLineChanged(current)
                        showJumpDialog = false
                    }
                }) { Text("이동") }
            }
        )
    }
}