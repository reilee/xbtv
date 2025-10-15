package com.xiaobaotv.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaobaotv.app.model.Category
import com.xiaobaotv.app.model.Video
import com.xiaobaotv.app.model.VideoDetail
import com.xiaobaotv.app.network.XiaoBaoTVParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val parser = XiaoBaoTVParser()

    // 播放地址缓存,key=原始播放页url,value=解析后的m3u8
    private val playUrlCache = mutableMapOf<String, String>()

    // 详情缓存
    private val detailCache = mutableMapOf<String, VideoDetail>()
    private var currentDetailUrl: String? = null
    private var detailJob: Job? = null

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    val videos: StateFlow<List<Video>> = _videos

    private val _videoDetail = MutableStateFlow<VideoDetail?>(null)
    val videoDetail: StateFlow<VideoDetail?> = _videoDetail

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    // 新增：当前选中分类与视频（配合 Navigation Compose 跨目的地共享数据）
    private val _selectedCategory = MutableStateFlow<Category?>(null)
    val selectedCategory: StateFlow<Category?> = _selectedCategory
    private val _selectedVideo = MutableStateFlow<Video?>(null)
    val selectedVideo: StateFlow<Video?> = _selectedVideo

    init { loadCategories() }

    fun setSelectedCategory(category: Category?, fireLoad: Boolean = true) {
        _selectedCategory.value = category
        if (category != null && fireLoad) {
            loadVideoList(category.url)
        }
    }

    fun setSelectedVideo(video: Video?) {
        _selectedVideo.value = video
    }

    fun loadCategories() {
        viewModelScope.launch {
            _loading.value = true
            try {
                Log.d("MainViewModel", "loadCategories start")
                _categories.value = parser.getCategories()
                if (_categories.value.isNotEmpty() && _selectedCategory.value == null) {
                    setSelectedCategory(_categories.value.first())
                }
                Log.d("MainViewModel", "loadCategories success size=${_categories.value.size}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadCategories error", e)
            } finally { _loading.value = false }
        }
    }

    fun loadVideoList(categoryUrl: String, page: Int = 1) {
        viewModelScope.launch {
            _loading.value = true
            try {
                Log.d("MainViewModel", "loadVideoList url=$categoryUrl page=$page")
                _videos.value = emptyList()
                _videos.value = parser.getVideoList(categoryUrl, page)
                Log.d("MainViewModel", "loadVideoList result size=${_videos.value.size}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadVideoList error", e)
            } finally { _loading.value = false }
        }
    }

    fun searchVideos(keyword: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                Log.d("MainViewModel", "searchVideos keyword=$keyword")
                _videos.value = parser.searchVideos(keyword)
                Log.d("MainViewModel", "searchVideos result size=${_videos.value.size}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "searchVideos error", e)
            } finally { _loading.value = false }
        }
    }

    fun loadVideoDetail(detailUrl: String) {
        if (currentDetailUrl != detailUrl) {
            currentDetailUrl = detailUrl
            _videoDetail.value = null
        }
        detailCache[detailUrl]?.let { cached ->
            Log.d("MainViewModel", "detail cache hit $detailUrl")
            _videoDetail.value = cached
        }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _loading.value = true
            try {
                Log.d("MainViewModel", "loadVideoDetail $detailUrl")
                val detail = parser.getVideoDetail(detailUrl)
                detailCache[detailUrl] = detail
                if (currentDetailUrl == detailUrl) {
                    _videoDetail.value = detail
                }
                Log.d("MainViewModel", "loadVideoDetail success id=${detail.id}")
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadVideoDetail error", e)
            } finally { _loading.value = false }
        }
    }

    suspend fun getPlayUrl(playUrl: String): String {
        playUrlCache[playUrl]?.let {
            Log.d("MainViewModel", "getPlayUrl cache hit $playUrl")
            return it
        }
        val real = parser.getPlayUrl(playUrl)
        if (real.isNotBlank()) {
            playUrlCache[playUrl] = real
            Log.d("MainViewModel", "getPlayUrl parsed $playUrl -> $real")
        } else {
            Log.w("MainViewModel", "getPlayUrl empty result for $playUrl")
        }
        return real
    }

    fun prefetchPlayUrl(playUrl: String) {
        if (playUrlCache.containsKey(playUrl)) return
        viewModelScope.launch {
            try { getPlayUrl(playUrl) } catch (e: Exception) { Log.e("MainViewModel", "prefetchPlayUrl error", e) }
        }
    }
}