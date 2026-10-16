# Nexara 折叠 N 应用图标

## 目标与边界

用户采用折叠 N 精修稿：白底黑标为主版本，黑底白标为深色版本。交付纯矢量母版，并统一 Android 普通、圆形、自适应和单色主题图标。任何 IDE、ADE、命令行或 CI 通过项目 Gradle 构建时，均从同一母版生成资源。

只拥有 `assets/branding/`、图标构建脚本、应用图标资源、对应测试及文档区域；不纳入工作树中既有模型选择、设置页、字符串、截图及其计划。CHANGELOG 的既有改动由其它工作保留，只暂存图标独立条目。

## 实施顺序

1. 从用户采用稿的三个折面人工重建三条闭合贝塞尔路径，记录来源、采用状态、配色与安全区，保留批准位图。
2. 先建立构建生成器的行为测试，确认缺失功能导致失败；实现纯 JDK/Gradle 生成器，不依赖 Python、Node 或 IDE 插件完成日常构建。
3. 从 SVG 生成前景、独立单色层、日夜背景与颜色、普通/圆形 adaptive XML 和矢量回退；禁止来源目录中的旧图标及覆盖资源。通过 `androidComponents` 的生成资源 API 接入每个变体与资源合并任务。
4. 回渲纯矢量母版、正反色、小尺寸和圆形遮罩；检查留白、分离缝与裁切。
5. 执行生成器正向/负向行为测试、项目 JVM 回归、Lint 及正式签名 Release 构建；核验 APK 实际 manifest、图标资源、日夜变体及单色层。构建串行，不打断其它任务正在运行的 Gradle。
6. 独立审阅构建契约与资源回退风险，主控复核；同步 README、品牌说明与发行验证事实，标准 HLG append，按任务范围提交推送。

## 验收命令

- `python3 scripts/tests/test_launcher_icons.py`
- `native-ui/gradlew -p native-ui --no-daemon :app:generateNexaraLauncherIcons :app:testDebugUnitTest :app:lintDebug :app:assembleRelease`
- `python3 scripts/verify-release-apk.py <apk>`，使用项目登记版本、包名和证书参数。
- 通过 Android SDK `aapt2 dump xmltree` 与资源表回读最终 APK；源码扫描不代替制品验证。

## 回滚

以本任务提交恢复旧图标和构建接线；不改数据库、签名、版本号、权限和用户数据。生成目录仅属于 `app/build/generated/nexaraLauncherIcons/`，不可覆盖其它构建输出。
