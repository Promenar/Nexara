# Nexara 开发与模型目录发布契约

## 授权与范围

2026-09-22，用户在“根据仓库进度审计规划补全修复”的当前 Codex 会话中批准修复计划，并明确：“本项目 PDEC 不走远程调试，直接使用本机的 Android 模拟器”。该项目例外覆盖本机编译、JVM/截图/Lint 验证、Android 模拟器联调和候选 APK 验真。禁止把默认远端策略当作本项目的执行前提。

用户同时授权使用无密钥本机聚合网关 `http://127.0.0.1:1337`，测试模型为 `newapi/deepseek-v4-flash`、`newapi/gemini-3.8-flash`、`newapi/sensenova-6.8-flash-lite`、`openai-chatgpt/gpt-5.6-luna`、`newapi/MiniMax-M3`。模型调用属于既有服务测试，不是远端开发执行；只发送合成测试数据，限制请求次数和输出长度。目录可见性不能替代协议与业务验证。

2026-09-22，用户批准 GitHub Actions + Pages 的独立模型目录方案并要求开始实施。授权包括创建目录更新工作流、设置独立目录签名 Secret、启用 Pages 和发布公开元数据；不包括公开 APK、tag、PR 或 GitHub Release。私钥只经主机工具注入，不能进入模型上下文。

## 平台与入口

- 执行主机：当前本机 macOS arm64；工具位置通过本机 Android SDK 配置、PATH 和 Java 安装发现，不纳管绝对路径。
- 应用：`native-ui/` 的 Gradle 9.5.0 wrapper，Android compile/target SDK 36，min SDK 31；本机已发现 OpenJDK 21。
- Android 产物是 APK，运行目标是 Android ARM64。PDEC v1 不含 Android OS 枚举，因此机器字段使用 Linux arm64 表示内核/ABI 家族；本说明和设备 API/ABI 验证才定义 Android 兼容性，不能据字段声称普通 Linux 可运行 APK。
- 日常构建、单测、截图、Lint、模拟器联调均在本机执行，同一工作树一次只允许一个 Gradle 验证任务。模拟器使用专用 Nexara AVD；API 31、35 与 36 系列分别记录系统版本、ABI、序列号和产物 SHA-256。
- 公开模型目录由 GitHub 标准 Linux x86_64 Runner 构建，通过 Pages artifact 分发；PDEC v1 的部署 target 引用生产该静态产物的 Runner，具体服务地址由 endpoint 定义，不代表 Pages 服务器实际架构。Android 的本机例外保持有效。
- 既有 GitHub Actions 保留为 push 触发的独立 CI；不手动调用远端调试、源码同步或设备服务，不部署家庭自托管执行器。
- 本机网关通过 `adb reverse tcp:1337 tcp:1337` 提供给已核验的专用模拟器；仅测试变体允许所需回环明文流量，正式版不扩大网络安全配置。

## 工具链、产物与资源

源码始终位于当前 Git 工作树，依赖和缓存保持 Gradle/Android SDK 既有主机隔离。构建结果在 `native-ui/**/build/`，测试原始证据在 `artifacts/`，均不提交。`secure_env/` 中 APK 与目录签名材料分别由对应安全签名路径消费，秘密不得回显或纳管。

受控模拟器按任务串行启动，使用有限超时；完成后停止本任务启动的模拟器并移除本任务 adb reverse。不得清理既有 AVD、用户设备或其它服务。日志仅保留合成输入及必要诊断，交付前检查敏感内容。

## 安装、发布与回滚

冷安装和黑盒脚本会卸载或清数据，仅用于明确标识的专用模拟器。用户真实设备只允许另行明确的保数据覆盖安装；不得运行冷安装夹具。候选 APK 通过本机文件交付，不自动部署、创建 tag/PR/GitHub Release 或发布。

PDEC 变更仅新增项目契约，不删除既有入口。回滚可还原契约与关联文档；代码回滚保留数据前进兼容性，不降级或重置真实数据库。测试失败保留原始证据，不自动清除测试数据规避问题。

目录发布路径为 `https://promenar.github.io/Nexara/model-catalog/v1/`。更新失败保留最后有效 Pages 部署；纠错时以更高版本重新签名旧数据，客户端不接受低版本回放。只有目录与公开来源说明进入 Pages artifact。

## 执行证据

执行前使用用户级 remote-development 的 `pdec.py validate --root .` 验证契约，要求 `execution_ready=true`。每次候选记录源码 SHA/工作树 diff、命令、主机工具链、设备版本、退出码、测试统计与 APK 哈希。该契约不会执行命令，也不提供系统级权限隔离。
