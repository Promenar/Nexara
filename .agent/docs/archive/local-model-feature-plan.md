# 本地模型功能模块 - 技术方案

> **状态**: ✅ 已实施 (v1.1.50+)
> **最后更新**: 2026-01-25
> **核心方案**: OpenAI 兼容协议 + 本地 HTTP 服务器 + 混合状态管理

---

## 架构概览

```
App → [现有 OpenAiClient] → HTTP → [本地 llama.cpp server] → GPU/NPU
       └── baseUrl: 'http://localhost:8080/v1'
```

**核心优势**: 复用现有 `openai.ts`，零网络层扩展

---

## 技术选型

| 组件 | 技术 | 说明 |
|------|------|------|
| LLM 推理 | llama.rn (v0.10+) | 内置 OpenAI 兼容 HTTP 服务器 |
| 模型格式 | GGUF | Hugging Face 直接下载 |
| Embedding | transformers.js (现有) | 或扩展 llama.rn |

---

## 实施阶段

### Phase 1: 服务器集成 (3-4h)
- 安装 `llama.rn`
- 创建 `LocalModelServer.ts` (启动/停止/状态)
- 应用生命周期钩子

### Phase 2: 模型管理 (2-3h)
- `ModelStorageManager.ts`
- 设置页开关

### Phase 3: Provider 集成 (1-2h)
- 添加 local provider 预设 (type: 'openai')
- 动态模型列表

### Phase 4: Embedding (2-3h)
- 扩展支持 embedding 端点

**总工时**: ~10h

---

## 关键代码示例

```typescript
// api-store.ts - Local Provider
const LOCAL_PROVIDER: ProviderConfig = {
  id: 'local',
  name: '本地模型',
  type: 'openai',  // 复用 OpenAI 协议
  baseUrl: 'http://127.0.0.1:8080/v1',
  apiKey: 'not-needed',
  enabled: false,
};
```

---

## 参考资料

- [llama.rn GitHub](https://github.com/nicholaslee119/llama.rn)
- [GGUF 模型 Hugging Face](https://huggingface.co/models?library=gguf)
