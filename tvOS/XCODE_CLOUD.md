# XBTV：Xcode Cloud → TestFlight

## 仓库中已配置

- 工程：`tvOS/XBTV.xcodeproj`，共享 scheme：`XBTV`，Archive：Release / tvOS。
- Bundle ID：`com.reilee.xbtv.appletv`；自动签名。首次设置时需选择实际 Apple Developer 团队。
- `ci_scripts/ci_post_clone.sh`：运行 Core 包的全部离线 XCTest，失败则停止构建。
- `ci_scripts/ci_pre_xcodebuild.sh`：用 `CI_BUILD_NUMBER` 设置 numeric CFBundleVersion，
  使用 `CI_COMMIT` 写入 GitCommit；marketing version 保持 `1.0.0`。
- `TestFlight/WhatToTest.zh-Hans.txt` / `WhatToTest.en-US.txt`：自动填充测试说明。
- 工程、脚本和测试说明均已包含在仓库；云端不需要 XcodeGen、Python 或独立上传 API Key。
- 公共 Swift Package 依赖通过 SPM 拉取；锁文件在仓库中。

这些文件准备好构建，但不会自行创建或启用 Apple 服务器上的 workflow。
首次连接须在 Xcode 中完成，之后可在 App Store Connect 管理。

## Apple 端工作流设置

1. 在 Xcode 中打开 `tvOS/XBTV.xcodeproj`，为 XBTV target 选择开发团队，保留自动签名。
2. Product → Xcode Cloud → Create Workflow。选择 XBTV 产品与同一开发团队。
3. 将 Xcode Cloud 连接到 `reilee/xbtv`。如果需要新授权，只授予该仓库访问权限。
4. 创建或确认 Bundle ID 匹配的 App Store Connect 记录（平台 Apple TV / tvOS）。
5. 工作流名称：`XBTV · TestFlight`，Restrict Editing 开启。
6. Environment：最新正式版 Xcode（支持 Swift 6 / tvOS 18，最低 Xcode 16），Clean 开启。
7. Start Conditions：Branch Changes → `main`，只匹配 `tvOS/` 下的变更；不配置定时触发。
8. Actions：Archive → tvOS → scheme `XBTV` → Release。
   Deployment Preparation 选 `TestFlight and App Store`，保留未来外部测试的可能性。
9. 在 TestFlight 创建内部组 `XBTV Internal`，将本人/现有内部测试者加入该组。
10. Post-Actions：TestFlight Internal Testing → tvOS → 指向 Archive 产物 → `XBTV Internal`。
    必须实际选择测试组后保存，不能只开启组的“自动分发”选项。
11. 保存工作流，手动启动一次 `main` 构建，确认完整发布链。

默认只向内部测试组分发。外部测试需另行配置并经过 Beta App Review，
不会因这个 workflow 自动提交 App Store 正式发布。

## 日常发布

修改 tvOS 代码并推送到 `main` → 离线测试 → Archive/签名 → 上传 App Store Connect
→ Apple 处理 → TestFlight 内部组分发。

`CI_BUILD_NUMBER`、`CI_COMMIT` 是 Xcode Cloud 自带变量，不需要手工添加。
`MARKETING_VERSION` 在 `project.yml` 中维护；修改后本地重新生成工程并提交。
Cloud build number 自动递增，不需要为每次提交修改营销版本或在构建中推回 Git。

## 验证与排障

必须分别确认以下状态，不能用本地编译或 Git push 代替：

- Cloud build 对应期望的 Git commit。
- `ci_post_clone` 的 XCTest 通过，`ci_pre_xcodebuild` 完成构建号更新。
- Archive 和签名/分发完成。
- App Store Connect build processing 完成。
- TestFlight 中 tvOS build 可见，并已分配到内部组。
- 测试者在 Apple TV 的 TestFlight 上看到并安装。

脚本未运行：检查其位于 `tvOS/ci_scripts` 而非仓库根目录，且 Git mode 为 100755。
缺少签名团队：在 Xcode 选团队并保存，对应变更同步到 `project.yml`，再提交。
包依赖失败：检查 Cloud 的公共依赖下载与 SPM 锁文件，不要把网络视频可用性当作构建门禁。
Build 成功但没有 TestFlight：检查是否存在 Archive 动作、正确的 Deployment Preparation
及绑定内部测试组的 TestFlight post-action。

## 官方参考

- [配置首次工作流](https://developer.apple.com/documentation/xcode/configuring-your-first-xcode-cloud-workflow)
- [分发工作流](https://developer.apple.com/documentation/xcode/creating-a-workflow-that-builds-your-app-for-distribution)
- [自定义构建脚本位置](https://developer.apple.com/documentation/xcode/writing-custom-build-scripts)
- [自动测试说明](https://developer.apple.com/documentation/xcode/including-notes-for-testers-with-a-beta-release-of-your-app)

## 本次配置状态（2026-10-09）

仓库准备完成；post-clone 的 8 项测试通过，脚本语法检查通过，
隔离副本验证 Cloud 构建号 42 和完整 GitCommit 注入通过，Release 未签名归档通过。
这份归档不能直接分发，不代表云端签名或上传已经完成。

用户当前不在家，已明确要求跳过账号登录步骤。
因此尚未在 Apple 端创建/启用工作流、选定签名团队、绑定内部测试组或启动云端构建。
后续登录 Xcode / App Store Connect，按上述工作流参数完成一次连接即可。
