# LLM Integration Testing Guide (v1.0)

> **Last Updated**: 2026-02-14
> **Scope**: LLM Clients, Tool Calling, Prompt Engineering

## 1. 核心目标
本测试套件旨在提供一个**脱离 React Native UI** 的纯 Node.js 环境，用于快速验证：
- LLM 服务商连通性 (Connectivity)
- 流式输出解析 (Streaming Parsing)
- 工具调用逻辑 (Tool Calling / Function Calling)
- Prompt 工程调整 (System Prompts)

---

## 2. 快速开始

### 2.1 准备环境
确保已安装依赖（尤其是 `tsx` 和 `xhr2`）：
```bash
npm install
```

### 2.2 配置密钥
在 `secure_env/test_api.json` 中配置 API Key。该文件已被 `.gitignore` 忽略，安全无虞。

**模板**:
```json
{
  "zhipu-ai": {
    "apiKey": "your-api-key",
    "modelId": "glm-4.5-air",
    "baseUrl": "https://open.bigmodel.cn/api/paas/v4"
  },
  "vertex-ai": {
    "projectId": "your-project-id",
    "region": "us-central1",
    "modelId": "gemini-1.5-pro",
    "keyFile": "./vertex-key.json"
  }
}
```

### 2.3 运行测试
使用 `node --import tsx` 直接运行脚本：

```bash
# 默认使用 zhipu-ai
node --import tsx scripts/test-llm.ts

# 指定服务商
node --import tsx scripts/test-llm.ts --provider vertex-ai
node --import tsx scripts/test-llm.ts --provider ollama
```

---

## 3. 测试覆盖范围

此脚本 (`scripts/test-llm.ts`) 包含两个核心测试用例：

1.  **Chat Completion (Streaming)**:
    -   发送 "Hello World" 并要求讲笑话。
    -   验证 Token 是否能实时流式输出到控制台。
    -   验证 `<think>` 标签（DeepSeek）解析是否正常。

2.  **Tool Calling Verification**:
    -   发送 "What is the current time in Tokyo?"。
    -   验证模型是否返回了 `get_current_time` 工具调用。
    -   验证参数 JSON 是否正确解析 (e.g. `{"city": "Tokyo"}`)。

---

## 4. 常见问题 (Troubleshooting)

### Q1: `zod-to-json-schema` 报错？
**症状**: `Cannot read properties of undefined (reading 'typeName')`
**原因**: 测试脚本中定义的 Mock Tool 使用了纯 JSON Schema，而 `OpenAiClient` 试图将其作为 Zod 对象转换。
**解决**: `OpenAiClient.ts` 已打补丁，会自动检测 Schema 类型。如果报错回归，请检查 `mapSkillsToOpenAITools` 方法。

### Q2: 脚本无输出 (Hang)？
**原因**: `npx tsx` 在某些环境下（如 WSL2）可能会与 Node 进程通信死锁。
**解决**: 始终使用 `node --import tsx scripts/test-llm.ts` 替代 `npx tsx ...`。

### Q3: Native Module 报错？
**症状**: `Error: Whatever is not fully supported in Node.js` (e.g. `expo-file-system`)
**原因**: 引入了 React Native 专用库。
**解决**: 检查 `scripts/test-setup.ts` 中的 `module-alias` 配置，确保所有 Native 库都指向了 `scripts/mocks/` 下的模拟文件。

---

## 5. 扩展指南

若要添加新的测试用例（例如 RAG 检索），请直接修改 `scripts/test-llm.ts` 中的 `runTest` 函数。

```typescript
// Example: Add RAG Test
console.log('\n🧪 Test 3: RAG Retrieval...');
// ... your code here
```
