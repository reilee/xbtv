package com.xiaobaotv.app.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.xiaobaotv.app.R
import com.xiaobaotv.app.model.Category
import com.xiaobaotv.app.model.Video
import com.xiaobaotv.app.ui.common.consumePointerEvents

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    categories: List<Category>,
    selectedCategoryIndex: Int,
    onSelectCategory: (Int, Category) -> Unit,
    onSearch: (String) -> Unit,
    videos: List<Video> = emptyList(),
    onVideoClick: (Video) -> Unit = {},
    loading: Boolean = false
) {
    val context = LocalContext.current
    var searchText by remember { mutableStateOf(TextFieldValue("")) }
    var searchFocused by remember { mutableStateOf(false) }
    var micFocused by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var pendingCloseAfterSearch by remember { mutableStateOf(false) }
    var lastSearchKeyword by remember { mutableStateOf("") }

    val accent = Color(0xFFEB9100)

    val searchFieldFocus = remember { FocusRequester() }
    rememberCoroutineScope()

    BackHandler(enabled = showSearch) { showSearch = false }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull()
            if (!text.isNullOrBlank()) {
                searchText = TextFieldValue(text)
                onSearch(text.trim())
                showSearch = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            isListening = true
            startVoiceInput(voiceLauncher)
        } else isListening = false
    }

    fun startVoice() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) else {
            isListening = true
            startVoiceInput(voiceLauncher)
        }
    }

    LaunchedEffect(loading, pendingCloseAfterSearch, videos) {
        if (!loading && pendingCloseAfterSearch) {
            pendingCloseAfterSearch = false
            if (searchText.text.trim().isNotBlank() && searchText.text.trim() == lastSearchKeyword && videos.isNotEmpty()) {
                showSearch = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .consumePointerEvents()
    ) {
        // 顶部导航栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App 图标
            Image(
                painter = painterResource(id = R.drawable.ic_pig_foreground),
                contentDescription = "App 图标",
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.width(8.dp))
            // 分类 Tab 行
            if (categories.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = selectedCategoryIndex.coerceIn(0, (categories.size - 1).coerceAtLeast(0)),
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        if (selectedCategoryIndex in tabPositions.indices) {
                            TabRowDefaults.Indicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[selectedCategoryIndex])
                                    .height(3.dp),
                                color = Color(0xFFEB9100)
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    categories.forEachIndexed { index, cat ->
                        val selected = index == selectedCategoryIndex
                        val tabBg = if (selected) Color(0xFF2A2A2A) else Color.Transparent
                        Tab(
                            selected = selected,
                            onClick = { onSelectCategory(index, cat) },
                            selectedContentColor = Color(0xFFEB9100),
                            unselectedContentColor = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier
                                .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(tabBg),
                            text = {
                                Text(
                                    cat.name,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) Color(0xFFEB9100) else Color.White.copy(alpha = 0.85f)
                                )
                            }
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(4.dp))
            // 搜索按钮
            val searchIconScale by animateFloatAsState(if (searchFocused) 1.15f else 1f, tween(160), label = "searchIconScale")
            val searchBg by animateColorAsState(
                targetValue = if (searchFocused) Color(0x552A2A2A) else Color.Transparent,
                animationSpec = tween(160)
            )
            IconButton(
                onClick = {
                    if (!showSearch) showSearch = true else {
                        val kw = searchText.text.trim()
                        if (kw.isNotBlank()) {
                            lastSearchKeyword = kw
                            onSearch(kw); pendingCloseAfterSearch = true
                        } else showSearch = false
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer(scaleX = searchIconScale, scaleY = searchIconScale)
                    .shadow(if (searchFocused) 8.dp else 0.dp, CommonButtonShape)
                    .background(searchBg, CommonButtonShape)
                    .then(if (searchFocused) Modifier.border(1.dp, accent, CommonButtonShape) else Modifier)
                    .clip(CommonButtonShape)
                    .onFocusChanged { searchFocused = it.isFocused }
                    .focusable()
            ) { Icon(Icons.Filled.Search, contentDescription = "搜索", tint = Color.White) }
            Spacer(Modifier.width(4.dp))
            // 语音按钮
            val micBaseScale by animateFloatAsState(if (micFocused) 1.15f else 1f, tween(160), label = "micBaseScale2")
            val infinite = rememberInfiniteTransition(label = "micPulse2")
            val pulse = if (isListening) infinite.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse2").value else 1f
            val micScale = micBaseScale * pulse
            val micColor = when {
                isListening -> Color(0xFFFF6B6B)
                micFocused -> Color.White
                else -> Color.White.copy(alpha = 0.85f)
            }
            val micBg by animateColorAsState(
                targetValue = when {
                    isListening -> Color(0x33FF6B6B)
                    micFocused -> Color(0x553A3A3A)
                    else -> Color.Transparent
                },
                animationSpec = tween(160)
            )
            IconButton(
                onClick = { startVoice() },
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer(scaleX = micScale, scaleY = micScale)
                    .shadow(if (micFocused || isListening) 8.dp else 0.dp, CommonButtonShape)
                    .background(micBg, CommonButtonShape)
                    .then(if (micFocused || isListening) Modifier.border(1.dp, if (isListening) Color(0xFFFF6B6B) else accent, CommonButtonShape) else Modifier)
                    .clip(CommonButtonShape)
                    .onFocusChanged { micFocused = it.isFocused }
                    .focusable()
            ) { Icon(Icons.Filled.Mic, contentDescription = if (isListening) "正在监听" else "语音搜索", tint = micColor) }
        }

        // 搜索输入框
        AnimatedVisibility(visible = showSearch, enter = fadeIn(), exit = fadeOut()) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFieldFocus)
                    .onKeyEvent { evt ->
                        if (evt.type == KeyEventType.KeyUp && evt.key == Key.Enter) {
                            val kw = searchText.text.trim()
                            if (kw.isNotBlank()) {
                                lastSearchKeyword = kw
                                onSearch(kw); pendingCloseAfterSearch = true
                            }
                            return@onKeyEvent true
                        }
                        false
                    },
                placeholder = { Text("搜索影片/演员", color = Color.Gray) },
                trailingIcon = {
                    if (searchText.text.isNotBlank()) {
                        IconButton(onClick = { searchText = TextFieldValue("") }) {
                            Text("×", color = Color.White)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val kw = searchText.text.trim(); if (kw.isNotBlank()) { lastSearchKeyword = kw; onSearch(kw); pendingCloseAfterSearch = true }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFEB9100),
                    unfocusedBorderColor = Color(0xFF444444),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFEB9100)
                )
            )
        }

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = Color(0xFFEB9100)
            )
        } else Divider(color = Color(0x33222222), thickness = 1.dp)

        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val gridColumns = 6
            val spacing = 16.dp
            val totalSpacing = spacing * (gridColumns - 1)
            val cardWidth = (maxWidth - totalSpacing) / gridColumns
            LazyVerticalGrid(columns = GridCells.Fixed(gridColumns)) {
                items(videos) { video ->
                    VideoCard(video = video, onClick = { onVideoClick(video) }, width = cardWidth)
                }
            }
            if (loading && videos.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            }
        }
    }
}

private fun startVoiceInput(launcher: ActivityResultLauncher<Intent>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要搜索的片名")
    }
    try { launcher.launch(intent) } catch (_: ActivityNotFoundException) {}
}
