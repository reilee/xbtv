package com.xiaobaotv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.xiaobaotv.app.ui.MainScreen
import com.xiaobaotv.app.viewmodel.MainViewModel

/**
 * 应用主 Activity，作为 Compose 渲染的容器
 */
class MainActivity : ComponentActivity() {
    /** 注入全局共享的 ViewModel */
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 设置深色背景底色，避免切换页面时的闪白
            Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
                MainScreen(viewModel)
            }
        }
    }
}
