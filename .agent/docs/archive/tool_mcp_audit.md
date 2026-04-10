# Tool System & MCP Client 审计报告

> 审计日期：2026-02-14 | 已经过反向校验

## 1. 系统架构

系统实现了**双通道并行**的工具调用架构：

| 通道 | 机制 | 代码位置 |
|------|------|---------|
| **通道 A：原生 Function Calling** | HTTP Body `tools` 参数 | `openai.ts:443`, `gemini.ts:303-316`, `deepseek.ts:520` |
| **通道 B：Prompt 降级** | System Prompt + `StreamParser` 正则 | `context-builder.ts:274-312`, `stream-parser.ts` |

两通道结果在 `chat-store.ts:1461-1482` **合并**到同一 `toolCalls` 数组，由 `ToolExecutor` 统一执行。

## 2. 原生 API vs Prompt 调用

| 维度 | 原生 API（通道 A） | Prompt 注入（通道 B） |
|------|-------|---------|
| **可靠性** | ⭐⭐⭐⭐⭐ 结构化输出 | ⭐⭐⭐ XML 正则提取 |
| **Token 消耗** | ⭐⭐⭐⭐⭐ 不占上下文 | ⭐⭐ 占上下文窗口 |
| **泛用性** | ~70-80%（主流商用模型） | ~100%（所有文本模型） |

通道 B 是针对本地 LLM / 不支持原生 API 的模型的必要 fallback。**双通道设计合理**。

## 3. Provider 原生支持矩阵

| Provider | 原生 Function Calling | 当前状态 |
|----------|---------------------|---------|
| OpenAI (GPT-4/4o) | ✅ + `strict` 模式 | ✅ 已启用 |
| Google Gemini | ✅ `functionDeclarations` | ✅ 已启用 |
| DeepSeek (V3/R1) | ✅ OpenAI 兼容 | ✅ 已启用 |
| OpenAI Compatible | ⚠️ 取决于网关 | ✅ 尝试启用 |
| Local LLM (Ollama) | ❌ | 仅通道 B |

## 4. MCP Bridge

- **设计**：无状态，每次调用 `new McpClient()` → `connect()` → `callTool()` → `disconnect()`
- **优点**：无连接泄漏风险
- **缺点**：远程 Server 延迟高

## 5. 已识别问题

### 5.1 双通道重复消耗 🔴
`context-builder.ts` **无条件**将工具描述写入 System Prompt（Markdown），同时 Provider 也通过 `tools` 参数传递（JSON Schema）。同一工具被描述两次。

### 5.2 Gemini 双重搜索指令 🟡
`gemini.ts:235-243` 硬编码搜索指令 + `context-builder.ts` System Prompt 中可能存在重复搜索指令。

### 5.3 MCP 连接效率 🟡
远程 MCP Server 的无状态连接开销较大，建议实现连接池。

## 6. 建议优先级

| 优先级 | 建议 | 对应 TODO |
|--------|------|----------|
| 🔴 High | 条件性 Prompt 注入：支持原生 API 时跳过 System Prompt 工具描述 | `009_tool_prompt_dedup.md` |
| 🟡 Medium | MCP 连接池 | `010_mcp_connection_pool.md` |
| 🟢 Low | Provider 能力矩阵化 | 长期架构目标 |
