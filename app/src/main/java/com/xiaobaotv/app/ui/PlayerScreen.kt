package com.xiaobaotv.app.ui

import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.xiaobaotv.app.model.Episode
import com.xiaobaotv.app.ui.common.UiConstants
import com.xiaobaotv.app.ui.common.consumePointerEvents
import com.xiaobaotv.app.ui.player.PlayerHud
import com.xiaobaotv.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

@Composable
fun PlayerScreen(
    playUrl: String,
    viewModel: MainViewModel,
    controllerAutoHideMillis: Int = 5000
) {
    // 初始播放页 url
    var currentPlayPageUrl by remember { mutableStateOf(playUrl) }
    var m3u8Url by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val containerFocusRequester = remember { FocusRequester() }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    val scope = rememberCoroutineScope()

    // 详情（用于获取分集列表）
    val videoDetail by viewModel.videoDetail.collectAsState()
    // 改进：根据当前播放页 URL 在所有线路里查找对应线路的剧集，否则回退第一条线路
    val episodes: List<Episode> = remember(videoDetail, currentPlayPageUrl) {
        val detail = videoDetail
        if (detail == null) emptyList() else {
            val line = detail.playLines.firstOrNull { line -> line.episodes.any { it.playUrl == currentPlayPageUrl } }
                ?: detail.playLines.firstOrNull()
            line?.episodes ?: emptyList()
        }
    }

    // 当前分集索引
    val currentEpisodeIndex by remember(currentPlayPageUrl, episodes) {
        mutableStateOf(episodes.indexOfFirst { it.playUrl == currentPlayPageUrl })
    }

    // 剧集列表可见与选择状态
    var showEpisodeList by remember { mutableStateOf(false) }
    var selectedEpisodeIndex by remember { mutableStateOf(-1) }

    // 用户尝试显示分集列表但尚未有数据的标记
    var pendingShowList by remember { mutableStateOf(false) }

    // 当显示剧集列表时默认选中当前分集
    LaunchedEffect(showEpisodeList, currentEpisodeIndex) {
        if (showEpisodeList) {
            selectedEpisodeIndex = currentEpisodeIndex.takeIf { it >= 0 } ?: 0
        }
    }

    // 根据当前播��页解析 m3u8
    LaunchedEffect(currentPlayPageUrl) {
        m3u8Url = viewModel.getPlayUrl(currentPlayPageUrl)
        // 播放器已存在则直接切换媒体项
        m3u8Url?.let { real ->
            exoPlayer?.let { p ->
                val item = MediaItem.Builder().setUri(real).setMimeType(MimeTypes.APPLICATION_M3U8).build()
                p.setMediaItem(item)
                p.prepare(); p.playWhenReady = true
            }
        }
    }

    // 初始聚焦
    LaunchedEffect(Unit) { kotlinx.coroutines.yield(); runCatching { containerFocusRequester.requestFocus() } }

    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var showSeekHud by remember { mutableStateOf(false) }
    var lastSeekTriggerAt by remember { mutableStateOf(0L) }

    fun updatePos() {
        exoPlayer?.let { p ->
            positionMs = p.currentPosition.coerceAtLeast(0L)
            durationMs = p.duration.takeIf { it > 0 } ?: durationMs
        }
    }

    fun seekBy(delta: Long) {
        exoPlayer?.let { p ->
            val d = p.duration.takeIf { it > 0 } ?: return
            val newPos = (p.currentPosition + delta).coerceIn(0, d)
            p.seekTo(newPos)
            updatePos()
            // 触发 HUD 显示
            showSeekHud = true
            lastSeekTriggerAt = System.currentTimeMillis()
        }
    }

    // 监听 lastSeekTriggerAt，延迟隐藏 HUD
    LaunchedEffect(lastSeekTriggerAt) {
        if (lastSeekTriggerAt == 0L) return@LaunchedEffect
        val snapshotTime = lastSeekTriggerAt
        kotlinx.coroutines.delay(UiConstants.SEEK_HUD_VISIBLE_MS)
        // 若期间没有新的 seek 触发，隐藏
        if (snapshotTime == lastSeekTriggerAt) {
            showSeekHud = false
        }
    }

    fun togglePlayPauseInternal() {
        exoPlayer?.let { p -> if (p.isPlaying) p.pause() else p.play(); updatePos() }
    }

    // 定时刷新播放进度
    LaunchedEffect(Unit) { while (true) { updatePos(); delay(UiConstants.HUD_PROGRESS_REFRESH_MS) } }

    fun playEpisodeAt(index: Int) {
        if (index !in episodes.indices) return
        val target = episodes[index]
        if (target.playUrl == currentPlayPageUrl) { showEpisodeList = false; return }
        showEpisodeList = false
        currentPlayPageUrl = target.playUrl
    }

    // 当 episodes 由空变为非空且用户之前尝试过显示，则自动显示
    LaunchedEffect(episodes) {
        if (episodes.isNotEmpty() && pendingShowList) {
            pendingShowList = false
            showEpisodeList = true
        }
    }

    // LazyRow 滚动状态，显示时滚动到选中项
    val episodeListState = rememberLazyListState()
    LaunchedEffect(showEpisodeList, selectedEpisodeIndex) {
        if (showEpisodeList && selectedEpisodeIndex in episodes.indices) {
            episodeListState.scrollToItem(selectedEpisodeIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .consumePointerEvents()
            .focusRequester(containerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event: ComposeKeyEvent ->
                // 只处理 KeyUp，避免一次按键触发两次（KeyDown/KeyUp）导致跳两集或双倍 seek
                if (event.nativeKeyEvent.action != AndroidKeyEvent.ACTION_UP) return@onPreviewKeyEvent false
                val code = event.nativeKeyEvent.keyCode
                val controllerVisible = playerViewRef?.isControllerFullyVisible == true
                // Back 键优先关闭列表
                if (code == AndroidKeyEvent.KEYCODE_BACK) {
                    if (showEpisodeList) { showEpisodeList = false; return@onPreviewKeyEvent true }
                    if (controllerVisible) { playerViewRef?.hideController(); return@onPreviewKeyEvent true }
                    return@onPreviewKeyEvent false
                }
                // 上键显示/隐藏分集列表（此时 PlayerView 不再可聚焦，不会触发内置 UI）
                if (code == AndroidKeyEvent.KEYCODE_DPAD_UP) {
                    if (!showEpisodeList) {
                        if (episodes.isNotEmpty()) showEpisodeList = true else pendingShowList = true
                    } else showEpisodeList = false
                    return@onPreviewKeyEvent true
                }
                if (showEpisodeList) {
                    when (code) {
                        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { selectedEpisodeIndex = (selectedEpisodeIndex - 1).coerceAtLeast(0); true }
                        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { selectedEpisodeIndex = (selectedEpisodeIndex + 1).coerceAtMost(episodes.lastIndex); true }
                        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> { playEpisodeAt(selectedEpisodeIndex); true }
                        else -> false
                    }.let { return@onPreviewKeyEvent it }
                }
                if (controllerVisible) return@onPreviewKeyEvent false
                when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-UiConstants.SEEK_STEP_MS); true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(UiConstants.SEEK_STEP_MS); true }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> { togglePlayPauseInternal(); true }
                    AndroidKeyEvent.KEYCODE_MENU -> { playerViewRef?.showController(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlayPauseInternal(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { if (!(exoPlayer?.isPlaying ?: false)) togglePlayPauseInternal(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { if (exoPlayer?.isPlaying == true) togglePlayPauseInternal(); true }
                    else -> false
                }
            }
    ) {
        // 播放器视图
        m3u8Url?.let { realUrl ->
            val exo = remember(context, realUrl) {
                ExoPlayer.Builder(context).build().apply {
                    val mediaItem = MediaItem.Builder().setUri(realUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build()
                    setMediaItem(mediaItem); prepare(); playWhenReady = true
                }
            }
            exoPlayer = exo
            DisposableEffect(Unit) { onDispose { exo.release() } }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exo
                        useController = true
                        setControllerShowTimeoutMs(controllerAutoHideMillis)
                        keepScreenOn = true
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        // 关键：不让 PlayerView 抢焦点，避免 DPAD_UP 触发内部控制条
                        isFocusable = false
                        isFocusableInTouchMode = false
                        playerViewRef = this
                        hideController()
                    }
                },
                update = { pv ->
                    playerViewRef = pv
                    if (pv.player !== exo) pv.player = exo
                    pv.setControllerShowTimeoutMs(controllerAutoHideMillis)
                },
                modifier = Modifier.fillMaxSize()
            )
        } ?: CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

        // 剧集列表 Overlay（不换行，水平滚动）
        if (showEpisodeList && episodes.isNotEmpty()) {
            Surface(
                color = Color(0xAA101010),
                shape = RoundedCornerShape(6.dp),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
            ) {
                LazyRow(
                    state = episodeListState,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(episodes.size) { idx ->
                        val ep = episodes[idx]
                        val selected = idx == selectedEpisodeIndex
                        val playing = idx == currentEpisodeIndex
                        val shape = RoundedCornerShape(4.dp)
                        val bg = when {
                            selected -> Color(0xFFFF6B6B)
                            playing -> Color(0xFF454545)
                            else -> Color(0xFF303030)
                        }
                        Column(
                            modifier = Modifier
                                .widthIn(min = 68.dp)
                                .wrapContentHeight()
                                .background(bg, shape)
                                .then(if (playing && !selected) Modifier.border(1.dp, Color(0xFFFF6B6B), shape) else Modifier)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = ep.name,
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (playing) {
                                // 当前播放指示条（选中时也显示，区分为顶部/底部一条）
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .fillMaxWidth()
                                        .background(Color(0xFFFF6B6B))
                                ) {}
                            }
                        }
                    }
                }
            }
        }

        PlayerHud(positionMs = positionMs, durationMs = durationMs, visible = showSeekHud)
    }
}
