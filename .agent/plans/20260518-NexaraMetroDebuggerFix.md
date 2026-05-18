# 调试桥与超时机制升级实施方案 (2026-05-18)

## 1. 架构设计

以下是 RAG 知识图谱提取超时机制与调试桥日志事件广播的完整数据链路拓扑图：

```mermaid
graph TD
    subgraph 移动端真机 (Android Runtime)
        A[RagViewModel / Doc Indexer] -->|1. 触发提取| B(GraphExtractor)
        B -->|2. sendPromptSync| C{LlmProtocol}
        C -->|3. OpenAIProtocol / GenericOpenAICompat| D[Ktor HttpClient OkHttp]
        D -->|4. 配置: socketTimeout = 120s| E(大模型 API 服务端)
        
        B -.->|发生异常| F[NexaraLogger.logError]
        F -->|5. 格式化并广播| G[Log.d NEXARA_METRO]
    end

    subgraph 开发主机 (Development Host)
        H[desktop: nexara-metro-tui.js] -->|6. adb logcat -s NEXARA_METRO| I(实时捕获事件流)
        I -->|7. ANSI 美化渲染| J[终端大屏彩色看板]
        I -->|ERROR 事件| K[🚨 红色警报 + 堆栈信息可视化]
    end
```

---

## 2. 流程推演

### 2.1 超时根因与解法推演
- **现状**：非流式（Sync）请求用于 KG 抽取时，需要模型一次性生成完整 JSON（包含多节点、多关系），生成耗时通常在 15s~40s。由于未在 Ktor 中显式设置 `socketTimeoutMillis`，底层 OkHttp 引擎使用其硬编码的默认值 **10s**，导致提取时必定在第 10 秒抛出 `SocketTimeoutException`。
- **解法**：在 Ktor `HttpTimeout` 配置中显式设置 `socketTimeoutMillis = 120_000` (120秒)。OkHttp 引擎会自动捕获此设置并调整其套接字读取超时，从而允许大模型在非流式模式下有足够的时间生成并返回数据。

### 2.2 调试桥不完整性推演
- **现状**：`NexaraLogger.logError` 内部仅向普通 Logcat 和磁盘写入错误日志，没有向 `NEXARA_METRO` 输出 `EVENT_START|ERROR|...|EVENT_END` 结构化日志。桌面 TUI 只过滤 `NEXARA_METRO` 标签，因此在提取失败或大模型请求发生致命崩溃时，TUI 面板上完全看不到任何错误详情，造成严重的信息断链。
- **解法**：在 `NexaraLogger.logError` 内部将异常信息转换为 `JSONObject`，然后通过 `Log.d("NEXARA_METRO", "EVENT_START|ERROR|{...}|EVENT_END")` 广播出去。TUI 终端增加 `ERROR` 事件的解析逻辑并用红色高亮显示，完美闭环可观测性。

---

## 3. 分阶段实施计划

### Phase 1: 核心网络层超时时长修正
- 修改 `OpenAIProtocol.kt` 里的 Ktor 客户端构造块，在 `HttpTimeout` 插件配置中追加 `socketTimeoutMillis = 120_000`。
- 修改 `GenericOpenAICompatProtocol.kt` 中的 Ktor 客户端构造块，追加 `socketTimeoutMillis = 120_000`。
- 检查 `AnthropicProtocol.kt` 和 `VertexAIProtocol.kt` 并做同样配置，防御潜在的其它协议大模型抽取超时风险。

### Phase 2: 调试桥报错广播与日志标签规范化
- 重构 `NexaraLogger.kt` 中的 `logError(tag, throwable)` 方法，通过 Android 原生 `Log.d("NEXARA_METRO", ...)` 广播以 `EVENT_START|ERROR|` 开头、包含异常 Message 与 5 行 StackTrace 摘要的结构化事件。
- 重构 `GraphExtractor.kt`，将日志前缀从 `"GraphExtractor: ..."` 替换为带中括号的 `"[RAG][GraphExtractor] ..."`，以便调试桥能精确捕获并将这类日志智能归类到 RAG/KG 专属信息通道中。

### Phase 3: 桌面 TUI 终端渲染器升级
- 修改 `scripts/nexara-metro-tui.js` 的 `processLine` 解析函数，追加 `case 'ERROR'` 的逻辑。
- 引入 ANSI 鲜红色样式，在桌面终端绘制出极其震撼且富含工业美学质感的报错卡片，显示报错 Tag、错误说明，并将其堆栈信息以浅灰色缩进呈现。

---

## 4. 边界条件与极端防御
- **无网络/离线崩溃防护**：如果在 Log 序列化 JSON 时发生任何异常，在 `NexaraLogger` 内部使用 `try-catch` 绝对保底，静默降级为传统输出，绝不能因“日志自身崩溃”拖垮主业务进程。
- **本地单元测试环境防崩**：`NexaraLogger.logError` 在 JVM 测试沙箱环境运行时，自动绕过 `android.util.Log` 调用以防止 `Method d in android.util.Log not mocked` 报错。

---

## 5. DIA 变更标准检查
- 架构/文件结构变更：否
- 新建或删除源代码文件：否
- 用户可见的功能/设置变更：是（TUI 渲染升级 + 提取防超时成功率提升） -> 将同步更新 `CHANGELOG.md`
