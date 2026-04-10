# 009：消除工具描述双通道重复消耗

> 来源：[Tool/MCP 审计报告](../tool-mcp-audit-report.md) §5.1
> 优先级：🔴 High

## 问题描述

当前 `context-builder.ts` **无条件**将所有可用工具以 Markdown 格式注入 System Prompt（第274-312行）。
但 `openai.ts`、`gemini.ts`、`deepseek.ts` 已通过 HTTP Body 的 `tools` 参数传递了同等信息的 JSON Schema。

**后果**：
- 同一工具被描述两次，浪费 Token（约 200-500 tokens/tool）
- 模型可能对两个来源的指令产生冲突解读

## 方案

### Phase 1：添加 Provider 能力标识

在 `model-specs.ts` 或 Provider 工厂中为每个 Provider 声明 `supportsNativeTools: boolean`。

### Phase 2：条件性跳过 Prompt 注入

在 `context-builder.ts` `buildSystemPrompt()` 中：
```typescript
// 当 Provider 原生支持 tools API 时，跳过 System Prompt 工具描述注入
if (provider.supportsNativeTools && skillsToUse.length > 0) {
    // 仅注入执行规则（XML/JSON 格式指导），不注入工具列表描述
    finalSystemPrompt += executionRulesOnly;
} else {
    // fallback：完整注入工具描述 + 执行规则
    finalSystemPrompt += toolInstruction;
}
```

### Phase 3：统一 Gemini 搜索指令

将 `gemini.ts:235-243` 中硬编码的 `systemInstruction` 迁移至 `context-builder.ts` 或 `model-prompts.ts` 统一管理。

## 影响范围

- `src/store/chat/context-builder.ts`
- `src/lib/llm/model-specs.ts`（或新文件）
- `src/lib/llm/providers/gemini.ts`

## 验证方式

1. 选择 GPT-4o 模型，启用工具，检查 System Prompt **不包含**工具 Markdown 描述
2. 选择本地 LLM 模型，确认 System Prompt **包含**完整工具描述（fallback 路径）
3. 对比 Token 消耗前后差异
