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
import androidx.navigation.navArgument
import com.xiaobaotv.app.model.Category
import com.xiaobaotv.app.viewmodel.MainViewModel

// 已迁移到 Navigation Compose：移除手写 sealed Screen 状态机

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val categories by viewModel.categories.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val videoDetail by viewModel.videoDetail.collectAsState()
    val loading by viewModel.loading.collectAsState()

    // 保存选中分类索引，进程重建恢复
    var selectedCategoryIndex by rememberSaveable { mutableStateOf(-1) }

    // 初次或分类列表变化，自动选中第一个并加载
    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && selectedCategoryIndex !in categories.indices) {
            selectedCategoryIndex = 0
            viewModel.setSelectedCategory(categories[0]) // 内部会加载视频
        }
    }

    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
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
                        ?: Category("temp", "当前", "")
                    viewModel.setSelectedVideo(video)
                    viewModel.loadVideoDetail(video.detailUrl)
                    viewModel.setSelectedCategory(cat, fireLoad = false)
                    navController.navigate("detail")
                },
                loading = loading
            )
        }
        composable("detail") {
            // 系统返回键直接返回上一层（NavController 已处理），这里可拦截特殊逻辑
            BackHandler(enabled = false) {}
            val detail = videoDetail
            if (detail == null) {
                LoadingFallback()
            } else {
                VideoDetailScreen(
                    videoDetail = detail,
                    onPlayClick = { playUrl ->
                        navController.navigate("player/${Uri.encode(playUrl)}")
                    }
                )
            }
        }
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

@Composable
fun LoadingFallback() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
