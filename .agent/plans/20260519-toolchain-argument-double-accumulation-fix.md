# 工具链参数双重累积与错误处理审计修复

> **日期**: 2026-05-19 | **状态**: ✅ 已实施 | **优先级**: P0
> **关联**: ADR-014 (工具调用系统架构), ADR-016 (CancellationException 规范)

## 背景

用户报告大量先进模型（DeepSeek-v4 等）的工具调用均出现参数格式错误：
- 部分模型多次尝试修正却一直无法成功
- 部分模型一次调用错误即被系统停止会话循环

## 根因分析

### P0-1: 工具调用参数「双重累积」(Double Accumulation)

**影响协议**: OpenAIProtocol, GenericOpenAICompatProtocol (覆盖 10+ 国产模型)

**因果链**:
```
OpenAI SSE: function.arguments (incremental fragment)
  → Protocol 层: 累积为完整字符串 [OK]
  → ToolCallDelta(完整) [BUG SOURCE]
  → ChatViewModel: existing.arguments + chunk.arguments [二次累积]
  → 参数膨胀: "{\"query\": \"南京{\"query\": \"南京 2026{\"query\": \"南京 2026年..."
  → ToolExecutor.parseArgs(): 解析失败 → emptyMap()
  → WebSearchSkill: args["query"] = null → "Missing query argument"
```

**对比**: AnthropicProtocol 的 `input_json_delta` 发送 `partialJson`(增量片段)，正确。

### P0-2: 流式错误「一次即死」

**影响**: 所有协议

StreamChunk.Error → `currentCoroutineContext().cancel()` → `generateMessage()` 在 line 592 直接 return → 跳过后置工具执行循环 → 模型无重试机会

### P1-1: System Prompt 工具调用指令冲突

旧指令同时告诉模型使用 native function calling 和 XML 降级方案，模型可能混合格式。

### P1-2: TOOL_RESULT_SEPARATOR_PATTERN 误匹配

正则 `---\s*` 匹配了 Markdown 表格分隔线 `---|---|---`，将 MiniMax 的工具列表文本误判为工具调用结果。

## 修复方案

### P0-1 修复 (OpenAIProtocol.kt, GenericOpenAICompatProtocol.kt)

```kotlin
// 修复前: 发送完整累积值 (重复累积)
send(StreamChunk.ToolCallDelta(id = acc.id, name = acc.name, arguments = acc.arguments, ...))

// 修复后: 发送增量片段 (仅本次新增)
send(StreamChunk.ToolCallDelta(id = ..., name = ..., arguments = fragment, ...))
```

同时在 `flushRemaining()` 中移除重复的 ToolCallDelta 发送。

### P0-1 修复 (AnthropicProtocol.kt)

`processContentBlockStop` 中移除完整的 ToolCallDelta 发送，避免与增量片段重复累积。

### P0-2 修复 (ChatViewModel.kt)

- 引入 `streamingError` 标志替代直接 `cancel()`
- 仅在无工具调用且无内容时立即终止
- 有工具调用时：创建假 assistant 消息 → 执行工具 → 反馈给模型 → 允许重试

### P1-1 修复 (ContextBuilder.kt)

重写工具使用指南：
- 明确的 "Calling Tools" 章节 (native function calling)
- 明确的 "Handling Errors" 章节 (告知模型重试策略)
- 移除 XML 降级指令（对原生 function calling 模型造成干扰）
- 移除过度严格的 "CRITICAL MANDATE"

### P1-2 修复 (ChatViewModel.kt)

TOOL_RESULT_SEPARATOR_PATTERN 添加：
- 负向前瞻 `(?!-{2,})` 排除表格分隔线
- 行首锚点 `(?:^|\n)` 要求模式在行首

## 变更文件

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `OpenAIProtocol.kt` | 修复 | 增量 fragment 发送 + flushRemaining 清理 |
| `GenericOpenAICompatProtocol.kt` | 修复 | 同上 |
| `AnthropicProtocol.kt` | 修复 | content_block_stop 去重 |
| `ChatViewModel.kt` | 修复 | 软错误处理 + streamingError 标志 + regex 强化 |
| `ContextBuilder.kt` | 优化 | 工具指令重写 |

## 验证

- ✅ 编译通过 (compileDebugKotlin)
- ✅ ToolExecutorTest (5/5 通过)
- ✅ 所有文件零 lint 错误
- ⚠️ OpenAIProtocolTest 需要真实 API 凭证 (非代码问题)

## 后续建议

1. **工具参数验证增强**: 在 SkillRegistry.getAllTools() 中校验 parametersSchema 是否为合法 JSON Schema
2. **Anthropic content_block_stop 信号**: 考虑添加 `ToolCallFinalized` 类型的 StreamChunk 替代已删除的最终完整发送
3. **流错误分类**: 区分 retryable error vs fatal error，对前者启用自动重试
4. **Arguments 幂等校验**: ChatViewModel 可添加 "如果新 fragment 是已有 arguments 的前缀则为重复" 的防御逻辑
