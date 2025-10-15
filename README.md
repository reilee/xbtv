# XiaoBaoTV App

一个使用 Jetpack Compose 构建的简易视频浏览与播放示例应用。

## 主要特性
- Jetpack Compose UI
- Navigation Compose 路由：`home` -> `detail` -> `player/{playUrl}`
- ExoPlayer (Media3) 播放 M3U8
- 协程 + 简单解析器（`XiaoBaoTVParser`）拉取分类、视频列表与详情
- 内存缓存：播放地址(m3u8) / 视频详情
- Detekt 静态检查（基础未使用与风格规则）

## 模块结构
```
app/
  ui/            组合式界面 (HomeScreen, VideoDetailScreen, PlayerScreen, Navigation)
  viewmodel/     状态与数据加载 (MainViewModel)
  model/         数据模型 (Category, Video, VideoDetail ...)
  network/       解析 / 网络访问 (XiaoBaoTVParser)
```

## 导航说明
| Route | 说明 | 参数 |
|-------|------|------|
| home | 分类 + 搜索 + 视频网格 | 无 |
| detail | 某视频详情 + 播放线路/剧集 | 依赖 ViewModel 当前选中 videoDetail |
| player/{playUrl} | 视频播放 | playUrl (经 Uri.encode) |

状态由 `MainViewModel` 共享：分类 / 视频列表 / 当前视频详情 / 播放地址缓存。

## 构建运行
```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 静态检查
```bash
./gradlew :app:detekt
```
报告路径：`app/build/reports/detekt/`

## Lint（含未使用资源分析）
```bash
./gradlew :app:lintVitalRelease
```
报告路径：`app/build/reports/lint-results-vital-release.html`

## 可改进方向
- 增加分页加载与下拉刷新
- 增加 Room 缓存与离线能力
- 增加单元/仪表测试（当前按需未启用）
- 更细粒度错误状态与重试 UI

## License
内部示例项目，未指定开源协议。

