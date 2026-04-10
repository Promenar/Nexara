# LLM 输出解析与分流策略技术存档

此文档记录了 Nexara 在处理不同大模型（LLM）输出时的解析架构、字段映射及工程权衡，旨在为后续优化及新模型接入提供参考。

## 1. 核心架构：双轨分流机制

Nexara 采用“原生协议优先 + 文本流状态机解析”的双轨机制来区分正文、思考过程和工具调用。

### A. 原生协议轨道 (Native Track)
直接从供应商的 API 响应字段中提取结构化数据。
- **正文 (`content`)**: `choices[0].delta.content`
- **推理/思考 (`reasoning`)**: `choices[0].delta.reasoning_content` (目前 DeepSeek R1, Kimi k2 采用此标准)
- **工具调用 (`tool_calls`)**: `choices[0].delta.tool_calls` (OpenAI 标准功能)

### B. 状态机解析轨道 (State Machine Track)
当 API 字段单一（全部混在 `content` 中）或模型降级时，利用 `StreamParser.ts` 进行实时剥离。
- **原理**: 维护一个 `ParserState` (IDLE, IN_THINK, IN_TOOL_XML, IN_PLAN)。
- **触发器**: 扫描特定标签（如 `<!-- THINKING_START -->`, `<think>`, `<tool_code>`）。
- **清理**: 在最终展示和持久化前，通过 `getCleanContent()` 物理删除所有解析标签。

---

## 2. 标签定义与用途

| 类别 | 标签格式 | 解析目标 | 用途 |
| :--- | :--- | :--- | :--- |
| **思考过程** | `<!-- THINKING_START -->` / `<!-- THINKING_END -->` | `reasoning` | 实时渲染 Timeline 中的思考过程 |
| **工具调用** | `<tool_code>` / `<call>` / `<tool_call>` | `toolCalls` | 实现 XML 降级下的函数调用解析 |
| **任务规划** | `<plan>` | `plan` | 驱动 UI 层展示甘特图/任务步骤 |
| **正文内容** | (无，排他性解析) | `content` | 消息气泡的主要文字，无转义干扰 |

---

## 3. 模型家族适配现状

| 模型家族 | 思考捕获 | 工具调用 | 备注 |
| :--- | :--- | :--- | :--- |
| **DeepSeek / Moonshot** | **原生字段** | 原生 `tool_calls` | 稳定性最高，Turn 2 需保留 reasoning 历史 |
| **Google Gemini / Vertex** | API 转换/标签引导 | 原生 `tools` | K2 等思考模型对 OpenAI 兼容接口尚在演进 |
| **智谱 (GLM)** | 标签解析 (`<thought>`) | 原生 `tool_calls` | 依赖专用 `stream_options` 或状态机剥离 |
| **OpenAI (GPT)** | 不可见 (o1/o3) | 原生 `tool_calls` | o3-mini 开始提供 reasoning_tokens 计数 |
| **本地 (Ollama/LMStudio)** | 标签解析 | XML 降级模式 | 大部分本地模型需引导输出 XML 以匹配工具 |

---

## 4. 工程权衡 (Trade-offs)

### JSON Mode vs. Tags
- **为何不用全量 JSON？** 
    1. **流式性能**: JSON 必须闭合才能解析（或使用复杂且脆弱的 Partial Parser）。
    2. **响应感**: Tags 允许并行展示正文，无需等待思考块结束。
    3. **Markdown 友好**: 避免在 JSON 字符串中处理大量转义字符（`\n`, `\"`）。
- **风险**: 存在“影子解析”风险。如果用户询问有关标签的教程，状态机可能误判。*潜在优化：在检测到 Markdown 代码块 (```) 时暂停解析。*

### 严格模式 (Strict Mode)
- **结论**: 虽然 OpenAI 推荐 `strict: true`，但 **Kimi 等厂商目前不支持**。Nexara 已针对 Kimi 引入 `MoonshotClient` 强制禁用严格模式以保证多轮会话稳定性。

---

## 5. 后续维护建议
1. **监控 Kimi/DeepSeek API 变动**：优先保持 `reasoning_content` 的同步。
2. **增强解析鲁棒性**：处理标签被 Token 强行切断（Split Tag）的情况（当前已有启发式恢复逻辑）。
3. **隔离代码块解析**：防止正文代码块内容被误判为指令标签。
