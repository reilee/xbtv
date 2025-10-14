package com.xiaobaotv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.xiaobaotv.app.model.*
import com.xiaobaotv.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

// ExoPlayer相关
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.ui.PlayerView
import android.widget.FrameLayout

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val categories by viewModel.categories.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val videoDetail by viewModel.videoDetail.collectAsState()
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    when (val screen = currentScreen) {
        is Screen.Home -> HomeScreen(
            categories = categories,
            onCategoryClick = { category ->
                viewModel.loadVideoList(category.url)
                currentScreen = Screen.VideoList(category)
            },
            onSearch = { keyword ->
                viewModel.searchVideos(keyword)
                currentScreen = Screen.Search(keyword)
            }
        )
        is Screen.VideoList -> VideoListScreen(
            videos = videos,
            category = screen.category,
            onVideoClick = { video ->
                viewModel.loadVideoDetail(video.detailUrl)
                currentScreen = Screen.Detail(video, screen.category)
            },
            onBack = { currentScreen = Screen.Home }
        )
        is Screen.Detail -> videoDetail?.let { detail ->
            VideoDetailScreen(
                videoDetail = detail,
                onPlayClick = { playUrl ->
                    currentScreen = Screen.Player(playUrl, screen.video, screen.category)
                },
                onBack = { currentScreen = Screen.VideoList(screen.category) }
            )
        }
        is Screen.Player -> PlayerScreen(
            playUrl = screen.playUrl,
            viewModel = viewModel,
            onBack = { currentScreen = Screen.Detail(screen.video, screen.category) }
        )
        is Screen.Search -> {
            val searchCategory = Category("search", "搜索: ${screen.keyword}", "")
            VideoListScreen(
                videos = videos,
                category = searchCategory,
                onVideoClick = { video ->
                    viewModel.loadVideoDetail(video.detailUrl)
                    currentScreen = Screen.Detail(video, searchCategory)
                },
                onBack = { currentScreen = Screen.Home }
            )
        }
    }
}

sealed class Screen {
    object Home : Screen()
    data class VideoList(val category: Category) : Screen()
    data class Detail(val video: Video, val category: Category) : Screen()
    data class Player(val playUrl: String, val video: Video, val category: Category) : Screen()
    data class Search(val keyword: String) : Screen()
}

@Composable
fun HomeScreen(
    categories: List<Category>,
    onCategoryClick: (Category) -> Unit,
    onSearch: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(48.dp)
    ) {
        Text(
            text = "小宝影院",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        LazyColumn {
            items(categories) { category ->
                CategoryCard(
                    category = category,
                    onClick = { onCategoryClick(category) }
                )
            }
        }
    }
}

@Composable
fun CategoryCard(category: Category, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF3A3A3A) else Color(0xFF2A2A2A)
        )
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            )
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
    }
}

@Composable
fun VideoListScreen(
    videos: List<Video>,
    category: Category,
    onVideoClick: (Video) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(48.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Button(onClick = onBack) {
                Text("返回")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(videos) { video ->
                VideoCard(video = video, onClick = { onVideoClick(video) })
            }
        }
    }
}

@Composable
fun VideoCard(video: Video, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .width(280.dp)
            .onFocusChanged { isFocused = it.isFocused },
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF3A3A3A) else Color(0xFF2A2A2A)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            AsyncImage(
                model = video.coverUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${video.year} · ${video.status}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查看详情")
            }
        }
    }
}

@Composable
fun VideoDetailScreen(
    videoDetail: VideoDetail,
    onPlayClick: (String) -> Unit,
    onBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        item {
            // 顶部信息与播放按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onBack) { Text("返回") }

                if (videoDetail.playLines.isNotEmpty() &&
                    videoDetail.playLines[0].episodes.isNotEmpty()
                ) {
                    Button(
                        onClick = {
                            onPlayClick(videoDetail.playLines[0].episodes[0].playUrl)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "播放")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("立即播放", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        item {
            // 封面与视频信息
            Row(modifier = Modifier.fillMaxWidth()) {
                Card(
                    modifier = Modifier
                        .width(300.dp)
                        .height(420.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    AsyncImage(
                        model = videoDetail.coverUrl,
                        contentDescription = videoDetail.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = videoDetail.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${videoDetail.year} · ${videoDetail.area}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "导演: ${videoDetail.director}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "主演: ${videoDetail.actors}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "简介:",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = videoDetail.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 24.sp
                    )
                }
            }
        }

        // 播放线路与剧集
        items(videoDetail.playLines) { playLine ->
            Column {
                Text(
                    text = playLine.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    playLine.episodes.forEach { episode ->
                        EpisodeButton(episode = episode, onPlayClick = onPlayClick)
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeButton(episode: Episode, onPlayClick: (String) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Button(
        onClick = { onPlayClick(episode.playUrl) },
        modifier = Modifier
            .width(120.dp)
            .onFocusChanged { isFocused = it.isFocused },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused)
                Color(0xFFFF6B6B)
            else
                Color(0xFF3A3A3A)
        )
    ) {
        Text(
            text = episode.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


// 需要添加 FlowRow 的简单实现（如果你的 Compose 版本不支持）
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }

        var xPosition = 0
        var yPosition = 0
        var maxHeight = 0

        val rows = mutableListOf<List<Placeable>>()
        var currentRow = mutableListOf<Placeable>()

        placeables.forEach { placeable ->
            if (xPosition + placeable.width > constraints.maxWidth) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                xPosition = 0
                yPosition += maxHeight + 8
                maxHeight = 0
            }

            currentRow.add(placeable)
            maxHeight = maxOf(maxHeight, placeable.height)
            xPosition += placeable.width + 8
        }

        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        val totalHeight = yPosition + maxHeight

        layout(constraints.maxWidth, totalHeight) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                var rowHeight = 0

                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + 8
                    rowHeight = maxOf(rowHeight, placeable.height)
                }

                y += rowHeight + 8
            }
        }
    }
}

@Composable
fun PlayerScreen(
    playUrl: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var m3u8Url by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(playUrl) {
        scope.launch {
            m3u8Url = viewModel.getPlayUrl(playUrl)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Button(
            onClick = onBack,
            modifier = Modifier.padding(16.dp)
        ) {
            Text("返回")
        }

        m3u8Url?.let { url ->
            val exoPlayer = remember(context, url) {
                ExoPlayer.Builder(context).build().apply {
                    val mediaItem = MediaItem.Builder()
                        .setUri(url)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .build()
                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true
                }
            }

            DisposableEffect(
                AndroidView(factory = {
                    PlayerView(it).apply {
                        player = exoPlayer
                        useController = true
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp))
            ) {
                onDispose {
                    exoPlayer.release()
                }
            }
        } ?: run {
            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
            )
        }
    }
}
