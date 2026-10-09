# 本地验证记录

日期：2026-10-09。使用 `/Applications/Xcode.app` 的 Xcode/tvOS 27 SDK。

- Swift Package：8 项 XCTest 全部通过，0 失败。
- tvOS Simulator：generic destination、未签名构建通过。
- Apple TV 真机目标：generic tvOS destination、未签名构建通过；并非真机安装或运行。
- 实时网站检查：电影分类 48 条记录，下一页、详情元数据、2 条线路与剧集、搜索均通过；
  从播放页取得 HLS 地址，CDN 返回 HTTP 200 且清单以 `#EXTM3U` 开头。
- `git diff --check`：通过。

尚未验证实际画面解码、声音、遥控器焦点、VoiceOver 和设备上的观看进度恢复。
本机原先只有 iOS 模拟器。尝试下载并安装 tvOS 27 运行时：下载成功，
注册失败（CoreSimulator 的 Cryptex/Personalization 目录权限，Operation not permitted）。
尝试获取 tvOS 18.5 arm64 运行时：当前 Xcode 下载服务不提供此版本。
没有修改系统目录权限。可在 Xcode Settings → Components 安装可用的 tvOS 运行时后进行 UI 验收。

App 未签名、未上传 TestFlight/App Store，也未推送远程仓库。
运行命令与真机验收清单见 README.md。
