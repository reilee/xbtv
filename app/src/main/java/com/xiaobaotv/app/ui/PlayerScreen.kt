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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.xiaobaotv.app.ui.player.PlayerHud
import com.xiaobaotv.app.ui.player.PlayerSeekControls
import com.xiaobaotv.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

/**
 * 视频播放屏幕，集成 ExoPlayer 并提供自定义 TV 交互控制
 * @param playUrl 初始播放页面 URL
 * @param viewModel 主 ViewModel
 * @param controllerAutoHideMillis 控制条自动隐藏时间（毫秒）
 */
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

    // 详情（用于获取分集列表）
    val videoDetail by viewModel.videoDetail.collectAsState()
    
    // 自动匹配当前播放地址所属的剧集列表
    val episodes: List<Episode> = remember(videoDetail, currentPlayPageUrl) {
        val detail = videoDetail
        if (detail == null) emptyList() else {
            val line = detail.playLines.firstOrNull { line -> line.episodes.any { it.playUrl == currentPlayPageUrl } }
                ?: detail.playLines.firstOrNull()
            line?.episodes ?: emptyList()
        }
    }

    // 自动记录观看进度
    LaunchedEffect(videoDetail?.id, currentPlayPageUrl, episodes) {
        val detail = videoDetail ?: return@LaunchedEffect
        val line = detail.playLines.firstOrNull { line -> line.episodes.any { it.playUrl == currentPlayPageUrl } }
            ?: detail.playLines.firstOrNull()
        val episode = line?.episodes?.firstOrNull { it.playUrl == currentPlayPageUrl }
            ?: return@LaunchedEffect
        val episodeIndex = line.episodes.indexOfFirst { it.playUrl == currentPlayPageUrl }
        if (episodeIndex >= 0) {
            viewModel.recordRecentWatch(
                detail = detail,
                lineName = line.name,
                episodeIndex = episodeIndex,
                episodeName = episode.name,
                playUrl = currentPlayPageUrl
            )
        }
    }

    // 当前分集索引
    val currentEpisodeIndex by remember(currentPlayPageUrl, episodes) {
        mutableStateOf(episodes.indexOfFirst { it.playUrl == currentPlayPageUrl })
    }

    // 剧集列表可见与选择状态
    var showEpisodeList by remember { mutableStateOf(false) }
    var selectedEpisodeIndex by remember { mutableStateOf(-1) }

    // 播放进度控制条可见与焦点状态
    var showSeekControls by remember { mutableStateOf(false) }
    var seekControlsFocused by remember { mutableStateOf(false) }
    val seekControlsFocusRequester = remember { FocusRequester() }

    // 用户尝试显示分集列表但尚未有数据的标记
    var pendingShowList by remember { mutableStateOf(false) }
    var lastEpisodeListInteractionAt by remember { mutableStateOf(0L) }

    // 当显示剧集列表时默认选中当前分集
    LaunchedEffect(showEpisodeList, currentEpisodeIndex) {
        if (showEpisodeList) {
            selectedEpisodeIndex = currentEpisodeIndex.takeIf { it >= 0 } ?: 0
            lastEpisodeListInteractionAt = System.currentTimeMillis()
        }
    }

    // 剧集列表超时自动隐藏
    LaunchedEffect(showEpisodeList, lastEpisodeListInteractionAt) {
        if (!showEpisodeList || lastEpisodeListInteractionAt == 0L) return@LaunchedEffect
        val snapshotTime = lastEpisodeListInteractionAt
        delay(10_000)
        if (showEpisodeList && snapshotTime == lastEpisodeListInteractionAt) {
            showEpisodeList = false
        }
    }

    // 根据当前播放页解析并加载 m3u8
    LaunchedEffect(currentPlayPageUrl) {
        m3u8Url = viewModel.getPlayUrl(currentPlayPageUrl)
        m3u8Url?.let { real ->
            exoPlayer?.let { p ->
                val item = MediaItem.Builder().setUri(real).setMimeType(MimeTypes.APPLICATION_M3U8).build()
                p.setMediaItem(item)
                p.prepare(); p.playWhenReady = true
            }
        }
    }

    // 初始聚焦到容器以接收按键
    LaunchedEffect(Unit) { kotlinx.coroutines.yield(); runCatching { containerFocusRequester.requestFocus() } }

    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var showSeekHud by remember { mutableStateOf(false) }
    var lastSeekTriggerAt by remember { mutableStateOf(0L) }

    /** 更新当前播放位置 */
    fun updatePos() {
        exoPlayer?.let { p ->
            positionMs = p.currentPosition.coerceAtLeast(0L)
            durationMs = p.duration.takeIf { it > 0 } ?: durationMs
        }
    }

    /** 快进或快退指定时长 */
    fun seekBy(delta: Long) {
        exoPlayer?.let { p ->
            val d = p.duration.takeIf { it > 0 } ?: return
            val newPos = (p.currentPosition + delta).coerceIn(0, d)
            p.seekTo(newPos)
            updatePos()
            showSeekHud = true
            lastSeekTriggerAt = System.currentTimeMillis()
        }
    }

    // 监听快进触发，延迟隐藏 HUD
    LaunchedEffect(lastSeekTriggerAt) {
        if (lastSeekTriggerAt == 0L) return@LaunchedEffect
        val snapshotTime = lastSeekTriggerAt
        kotlinx.coroutines.delay(UiConstants.SEEK_HUD_VISIBLE_MS)
        if (snapshotTime == lastSeekTriggerAt) {
            showSeekHud = false
        }
    }

    /** 切换播放/暂停状态 */
    fun togglePlayPauseInternal() {
        exoPlayer?.let { p -> if (p.isPlaying) p.pause() else p.play(); updatePos() }
    }

    // 定时刷新播放进度
    LaunchedEffect(Unit) { while (true) { updatePos(); delay(UiConstants.HUD_PROGRESS_REFRESH_MS) } }

    /** 播放指定索引的剧集 */
    fun playEpisodeAt(index: Int) {
        if (index !in episodes.indices) return
        val target = episodes[index]
        if (target.playUrl == currentPlayPageUrl) {
            showEpisodeList = false
            showSeekControls = false
            return
        }
        showEpisodeList = false
        showSeekControls = false
        currentPlayPageUrl = target.playUrl
    }

    // 自动弹出剧集列表（如果之前有挂起的显示请求）
    LaunchedEffect(episodes) {
        if (episodes.isNotEmpty() && pendingShowList) {
            pendingShowList = false
            showSeekControls = false
            showEpisodeList = true
            lastEpisodeListInteractionAt = System.currentTimeMillis()
        }
    }

    val episodeListState = rememberLazyListState()
    LaunchedEffect(showEpisodeList, selectedEpisodeIndex) {
        if (showEpisodeList && selectedEpisodeIndex in episodes.indices) {
            episodeListState.scrollToItem(selectedEpisodeIndex)
        }
    }

    LaunchedEffect(showSeekControls) {
        if (showSeekControls) {
            kotlinx.coroutines.yield()
            runCatching { seekControlsFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(containerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event: ComposeKeyEvent ->
                val isUp = event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_UP
                val isDown = event.nativeKeyEvent.action == AndroidKeyEvent.ACTION_DOWN
                if (!isUp && !isDown) return@onPreviewKeyEvent false

                val code = event.nativeKeyEvent.keyCode
                val controllerVisible = playerViewRef?.isControllerFullyVisible == true
                val now = System.currentTimeMillis()

                // 1. 处理返回键：优先关闭自定义 UI 或默认控制界面
                if (code == AndroidKeyEvent.KEYCODE_BACK) {
                    if (showSeekControls || showEpisodeList || controllerVisible) {
                        if (isUp) {
                            when {
                                showSeekControls -> showSeekControls = false
                                showEpisodeList -> showEpisodeList = false
                                controllerVisible -> playerViewRef?.hideController()
                            }
                        }
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }

                // 2. 如果默认控制器正在显示，让它处理除返回键外的其它按键
                if (controllerVisible) return@onPreviewKeyEvent false

                // 3. 处理其它交互按键，拦截 ACTION_DOWN 以防止 ExoPlayer 默认行为（如 Center 键显示控制条）
                return@onPreviewKeyEvent when (code) {
                    AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                        if (isUp) {
                            showSeekControls = false
                            if (!showEpisodeList) {
                                if (episodes.isNotEmpty()) {
                                    showEpisodeList = true
                                    lastEpisodeListInteractionAt = now
                                } else {
                                    pendingShowList = true
                                }
                            } else showEpisodeList = false
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isUp) {
                            showEpisodeList = false
                            if (!showSeekControls) showSeekControls = true
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isUp) {
                            if (showEpisodeList) {
                                selectedEpisodeIndex = (selectedEpisodeIndex - 1).coerceAtLeast(0)
                                lastEpisodeListInteractionAt = now
                            } else {
                                seekBy(-UiConstants.SEEK_STEP_MS)
                            }
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isUp) {
                            if (showEpisodeList) {
                                selectedEpisodeIndex = (selectedEpisodeIndex + 1).coerceAtMost(episodes.lastIndex)
                                lastEpisodeListInteractionAt = now
                            } else {
                                seekBy(UiConstants.SEEK_STEP_MS)
                            }
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                        if (isUp) {
                            if (showEpisodeList) {
                                lastEpisodeListInteractionAt = now
                                playEpisodeAt(selectedEpisodeIndex)
                            } else {
                                togglePlayPauseInternal()
                            }
                        }
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MENU -> {
                        if (isUp) playerViewRef?.showController()
                        true
                    }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { if (isUp) togglePlayPauseInternal(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY -> { if (isUp && !(exoPlayer?.isPlaying ?: false)) togglePlayPauseInternal(); true }
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE -> { if (isUp && exoPlayer?.isPlaying == true) togglePlayPauseInternal(); true }
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
                        setControllerAutoShow(false)
                        setControllerShowTimeoutMs(controllerAutoHideMillis)
                        keepScreenOn = true
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        isFocusable = false
                        isFocusableInTouchMode = false
                        playerViewRef = this
                        hideController()
                    }
                },
                update = { pv ->
                    playerViewRef = pv
                    if (pv.player !== exo) pv.player = exo
                    pv.useController = true
                    pv.controllerAutoShow = false
                    pv.setControllerShowTimeoutMs(controllerAutoHideMillis)
                },
                modifier = Modifier.fillMaxSize()
            )
        } ?: CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

        // 剧集列表 Overlay
        if (showEpisodeList && episodes.isNotEmpty()) {
            Surface(
                color = Color(0xAA101010),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
            ) {
                LazyRow(
                    state = episodeListState,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(episodes.size) { idx ->
                        val ep = episodes[idx]
                        val selected = idx == selectedEpisodeIndex
                        val playing = idx == currentEpisodeIndex
                        val shape = RoundedCornerShape(4.dp)
                        val scale = if (selected) 1.18f else 1f
                        val bg = if (playing) Color(0xFF454545) else Color(0xFF303030)
                        Column(
                            modifier = Modifier
                                .widthIn(min = 68.dp)
                                .wrapContentHeight()
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .background(bg, shape)
                                .then(if (playing) Modifier.border(1.dp, Color(0xFFFF6B6B), shape) else Modifier)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
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
                                Spacer(Modifier.height(4.dp))
                                Box(modifier = Modifier.height(2.dp).fillMaxWidth().background(Color(0xFFFF6B6B))) {}
                            }
                        }
                    }
                }
            }
        }

        PlayerHud(positionMs = positionMs, durationMs = durationMs, visible = showSeekHud && !showSeekControls)
        PlayerSeekControls(
            positionMs = positionMs,
            durationMs = durationMs,
            visible = showSeekControls,
            focused = seekControlsFocused,
            focusRequester = seekControlsFocusRequester,
            onFocusedChanged = { seekControlsFocused = it }
        )
    }
}
