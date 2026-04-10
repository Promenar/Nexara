# LLM抽象层架构提升方案

> [!IMPORTANT]
> **STATUS: IMPLEMENTED (2026-01-14)**
> 本文档为架构升级的原始提案。该方案已完全落地。
> - **最新架构文档**: 请查阅 [llm-abstraction-layer-guide.md](./llm-abstraction-layer-guide.md)
> - **代码结构**: 请查阅 `memory/CODE_STRUCTURE.md`


> 版本: v1.0  
> 日期: 2026-01-14  
> 状态: 已批准待实施

## 执行摘要

本文档规划如何在现有架构基础上，通过扩展抽象层来解决多Provider兼容性问题，避免"修A坏B"的循环困境。

## 一、现状分析

### 1.1 架构分层

项目已有良好的三层架构：

```
业务层 (chat-store.ts)
   ↓
抽象层 (normalizer/parser/preprocessor)
   ↓
网络层 (openai.ts/gemini.ts/vertexai.ts)
```

### 1.2 现有抽象组件

| 组件 | 职责 | 文件 |
|------|------|------|
| ResponseNormalizer | 统一各Provider响应格式 | `response-normalizer.ts` |
| MessagePreprocessor | 处理多模态消息、图片 | `message-preprocessor.ts` |
| StreamParser | 提取流式输出中的工具调用 | `stream-parser.ts` |
| Factory | 根据provider创建Client | `factory.ts` |

### 1.3 问题根源

**chat-store.ts 绕过了抽象层**，直接在业务逻辑中处理Provider差异：
- XML fallback 解析 (1586-1782行)
- 历史记录构建 (1137-1228行)
- Provider特定判断散落在多处

## 二、优化方案

### 2.1 扩展 ResponseNormalizer（细粒度Provider支持）

**当前**：所有OpenAI兼容模型共享 `normalizeOpenAI()`

**改进**：为每个服务商添加专属分支

```typescript
// response-normalizer.ts
export class ResponseNormalizer {
  static async normalize(rawResponse: any, providerType: ProviderType): Promise<NormalizedChunk> {
    switch (providerType) {
      case 'openai':
        return this.normalizeOpenAI(rawResponse);
      case 'deepseek':
        return this.normalizeDeepSeek(rawResponse);
      case 'zhipu':      // GLM
        return this.normalizeZhipu(rawResponse);
      case 'moonshot':   // KIMI
        return this.normalizeMoonshot(rawResponse);
      case 'gemini':
        return this.normalizeGemini(rawResponse);
      case 'vertex':
        return this.normalizeVertex(rawResponse);
    }
  }

  private static async normalizeDeepSeek(raw: any): Promise<NormalizedChunk> {
    // DeepSeek特定：支持 reasoning_content、<think> 标签
  }

  private static async normalizeZhipu(raw: any): Promise<NormalizedChunk> {
    // GLM特定：XML工具调用、特殊格式处理
  }
}
```

### 2.2 增强 StreamParser（内容清理）

**当前问题**：Parser提取工具调用但不剥离XML标签

**改进**：添加Provider感知的内容清理

```typescript
// stream-parser.ts
export class StreamParser {
  constructor(private provider: ProviderType) {}

  getCleanContent(): string {
    let content = this.rawContent;
    
    // Provider特定清理
    switch (this.provider) {
      case 'zhipu':
      case 'deepseek':
        content = content
          .replace(/<tool_call>[\s\S]*?<\/tool_call>/gi, '')
          .replace(/```html[\s\S]*?```/gi, '')
          .replace(/```xml[\s\S]*?```/gi, '');
        break;
    }
    
    return content.trim();
  }
}
```

### 2.3 新增 MessageFormatter（历史构建）

**新组件**：处理各Provider的历史记录构建差异

```typescript
// message-formatter.ts
export interface MessageFormatter {
  formatHistory(messages: Message[], contextWindow: number): any[];
  shouldStripToolCalls(message: Message): boolean;
}

export class OpenAIFormatter implements MessageFormatter {
  formatHistory(messages: Message[], contextWindow: number): any[] {
    // 严格的 tool_calls 完整性校验
    // 移除 reasoning_content（OpenAI不支持回传）
  }
}

export class DeepSeekFormatter implements MessageFormatter {
  formatHistory(messages: Message[], contextWindow: number): any[] {
    // 支持 reasoning_content
    // 处理XML fallback
    // 兼容 <think> 标签
  }
}

export class FormatterFactory {
  static getFormatter(provider: ProviderType): MessageFormatter {
    switch (provider) {
      case 'deepseek': return new DeepSeekFormatter();
      case 'zhipu': return new GLMFormatter();
      case 'moonshot': return new MoonshotFormatter();
      default: return new OpenAIFormatter();
    }
  }
}
```

### 2.4 清理 chat-store.ts

**目标**：恢复业务层的纯粹性

```typescript
// chat-store.ts（简化后）
const generateMessage = async (...) => {
  // 1. 获取适配器
  const formatter = FormatterFactory.getFormatter(provider.type);
  const parser = new StreamParser(provider.type);
  
  // 2. 构建历史（委托给formatter）
  const formattedHistory = formatter.formatHistory(
    session.messages,
    contextWindow
  );
  
  // 3. 流式解析（使用parser的清理功能）
  streamClient.streamChat({
    onToken: (token) => {
      parser.feed(token.content);
      const cleanContent = parser.getCleanContent();
      // ...
    }
  });
}
```

## 三、实施路线图

### Phase 1: 立即修复（本周）
1. ✅ 扩展 StreamParser，添加 `getCleanContent(provider)`
2. ✅ 在 ResponseNormalizer 中分离 DeepSeek/GLM/KIMI
3. ✅ 临时修复当前的XML泄露和中断问题

### Phase 2: 架构优化（2周内）
1. 创建 MessageFormatter 抽象
2. 实现各Provider的专属Formatter
3. 迁移 chat-store.ts 的历史构建逻辑

### Phase 3: 完善测试（1月内）
1. 为每个Formatter建立测试用例
2. 集成测试覆盖各Provider的完整流程
3. 文档化扩展点和最佳实践

## 四、架构原则

### 4.1 分层原则

| 层级 | 职责 | 禁止 |
|------|------|------|
| **业务层** | 任务管理、用户交互、代理循环 | ❌ Provider判断 |
| **抽象层** | 格式转换、内容清理、协议适配 | ❌ 业务逻辑 |
| **网络层** | HTTP通信、流式处理、错误重试 | ❌ 格式解析 |

### 4.2 扩展原则

- **按Provider颗粒度设计**（不是粗粒度的 OpenAI vs Vertex）
- **利用现有架构**（扩展而非重写）
- **开放封闭原则**（新增Provider不影响现有逻辑）

## 五、Tools与API通信的关系

### 职责分离

- **Tools（业务层）**：`manage_task` 等业务功能
- **API Communication（网络层）**：HTTP协议实现
- **抽象层（桥梁）**：
  - StreamParser 从API响应提取 tool_calls
  - MessageFormatter 将 tool_calls 转为API格式
  - chat-store 只需执行业务逻辑

### 协同流程

```
模型输出 → StreamParser.parseToolCalls() → chat-store.executeTools()
                                                      ↓
                                        MessageFormatter.formatToolCalls()
                                                      ↓
                                            发送给下一轮API请求
```

## 六、成功标准

1. ✅ 业务层无Provider判断逻辑
2. ✅ 新增Provider只需扩展Formatter/Normalizer
3. ✅ 所有模型稳定通过完整代理循环
4. ✅ 无"修A坏B"现象

## 七、参考文档

- 架构分析：`architecture_analysis.md`
- 现有实现：`response-normalizer.ts`, `stream-parser.ts`
- 待重构：`chat-store.ts` (1137-1228, 1586-1782行)
