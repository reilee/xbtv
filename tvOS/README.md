# 小宝影院 · Apple TV

使用 SwiftUI + AVKit 从原 Android 项目重写的原生 tvOS 应用，最低支持 tvOS 18。
Android 代码保持原样；Apple TV 代码、工程与测试都在此目录。

## 打开与运行

打开 `XBTV.xcodeproj`，选择 **XBTV** scheme 和 Apple TV 模拟器，点击 Run。
真机运行时，在 Signing & Capabilities 中选择你的开发团队；Bundle ID 可按账号需要调整。
工程已提交，不需要安装 XcodeGen 才能打开。

```sh
# 改动 project.yml 后重新生成工程（需要 brew install xcodegen）
xcodegen generate --spec tvOS/project.yml

# 在仓库根目录执行；按本机安装位置设置 DEVELOPER_DIR
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  swift test --package-path tvOS/XBTV/Core

DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  xcodebuild -project tvOS/XBTV.xcodeproj -scheme XBTV \
  -destination 'generic/platform=tvOS Simulator' \
  -derivedDataPath /tmp/XBTV-DerivedData CODE_SIGNING_ALLOWED=NO build

# 可选：实时检查分类 → 详情 → 播放地址 → HLS 清单，以及搜索
DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  swift run --package-path tvOS/XBTV/Core catalog-smoke
```

实时检查需要网站/CDN可用，仅拉取清单，不代表 AVPlayer 解码或真机播放通过。

## 持续集成与 TestFlight

Xcode Cloud 构建脚本、自动构建号和中英文测试说明已配置。
首次连接和工作流参数见 [XCODE_CLOUD.md](XCODE_CLOUD.md)。

### 自动递增版本号

每次创建 Git commit 时，pre-commit 钩子会将补丁版本递增一位（例如 `1.0.0` → `1.0.1`），
并同步更新 `tvOS/project.yml` 与 Xcode 工程。克隆仓库后，在仓库根目录运行
`scripts/install_tvos_version_hook.sh` 安装钩子。版本号更新会加入本次提交。

## 功能与操作

- 顶部原生标签：浏览、搜索、最近观看、设置。
- 电影、电视剧、动漫、综艺、短剧；海报网格和“加载更多”。
- 系统搜索输入，支持遥控器键盘及系统提供的输入方式；短暂防抖并取消旧请求。
- 详情含简介、导演/主演、线路与剧集；返回选集后优先恢复最近播放剧集的焦点。
- 原生 AVPlayerViewController：系统播放/暂停、时间轴、快进、字幕、音轨和 AirPlay。
  字幕/音轨仅在媒体源实际提供时显示；下一集动作放在系统播放器控件中。
- 最近 12 部影片，保存线路、剧集和实际播放进度；继续观看或从头播放。
  距离结尾 30 秒以内的记录再次播放时从头开始。记录仅存储于本设备。
- 首次加载、网络失败、播放失败、空结果均有明确状态；重试播放时重新解析地址。
- 使用方向键移动焦点，选择键进入，返回键返回上一层。
  长按观看记录卡片可移除单条记录，设置中可确认清除全部记录。

## 设计依据

遵循 [Apple 的 tvOS 设计指南](https://developer.apple.com/design/human-interface-guidelines/designing-for-tvos)
和 [AVKit 原生播放器文档](https://developer.apple.com/documentation/avkit/avplayerviewcontroller)：
使用系统标签、导航栈和可聚焦按钮；系统卡片焦点反馈、客厅阅读尺寸与安全边距；
将播放交互交给 AVKit，避免自制遥控器时间轴。简介全文提供独立可滚动页面。
包含 tvOS 分层图标和 Top Shelf 图片（静态资源，未创建动态 Top Shelf 扩展）。

## 实现

- `XBTV/App`：SwiftUI 页面、设备本地记录和 AVKit 播放会话。
- `XBTV/Core`：可独立测试的 Swift Package；SwiftSoup 2.11.3 解析 DOM，URLSession 异步网络。
- 分类/详情选择器继承 Android Jsoup 规则，线路通过目标 pane ID 匹配，避免标签顺序串线。
- 播放页只读取 `player_*` JSON；支持普通、百分号编码、Base64 地址和带签名查询的 m3u8。
  不执行网页脚本，不保存短期播放地址，也不将带签名 URL 写入日志。
- 网络页面请求仅使用站点 HTTPS。`NSAllowsArbitraryLoadsForMedia` 仅兼容网站可能提供的 HTTP 媒体，
  HTML 请求仍要求 HTTPS；没有开启全局 ATS 放行。
- 不支持需要网页 JavaScript 运行、DRM 授权或专用请求头的播放线路；遇到此类线路提示切换。
- 图标可通过 `scripts/generate_assets.py` 重建（Pillow）；应用运行不依赖 Python。

## 验证与待实测

解析/记录测试覆盖列表、搜索结构、封面、分页、线路映射、编码和带签名的 HLS 地址、
非播放地址拒绝、12 条历史上限和续播完成边界。

真机验收：播放一集后退出并继续观看、切换线路、下一集、拖动时间轴、返回时焦点恢复、
VoiceOver、系统搜索键盘、音轨/字幕（媒体提供时）和待机恢复。
开发签名、真机安装和 App Store/TestFlight 分发需要独立完成。
