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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.xiaobaotv.app.R
import com.xiaobaotv.app.model.Category
import com.xiaobaotv.app.model.Video
import com.xiaobaotv.app.ui.common.tvActivate

/**
 * 应用主屏幕，包含顶部导航、分类切换、搜索及视频列表网格
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    isVisible: Boolean = false,
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
    var focusVideoGridAfterSelection by remember { mutableStateOf(false) }

    val accent = Color(0xFFEB9100)

    val searchFieldFocus = remember { FocusRequester() }
    val categoryFocusRequesters = remember(categories.size) { List(categories.size) { FocusRequester() } }
    val videoFocusRequesters = remember(videos.size) { List(videos.size) { FocusRequester() } }
    val keyboardController = LocalSoftwareKeyboardController.current
    var focusedTabByRemote by remember { mutableStateOf(-1) }

    // 防抖处理分类切换：焦点停留在某个 Tab 150ms 后才触发实际加载
    LaunchedEffect(focusedTabByRemote) {
        if (focusedTabByRemote != -1 && focusedTabByRemote != selectedCategoryIndex) {
            kotlinx.coroutines.delay(150)
            onSelectCategory(focusedTabByRemote, categories[focusedTabByRemote])
        }
    }

    // 搜索模式下拦截返回键以关闭搜索
    BackHandler(enabled = showSearch) {
        showSearch = false
        keyboardController?.hide()
    }

    /** 提交搜索任务 */
    fun submitSearch(keyword: String) {
        val kw = keyword.trim()
        if (kw.isNotBlank()) {
            onSearch(kw)
        }
        showSearch = false
        keyboardController?.hide()
    }

    // 语音识别结果处理器
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull()
            if (!text.isNullOrBlank()) {
                searchText = TextFieldValue(text)
                submitSearch(text)
            }
        }
    }

    // 权限申请处理器
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            isListening = true
            startVoiceInput(voiceLauncher)
        } else isListening = false
    }

    /** 启动语音搜索 */
    fun startVoice() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!granted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) else {
            isListening = true
            startVoiceInput(voiceLauncher)
        }
    }

    // 焦点控制逻辑
    LaunchedEffect(isVisible, showSearch, categories, selectedCategoryIndex) {
        kotlinx.coroutines.yield()
        when {
            showSearch -> {
                runCatching { searchFieldFocus.requestFocus() }
                keyboardController?.show()
            }
            isVisible && categories.isNotEmpty() -> {
                keyboardController?.hide()
                val index = selectedCategoryIndex.coerceIn(0, categoryFocusRequesters.lastIndex.coerceAtLeast(0))
                if (index in categoryFocusRequesters.indices) {
                    runCatching { categoryFocusRequesters[index].requestFocus() }
                }
            }
            else -> keyboardController?.hide()
        }
    }

    // 选中分类后自动将焦点移动到视频列表
    LaunchedEffect(videos, focusVideoGridAfterSelection, isVisible, showSearch) {
        if (!isVisible || showSearch || !focusVideoGridAfterSelection || videos.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.yield()
        if (videoFocusRequesters.isNotEmpty()) {
            runCatching { videoFocusRequesters[0].requestFocus() }
        }
        focusVideoGridAfterSelection = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .focusGroup()
    ) {
        // 顶部导航栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_happy_pig),
                contentDescription = "App 图标",
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.width(8.dp))
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
                        Tab(
                            selected = selected,
                            onClick = {
                                onSelectCategory(index, cat)
                                focusVideoGridAfterSelection = true
                            },
                            selectedContentColor = Color(0xFFEB9100),
                            unselectedContentColor = Color.White.copy(alpha = 0.78f),
                            modifier = Modifier
                                .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                                .then(if (index in categoryFocusRequesters.indices) Modifier.focusRequester(categoryFocusRequesters[index]) else Modifier)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        focusedTabByRemote = index
                                    }
                                }
                                .focusable(),
                            text = {
                                Text(
                                    cat.name,
                                    maxLines = 1,
                                    fontSize = if (selected) 18.sp else 15.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) Color.White else Color.White.copy(alpha = 0.85f)
                                )
                            }
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.width(4.dp))
            
            // 搜索入口
            val searchIconScale by animateFloatAsState(if (searchFocused) 1.15f else 1f, tween(160), label = "searchIconScale")
            val searchBg by animateColorAsState(targetValue = if (searchFocused) Color(0x552A2A2A) else Color.Transparent, animationSpec = tween(160))
            IconButton(
                onClick = { if (!showSearch) showSearch = true else submitSearch(searchText.text) },
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

            // 语音输入入口
            val micBaseScale by animateFloatAsState(if (micFocused) 1.15f else 1f, tween(160), label = "micBaseScale")
            val infinite = rememberInfiniteTransition(label = "micPulse")
            val pulse = if (isListening) infinite.animateFloat(0.9f, 1.1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse").value else 1f
            val micScale = micBaseScale * pulse
            val micColor = when { isListening -> Color(0xFFFF6B6B); micFocused -> Color.White; else -> Color.White.copy(alpha = 0.85f) }
            val micBg by animateColorAsState(targetValue = when { isListening -> Color(0x33FF6B6B); micFocused -> Color(0x553A3A3A); else -> Color.Transparent }, animationSpec = tween(160))
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

        // 搜索栏展开视图
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
                            submitSearch(searchText.text)
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
                keyboardActions = KeyboardActions(onSearch = { submitSearch(searchText.text) }),
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
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color(0xFFEB9100)
            )
        } else Divider(color = Color(0x33222222), thickness = 1.dp)

        Spacer(Modifier.height(8.dp))

        // 视频网格：改为使用 LazyColumn + Row 以获得最稳定的 TV 焦点交互
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val gridColumns = 6
            val spacing = 16.dp
            val totalSpacing = spacing * (gridColumns - 1)
            val cardWidth = (maxWidth - totalSpacing) / gridColumns
            
            val videoRows = remember(videos) { videos.chunked(gridColumns) }

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(videoRows) { rowIndex, rowVideos ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing / 2)
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(spacing)
                    ) {
                        rowVideos.forEachIndexed { colIndex, video ->
                            val globalIndex = rowIndex * gridColumns + colIndex
                            VideoCard(
                                video = video,
                                onClick = { onVideoClick(video) },
                                width = cardWidth,
                                focusRequester = videoFocusRequesters.getOrNull(globalIndex)
                            )
                        }
                        
                        // 补充空白，保持对齐
                        if (rowVideos.size < gridColumns) {
                            repeat(gridColumns - rowVideos.size) {
                                Spacer(modifier = Modifier.width(cardWidth))
                            }
                        }
                    }
                }
            }
            if (loading && videos.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
            }
        }
    }
}

/** 辅助方法：启动系统语音输入界面 */
private fun startVoiceInput(launcher: ActivityResultLauncher<Intent>) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
        putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出要搜索的片名")
    }
    try { launcher.launch(intent) } catch (_: ActivityNotFoundException) {}
}
