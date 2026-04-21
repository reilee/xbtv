package com.xiaobaotv.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.xiaobaotv.app.viewmodel.MainViewModel

/**
 * 应用的主导航容器，管理首页、详情页和播放页之间的跳转
 */
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val categories by viewModel.categories.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val videoDetail by viewModel.videoDetail.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val recentWatchEntries by viewModel.recentWatchEntries.collectAsState()
    val pendingResumeEntry by viewModel.pendingResumeEntry.collectAsState()

    // 保存选中分类索引，支持进程重建恢复
    var selectedCategoryIndex by rememberSaveable { mutableStateOf(-1) }

    // 初次进入或分类列表加载后，自动选中第一个有效分类
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && (selectedCategoryIndex < 0 || selectedCategoryIndex >= categories.size)) {
            selectedCategoryIndex = 0
            viewModel.setSelectedCategory(categories[0])
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavHost(navController = navController, startDestination = "home") {
        // 首页
        composable("home") {
            HomeScreen(
                isVisible = currentRoute == "home",
                categories = categories,
                selectedCategoryIndex = selectedCategoryIndex,
                onSelectCategory = { idx, cat ->
                    if (idx != selectedCategoryIndex) {
                        selectedCategoryIndex = idx
                        viewModel.setSelectedCategory(cat)
                    }
                },
                onSearch = { keyword -> if (keyword.isNotBlank()) viewModel.searchVideos(keyword) },
                videos = videos,
                onVideoClick = { video ->
                    val cat = categories.getOrNull(selectedCategoryIndex)
                    // 如果在最近观看页面点击，尝试恢复之前的播放进度
                    if (selectedCategoryIndex == 0) {
                        val recent = recentWatchEntries.firstOrNull { it.detailUrl == video.detailUrl }
                            ?: pendingResumeEntry?.takeIf { it.detailUrl == video.detailUrl }
                        if (recent != null) {
                            viewModel.setPendingResumeEntry(recent)
                        }
                    } else {
                        viewModel.setPendingResumeEntry(null)
                    }
                    viewModel.setSelectedVideo(video)
                    viewModel.loadVideoDetail(video.detailUrl)
                    if (cat != null) {
                        viewModel.setSelectedCategory(cat, fireLoad = false)
                    }
                    navController.navigate("detail")
                },
                loading = loading
            )
        }

        // 详情页
        composable("detail") {
            BackHandler(enabled = false) {}
            val detail = videoDetail
            if (detail == null) {
                LoadingFallback()
            } else {
                VideoDetailScreen(
                    videoDetail = detail,
                    resumePlayUrl = pendingResumeEntry?.playUrl,
                    onPlayClick = { resumeEntry ->
                        viewModel.setPendingResumeEntry(resumeEntry)
                        val playUrl = resumeEntry.playUrl
                        navController.navigate("player/${Uri.encode(playUrl)}")
                    }
                )
            }
        }

        // 播放页
        composable(
            route = "player/{playUrl}",
            arguments = listOf(navArgument("playUrl") { type = NavType.StringType })
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("playUrl")
            val playUrl = encoded?.let { Uri.decode(it) }
            if (playUrl == null) {
                LoadingFallback()
            } else {
                PlayerScreen(playUrl = playUrl, viewModel = viewModel)
            }
        }
    }
}

/**
 * 数据加载时的占位视图
 */
@Composable
fun LoadingFallback() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
