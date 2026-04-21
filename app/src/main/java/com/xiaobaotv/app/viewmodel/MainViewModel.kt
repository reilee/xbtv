package com.xiaobaotv.app.viewmodel

import android.app.Application
import android.util.Log
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xiaobaotv.app.data.RecentWatchRepository
import com.xiaobaotv.app.model.Category
import com.xiaobaotv.app.model.RecentWatchItem
import com.xiaobaotv.app.model.Video
import com.xiaobaotv.app.model.VideoDetail
import com.xiaobaotv.app.network.XiaoBaoTVParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 应用的主 ViewModel，管理视频数据流、分类及播放记录
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        /** 最近观看虚拟分类 */
        private val RECENT_CATEGORY = Category("__recent__", "最近观看", "")
    }

    private val parser = XiaoBaoTVParser()
    private val recentWatchRepository = RecentWatchRepository(
        application.getSharedPreferences("recent_watch", Context.MODE_PRIVATE)
    )

    /** 播放地址缓存, key=原始播放页url, value=解析后的m3u8地址 */
    private val playUrlCache = mutableMapOf<String, String>()

    /** 分类视频列表缓存, key=分类url, value=视频列表 */
    private val categoryVideoCache = mutableMapOf<String, List<Video>>()

    /** 详情缓存, key=详情页url, value=详情对象 */
    private val detailCache = mutableMapOf<String, VideoDetail>()
    private var currentDetailUrl: String? = null
    private var detailJob: Job? = null

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    /** 暴露给UI的分类列表流 */
    val categories: StateFlow<List<Category>> = _categories

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    /** 暴露给UI的当前视频列表流 */
    val videos: StateFlow<List<Video>> = _videos

    private val _videoDetail = MutableStateFlow<VideoDetail?>(null)
    /** 暴露给UI的视频详情流 */
    val videoDetail: StateFlow<VideoDetail?> = _videoDetail

    private val _loading = MutableStateFlow(false)
    /** 暴露给UI的加载状态流 */
    val loading: StateFlow<Boolean> = _loading

    private val _selectedCategory = MutableStateFlow<Category?>(null)
    /** 当前选中的分类 */
    val selectedCategory: StateFlow<Category?> = _selectedCategory

    private val _selectedVideo = MutableStateFlow<Video?>(null)
    /** 当前选中的视频 */
    val selectedVideo: StateFlow<Video?> = _selectedVideo

    private val _recentWatchEntries = MutableStateFlow<List<RecentWatchItem>>(recentWatchRepository.load())
    /** 所有的最近观看记录 */
    val recentWatchEntries: StateFlow<List<RecentWatchItem>> = _recentWatchEntries

    private val _pendingResumeEntry = MutableStateFlow<RecentWatchItem?>(null)
    /** 待恢复的播放记录（进入详情页时匹配记录） */
    val pendingResumeEntry: StateFlow<RecentWatchItem?> = _pendingResumeEntry

    init {
        loadCategories()
    }

    /**
     * 设置选中的分类
     * @param category 选中的分类
     * @param fireLoad 是否立即触发视频列表加载
     */
    fun setSelectedCategory(category: Category?, fireLoad: Boolean = true) {
        _selectedCategory.value = category
        if (category != null && fireLoad) {
            loadVideoList(category.url)
        } else if (category != null && category.id == RECENT_CATEGORY.id) {
            loadVideoList(category.url)
        }
    }

    /**
     * 设置当前焦点选中的视频
     */
    fun setSelectedVideo(video: Video?) {
        _selectedVideo.value = video
    }

    /**
     * 设置用于自动续播的记录项
     */
    fun setPendingResumeEntry(entry: RecentWatchItem?) {
        _pendingResumeEntry.value = entry
    }

    /**
     * 加载全部分类
     */
    fun loadCategories() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _categories.value = listOf(RECENT_CATEGORY) + parser.getCategories()
                if (_categories.value.isNotEmpty() && _selectedCategory.value == null) {
                    setSelectedCategory(_categories.value.first())
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadCategories error", e)
            } finally { _loading.value = false }
        }
    }

    /**
     * 根据分类URL加载视频列表
     * @param categoryUrl 分类URL，如果是最近观看则传空或特定值
     * @param page 分页页码
     */
    fun loadVideoList(categoryUrl: String, page: Int = 1) {
        viewModelScope.launch {
            _loading.value = true
            try {
                if (categoryUrl.isBlank() || (_selectedCategory.value?.id == RECENT_CATEGORY.id)) {
                    val entries = recentWatchRepository.load()
                    _recentWatchEntries.value = entries
                    _videos.value = entries.map { it.toVideo() }
                } else if (page == 1 && categoryVideoCache.containsKey(categoryUrl)) {
                    _videos.value = categoryVideoCache.getValue(categoryUrl)
                } else {
                    _videos.value = emptyList()
                    val result = parser.getVideoList(categoryUrl, page)
                    _videos.value = result
                    if (page == 1) {
                        categoryVideoCache[categoryUrl] = result
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadVideoList error", e)
            } finally { _loading.value = false }
        }
    }

    /**
     * 搜索视频
     * @param keyword 搜索关键字
     */
    fun searchVideos(keyword: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _videos.value = parser.searchVideos(keyword)
            } catch (e: Exception) {
                Log.e("MainViewModel", "searchVideos error", e)
            } finally { _loading.value = false }
        }
    }

    /**
     * 加载视频详情
     * @param detailUrl 详情页URL
     */
    fun loadVideoDetail(detailUrl: String) {
        var cleanUrl = detailUrl
        if (detailUrl.contains("/vod/play/")) {
            val id = detailUrl.substringAfter("/vod/play/").substringBefore("-")
            cleanUrl = "/vod/detail/$id.html"
        }

        currentDetailUrl = cleanUrl
        _videoDetail.value = null

        detailCache[cleanUrl]?.let { cached ->
            _videoDetail.value = cached
        }
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _loading.value = true
            try {
                val detail = parser.getVideoDetail(cleanUrl)
                detailCache[cleanUrl] = detail
                if (currentDetailUrl == cleanUrl) {
                    _videoDetail.value = detail
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "loadVideoDetail error", e)
            } finally { _loading.value = false }
        }
    }

    /**
     * 获取解析后的真实播放地址（m3u8）
     * @param playUrl 原始播放页面URL
     */
    suspend fun getPlayUrl(playUrl: String): String {
        playUrlCache[playUrl]?.let {
            return it
        }
        val real = parser.getPlayUrl(playUrl)
        if (real.isNotBlank()) {
            playUrlCache[playUrl] = real
        }
        return real
    }

    /**
     * 预加载播放地址
     */
    fun prefetchPlayUrl(playUrl: String) {
        if (playUrlCache.containsKey(playUrl)) return
        viewModelScope.launch {
            try { getPlayUrl(playUrl) } catch (e: Exception) { Log.e("MainViewModel", "prefetchPlayUrl error", e) }
        }
    }

    /**
     * 记录观看历史
     */
    fun recordRecentWatch(
        detail: VideoDetail,
        lineName: String,
        episodeIndex: Int,
        episodeName: String,
        playUrl: String
    ) {
        viewModelScope.launch {
            val recentItem = RecentWatchItem(
                detailUrl = "/vod/detail/${detail.id}.html",
                title = detail.title,
                coverUrl = detail.coverUrl,
                year = detail.year,
                status = buildString {
                    if (lineName.isNotBlank()) append(lineName)
                    if (episodeName.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(episodeName)
                    }
                },
                playUrl = playUrl,
                episodeName = episodeName,
                episodeIndex = episodeIndex,
                lineName = lineName,
                updatedAt = System.currentTimeMillis()
            )
            val updated = recentWatchRepository.upsert(recentItem)
            _recentWatchEntries.value = updated
            if (_selectedCategory.value?.id == RECENT_CATEGORY.id || _selectedCategory.value?.url.isNullOrBlank()) {
                _videos.value = updated.map { it.toVideo() }
            }
        }
    }
}
