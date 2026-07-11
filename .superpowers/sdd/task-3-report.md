# P0 Task 3 实施报告：日志脱敏、系统备份与 HTTPS 门禁

## Status

- 实现状态：已完成简报范围内实现。
- TDD：已完成 RED → GREEN。
- Focused tests：通过。
- 全量 Debug 单元测试：通过。
- Lint：任务文件未新增 error；项目被 17 个既有范围外 error 阻断。

## RED 证据

命令：

```text
cd native-ui
./gradlew :app:testDebugUnitTest --tests '*SensitiveDataRedactorTest' --tests '*MetroLogInterceptorTest' --tests '*ManifestSecurityPolicyTest'
```

1. 首次运行先发现测试自身把请求正文误写成 `ResponseBody`；修正为 `RequestBody` 后重新执行。
2. 新接口尚不存在时，编译按预期因 `SensitiveDataRedactor` / `HttpsUrlValidator` 缺失而失败。
3. 加入只满足编译的最小桩后再次执行，10 个测试全部按预期失败，确认捕获到以下既有缺陷：
   - HTTP 查询串、headers、普通响应正文与 SSE chunk 正文进入日志；
   - Manifest 允许系统备份；
   - network security config 允许 cleartext；
   - 备份规则文件缺失；
   - 脱敏与纯 HTTPS 校验契约尚未实现。

## GREEN 与实现摘要

- 新增 `SensitiveDataRedactor`：敏感 headers、URL 查询串/fragment、结构化 prompt/response/body/bindArgs/token 等字段脱敏；异常仅保留类型与稳定短码，不保留 message 或堆栈。
- 新增 `HttpsUrlValidator.isAllowed(url)` 纯函数契约：仅接受具有 host、无 user-info 的绝对 HTTPS URL。
- `MetroLogInterceptor`：Debug 仅输出 method、无 query URL、状态码、字节数、耗时、token 计数；不输出 headers、请求/响应正文或 SSE chunk 文本；Release 直接透传。
- `NexaraLogger`：Release 普通日志直接禁用且不落盘；错误仅输出异常类别和短码；Debug 日志先统一脱敏，不输出完整堆栈。
- `MetroLoggingMiddleware`：移除 system prompt 与消息正文，仅保留模型、开关、消息数量和输入字符数。
- Room QueryCallback：移除 SQL 正文与 bindArgs，仅记录操作类型和参数数量。
- `ContextBuilder` / `GraphExtractor`：移除查询原文、模型响应预览、图节点/边名称、文档 ID 与异常 message 等用户内容。
- Manifest/XML：`allowBackup=false`、`usesCleartextTraffic=false`，同时配置 legacy 与 Android 12+ 备份排除规则；network security config 全局禁止 cleartext。

## 验证

### Focused tests

```text
./gradlew :app:testDebugUnitTest --tests '*SensitiveDataRedactorTest' --tests '*MetroLogInterceptorTest' --tests '*ManifestSecurityPolicyTest'
```

结果：`BUILD SUCCESSFUL`，10/10 通过。

### 全量 Debug 单元测试

```text
./gradlew :app:testDebugUnitTest
```

结果：`BUILD SUCCESSFUL`，退出码 0。

### Lint

```text
./gradlew :app:lintDebug
```

结果：项目既有门禁失败，`17 errors, 364 warnings, 21 hints`。17 个 error 均不在本任务修改文件：

- 5 个既有 Compose error：`AgentSessionsScreen.kt`、`RagHomeScreen.kt`、`UserSettingsHomeScreen.kt`、`DocEditorScreen.kt`、`MarkdownText.kt`。
- 12 个既有 `strings.xml` 缺失中文翻译 error。
- 本任务文件无 Lint error；按范围未批量修复既有问题。

### 静态泄露扫描

- `git diff --check`：通过。
- 对任务日志文件扫描 `headers/body/response/chunkText/bindArgs/stacktrace` 输出、完整堆栈 API、用户内容预览和文档 ID 日志：无命中。

## 变更文件

- `native-ui/app/src/main/java/com/promenar/nexara/utils/SensitiveDataRedactor.kt`
- `native-ui/app/src/main/java/com/promenar/nexara/utils/NexaraLogger.kt`
- `native-ui/app/src/main/java/com/promenar/nexara/utils/MetroLogInterceptor.kt`
- `native-ui/app/src/main/java/com/promenar/nexara/data/remote/middleware/MetroLoggingMiddleware.kt`
- `native-ui/app/src/main/java/com/promenar/nexara/NexaraApplication.kt`
- `native-ui/app/src/main/java/com/promenar/nexara/ui/chat/manager/ContextBuilder.kt`
- `native-ui/app/src/main/java/com/promenar/nexara/data/rag/GraphExtractor.kt`
- `native-ui/app/src/main/AndroidManifest.xml`
- `native-ui/app/src/main/res/xml/network_security_config.xml`
- `native-ui/app/src/main/res/xml/backup_rules.xml`
- `native-ui/app/src/main/res/xml/data_extraction_rules.xml`
- `native-ui/app/src/test/java/com/promenar/nexara/utils/SensitiveDataRedactorTest.kt`
- `native-ui/app/src/test/java/com/promenar/nexara/utils/MetroLogInterceptorTest.kt`
- `native-ui/app/src/test/java/com/promenar/nexara/security/ManifestSecurityPolicyTest.kt`
- `.superpowers/sdd/task-3-report.md`

## 自审

- 未修改 SecretCatalog、SecretStore、请求边界 resolver 或生产 KeyStore 强制策略。
- 未读取或输出 `.env`、真实 API Key、keystore、私钥、`local.properties`、用户 Prompt/响应。
- 测试仅使用 `obviously-fake-*` 明显假值。
- 未修改 UI、DB schema、backup codec、registry、handover 或其它治理文档。
- Release 普通日志无用户内容落盘路径；Release 异常日志无 message/堆栈。
- Debug 网络日志不读取普通响应正文用于日志；SSE 只在内存中统计 token 标记，不将 chunk 文本写入日志。

## NEEDS_CONTEXT / Risks

本任务按简报只提供可复用纯 HTTPS 校验器，没有越权修改以下保存/运行入口。主控在后续业务接线时需要决定错误呈现与历史 HTTP 配置迁移策略：

- Provider 主配置保存：`ProviderManager.updateMainProvider()`（当前 `ProviderManager.kt:80-94`）。
- 额外 Provider 保存：`ProviderManager.addProvider()` / `updateExtraProvider()` / `saveExtraProviders()`（当前 `ProviderManager.kt:225-278`）。
- WebDAV 配置保存：`BackupViewModel.updateWebdavConfig()`（当前 `BackupViewModel.kt:63-69`）。
- WebDAV 运行时纵深校验：`BackupRepository.uploadToWebDav()` 与下载路径 URL 拼接（当前 `BackupRepository.kt:94`、`:111`）。

在上述入口接入 `HttpsUrlValidator` 前，Android cleartext 策略会在网络层拒绝 HTTP，但保存时尚不能给用户即时、明确的 HTTPS 校验反馈。

## DIA / HLG

- DIA：本任务改变日志、备份与网络安全行为；按子任务明确边界未更新 README、CHANGELOG、registry 或 handover，留待主控集成收口。
- HLG：按子任务明确边界未追加 handover；无长期规则自动沉淀。
