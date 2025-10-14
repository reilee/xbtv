package com.xiaobaotv.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaobaotv.app.model.*
import com.xiaobaotv.app.network.XiaoBaoTVParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val parser = XiaoBaoTVParser()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos

    private val _videoDetail = MutableStateFlow<VideoDetail?>(null)
    val videoDetail: StateFlow<VideoDetail?> = _videoDetail

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _categories.value = parser.getCategories()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadVideoList(categoryUrl: String, page: Int = 1) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _videos.value = parser.getVideoList(categoryUrl, page)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun searchVideos(keyword: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _videos.value = parser.searchVideos(keyword)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadVideoDetail(detailUrl: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _videoDetail.value = parser.getVideoDetail(detailUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun getPlayUrl(playUrl: String): String {
        return parser.getPlayUrl(playUrl)
    }
}