# LLM抽象层架构完整指引

> **重要性：核心架构文档**  
> 本文档定义了项目中LLM相关功能的分层架构。所有LLM Provider的集成、调试和优化**必须**遵循此架构。

## 一、架构概览

### 三层架构

```
业务层 (chat-store.ts)
   ↓ 调用抽象层接口
【抽象层】← 所有Provider差异在这里处理
   ├─ ResponseNormalizer    (统一响应格式)
   ├─ StreamParser          (解析工具调用+清理内容)
   ├─ MessageFormatter      (构建历史记录)
   └─ FormatterFactory      (Provider路由)
   ↓ 调用网络层
网络层 (openai.ts / gemini.ts / vertexai.ts)
   └─ HTTP通信
```

### 核心原则

1. **职责隔离**：业务层不包含Provider判断逻辑
2. **Provider颗粒度**：按服务商（DeepSeek/GLM/KIMI）而非协议（OpenAI）划分
3. **独立扩展**：新增Provider不影响现有逻辑
4. **向下兼容**：网络层保持纯粹的HTTP职责

## 二、抽象层组件详解

### 2.1 ResponseNormalizer

**文件位置**：`src/lib/llm/response-normalizer.ts`

**职责**：统一各Provider的响应格式为`NormalizedChunk`

```typescript
interface NormalizedChunk {
  content: string;
  reasoning?: string;
  citations?: Citation[];
  images?: GeneratedImageData[];
  tokens?: TokenUsage;
}
```

**工作原理**：
```typescript
// 使用
const chunk = await ResponseNormalizer.normalize(rawResponse, 'deepseek');

// 内部分发
switch (providerType) {
  case 'deepseek': return normalizeDeepSeek(raw);
  case 'zhipu': return normalizeZhipu(raw);
  case 'moonshot': return normalizeMoonshot(raw);
  //...
}
```

**扩展方法**：
1. 在`ProviderType`中添加新Provider名称
2. 在`normalize()`的switch中添加分支
3. 实现`normalize[ProviderName]()`私有方法

### 2.2 StreamParser

**文件位置**：`src/lib/llm/stream-parser.ts`

**职责**：
1. 从流式输出中增量解析工具调用（XML fallback）
2. 提供Provider特定的内容清理

**关键方法**：
- `process(chunk)` - 增量处理流式chunk
- `getCleanContent(rawContent)` -根据Provider剥离XML/标签
- `parseToolCalls(block)` - 解析完整的工具调用块

**Provider特定清理**：
```typescript
constructor(provider: ProviderType) {
  this.provider = provider;
}

getCleanContent(rawContent: string): string {
  switch (this.provider) {
    case 'zhipu':
    case 'deepseek':
      // 移除XML标签
      return content.replace(/<tool_call>[\s\S]*?<\/tool_call>/gi, '');
    //...
  }
}
```

**扩展方法**：
在`getCleanContent()`的switch中为新Provider添加清理逻辑。

### 2.3 MessageFormatter

**文件位置**：`src/lib/llm/message-formatter.ts` + `formatters/provider-formatters.ts`

**职责**：处理各Provider历史记录构建的差异

**接口定义**：
```typescript
interface MessageFormatter {
  formatHistory(messages: Message[]): ChatMessage[];
  shouldStripHangingToolCalls(message: Message): boolean;
  supportsReasoningInHistory(): boolean;
}
```

**各Provider特性对比**：

| Provider | reasoning回传 | tool_calls严格性 | 特殊处理 |
|----------|--------------|-----------------|---------|
| OpenAI   | ❌ (仅o1输出) | ✅ 严格 | 移除reasoning |
| DeepSeek | ✅ 支持 | ✅ 严格 | 保留reasoning |
| GLM      | ❌ 不支持 | ✅ 严格 | 移除reasoning |
| KIMI     | ❌ 不支持 | ✅ 严格 | 移除reasoning |
| Gemini   | ❌ (用thought_signature) | ⚠️ 宽松 | 网络层处理 |

**扩展方法**：
1. 在`formatters/provider-formatters.ts`中创建新Formatter类
2. 继承`BaseMessageFormatter`
3. 实现三个接口方法
4. 在`FormatterFactory`的switch中添加分支

### 2.4 FormatterFactory

**文件位置**：`src/lib/llm/formatter-factory.ts`

**职责**：根据Provider类型返回对应的Formatter实例（单例模式）

**使用示例**：
```typescript
const formatter = FormatterFactory.getFormatter('deepseek');
const formattedHistory = formatter.formatHistory(messages);
```

## 三、工作流程示例

### 3.1 添加新Provider（例如：Anthropic）

**Step 1**：扩展类型定义
```typescript
// response-normalizer.ts
export type ProviderType = 
  | 'openai' | 'deepseek' | 'zhipu' | 'moonshot'
  | 'anthropic'; // 新增
```

**Step 2**：实现ResponseNormalizer
```typescript
// response-normalizer.ts
case 'anthropic':
  return this.normalizeAnthropic(rawResponse);

private static async normalizeAnthropic(raw: any): Promise<NormalizedChunk> {
  // Anthropic特定解析
}
```

**Step 3**：实现MessageFormatter
```typescript
// formatters/provider-formatters.ts
export class AnthropicFormatter extends BaseMessageFormatter {
  formatHistory(messages: Message[]): ChatMessage[] {
    // Anthropic特定历史构建
  }
  shouldStripHangingToolCalls() { return true; }
  supportsReasoningInHistory() { return false; }
}
```

**Step 4**：注册FormatterFactory
```typescript
// formatter-factory.ts
case 'anthropic':
  return new AnthropicFormatter();
```

**Step 5**：（可选）StreamParser清理
```typescript
// stream-parser.ts
case 'anthropic':
  // 如有特殊XML/标签需要清理
  break;
```

### 3.2 调试现有Provider

**问题定位规则**：

| 症状 | 可能位置 | 调试方法 |
|------|---------|---------|
| 输出格式错误（如XML泄露） | StreamParser | 检查`getCleanContent()` |
| reasoning显示问题 | ResponseNormalizer | 检查`normalize[Provider]()` |
| 历史记录导致API错误 | MessageFormatter | 检查`formatHistory()` |
| 循环终止问题 | chat-store通用逻辑 | 检查循环终止条件 |

**示例：修复GLM的XML输出**
```typescript
// 错误位置：StreamParser.getCleanContent()
case 'zhipu':
  cleaned = cleaned.replace(/<tool_call>[\s\S]*?<\/tool_call>/gi, '');
  cleaned = cleaned.replace(/```xml[\s\S]*?```/gi, ''); // 添加此行
  break;
```

## 四、最佳实践

### 4.1 修改原则

1. **✅ 应该在抽象层修改**：
   - Provider特定的格式差异
   - XML/标签清理逻辑
   - 历史记录构建规则

2. **❌ 不应该在业务层修改**：
   - 不要在chat-store.ts中添加`if (provider === 'xxx')`判断
   - 不要硬编码Provider特定的正则表达式

3. **⚠️ 慎重在网络层修改**：
   - 网络层应保持纯粹的HTTP职责
   - 格式解析属于抽象层职责

### 4.2 代码审查清单

添加新功能时，问自己：
- [ ] 这个逻辑是Provider特定的吗？ → 应该在抽象层
- [ ] 这会影响所有Provider吗？ → 应该在业务层
- [ ] 这只是HTTP通信细节吗？ → 应该在网络层

### 4.3 常见陷阱

1. **陷阱：在chat-store中直接清理XML**
   ```typescript
   // ❌ 错误
   content = content.replace(/<tool_call>.*?<\/tool_call>/g, '');
   ```
   **正确**：在StreamParser.getCleanContent()中处理

2. **陷阱：在业务层判断reasoning支持**
   ```typescript
   // ❌ 错误
   if (provider === 'deepseek') {
     message.reasoning = ...;
   }
   ```
   **正确**：在MessageFormatter.formatHistory()中处理

## 五、架构演进

### 当前状态（v1.0）

- ✅ ResponseNormalizer - 完成
- ✅ StreamParser - 完成
- ✅ MessageFormatter - 完成
- ⚠️ 循环控制 - 部分在业务层（可接受）

### 未来优化（可选）

1. **LoopController抽象**：将循环终止条件Provider化
2. **ToolCallExtractor**：统一工具调用提取逻辑
3. **完全集成MessageFormatter**：移除chat-store中的历史拼装

## 六、故障排查指南

### 问题：新Provider不工作

**检查清单**：
1. [ ] ProviderType类型是否添加？
2. [ ] ResponseNormalizer switch分支是否添加？
3. [ ] MessageFormatter是否实现？
4. [ ] FormatterFactory是否注册？
5. [ ] factory.ts中createLlmClient是否支持？

### 问题："修A坏B"现象

**根本原因**：在业务层修改了共享逻辑

**解决**：
1. 回退业务层修改
2. 在对应Provider的Formatter中实现差异化
3. 测试所有Provider确认隔离

## 七、术语表

- **Provider**：LLM服务提供商（如OpenAI、DeepSeek、智谱）
- **ResponseNormalizer**：响应标准化器
- **StreamParser**：流式解析器
- **MessageFormatter**：消息格式化器
- **reasoning_content**：模型的推理/思考过程
- **tool_calls**：工具调用（function calling）
- **Hanging tool_calls**：悬挂的工具调用（没有对应结果）

---

**最后更新**：2026-01-14  
**维护者**：Architecture Team  
**审查周期**：每次新增Provider后
