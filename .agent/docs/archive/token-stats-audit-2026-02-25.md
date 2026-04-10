# Token 统计模块审计报告

## 审计概述

| 项目 | 详情 |
|------|------|
| 审计日期 | 2026-02-25 |
| 审计范围 | 会话界面 Token 统计模块 |
| 审计目标 | 评估统计机制合理性、识别潜在漏洞、评估上下文超限检测能力 |
| 整体评级 | **B+** (功能完整，存在改进空间) |

---

## 一、系统架构分析

### 1.1 数据流架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Token 统计数据流                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐                                                        │
│  │  LLM API 响应   │                                                        │
│  │  usage 字段     │                                                        │
│  └────────┬────────┘                                                        │
│           │                                                                 │
│           ▼                                                                 │
│  ┌─────────────────────────────────────────┐                               │
│  │  chat-store.generateMessage()           │                               │
│  │  ├─ accumulatedUsage (API 返回)         │                               │
│  │  └─ estimateTokens() (降级估算)         │                               │
│  └────────┬────────────────────────────────┘                               │
│           │                                                                 │
│           ▼                                                                 │
│  ┌─────────────────────────────────────────┐                               │
│  │  post-processor.updateStats()           │                               │
│  │  构建 BillingUsage 对象                  │                               │
│  └────────┬────────────────────────────────┘                               │
│           │                                                                 │
│     ┌─────┴─────┐                                                          │
│     ▼           ▼                                                          │
│  ┌──────────┐  ┌───────────────────────┐                                   │
│  │ 会话级   │  │ 全局级                 │                                   │
│  │ session  │  │ useTokenStatsStore    │                                   │
│  │ .stats   │  │ .byModel[modelId]     │                                   │
│  │ .billing │  │ .globalTotal          │                                   │
│  └──────────┘  └───────────────────────┘                                   │
│       │                   │                                                 │
│       ▼                   ▼                                                 │
│  ┌──────────────┐  ┌────────────────────┐                                  │
│  │TokenStats    │  │token-usage.tsx     │                                  │
│  │Modal         │  │(全局统计页面)       │                                  │
│  │(会话统计弹窗)│  │                    │                                  │
│  └──────────────┘  └────────────────────┘                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 核心数据结构

```typescript
// 基础 Token 计量单位
interface TokenMetric {
  count: number;
  isEstimated: boolean;  // 🔑 核心字段：区分 API 真实值 vs 本地估算
}

// 计费详情结构
interface BillingUsage {
  chatInput: TokenMetric;   // 用户提问 + 历史上下文
  chatOutput: TokenMetric;  // 模型生成回复
  ragSystem: TokenMetric;   // RAG 开销：重写 + 摘要 + Embedding
  total: number;            // 总计费 Token
  costUSD?: number;         // 估算成本
}
```

### 1.3 Token 来源优先级

| 优先级 | 来源 | 标记 | 准确性 |
|--------|------|------|--------|
| 1 | LLM API `usage` 字段 | `isEstimated: false` | 高 |
| 2 | 本地启发式估算 | `isEstimated: true` | 中低 |

---

## 二、发现的问题与风险

### 🔴 P1 - 高优先级问题

#### 问题1：ChatInput 中 tokenUsage 未被渲染

**文件**：[src/features/chat/components/ChatInput.tsx](file:///Users/promenar/Codex/Nexara/src/features/chat/components/ChatInput.tsx#L249-L252)

**现象**：
- 组件接收 `tokenUsage` prop（包含 `total` 和 `last` 字段）
- `Calculator` 图标已导入但**从未使用**
- `handleTokenPress` 函数已定义但**无 UI 触发点**
- 用户无法在输入栏直接看到当前会话的 Token 使用情况

**代码证据**：
```typescript
// Props 定义存在
tokenUsage?: {
  total: number;
  last?: TokenUsage;
};
onTokenPress?: () => void;

// 但组件内部没有任何地方渲染 tokenUsage
// Calculator 图标被导入但未使用
import { Calculator, ... } from 'lucide-react-native';
```

**影响**：用户需要额外点击进入 Token 统计弹窗才能查看使用量，体验不连贯。

---

#### 问题2：缺少上下文窗口超限检测与警告

**现象**：
- 系统没有向用户展示"当前上下文使用量 vs 模型上下文限制"的对比
- 用户无法判断会话是否接近或超出模型的上下文窗口限制
- 没有在接近限制时提供预警

**根本原因**：
1. **没有存储模型上下文限制**：系统未存储各模型的 `context_window` 信息（如 GPT-4 的 128K、Claude 的 200K）
2. **统计维度不匹配**：当前统计的是"计费用 Token"，而非"上下文窗口使用量"
3. **缺少超限预警机制**：没有在接近限制时提醒用户

**影响**：用户可能在不知情的情况下超出上下文限制，导致早期消息被截断或模型行为异常。

---

### 🟡 P2 - 中优先级问题

#### 问题3：StatsPanel 数据来源不一致

**文件对比**：

| 组件 | 数据来源 | 计算方式 |
|------|----------|----------|
| [TokenStatsModal](file:///Users/promenar/Codex/Nexara/src/features/chat/components/TokenStatsModal.tsx#L20-L33) | `session.stats.billing` | 直接读取会话统计 |
| [StatsPanel](file:///Users/promenar/Codex/Nexara/src/features/chat/components/SessionSettingsSheet/StatsPanel.tsx#L18-L20) | `messages[].tokens` | 累加每条消息的 tokens |

**代码证据**：
```typescript
// StatsPanel.tsx - 从消息累加
const totalTokens = messages.reduce((sum: number, m: any) => {
  return sum + (m.tokens?.input || 0) + (m.tokens?.output || 0);
}, 0);

// TokenStatsModal.tsx - 从会话统计读取
const stats: BillingUsage = useMemo(() => {
  if (session.stats?.billing) {
    return session.stats.billing;
  }
  // Fallback...
}, [session.stats]);
```

**风险**：两个组件可能显示不同的数值，造成用户困惑。

---

#### 问题4：全局统计与会话统计不同步

**现象**：
- `useTokenStatsStore`（全局）和 `session.stats`（会话级）分别存储
- 重置会话统计时，全局统计不会自动减少
- 重置全局统计时，会话统计不受影响

**代码证据**：
```typescript
// TokenStatsModal.tsx - 重置会话统计
const handleReset = () => {
  const emptyBilling: BillingUsage = { /* ... */ };
  updateSession(session.id, { stats: { totalTokens: 0, billing: emptyBilling } });
  // ⚠️ 未同步更新 useTokenStatsStore
};

// token-stats-store.ts - 重置全局统计
resetGlobalStats: () =>
  set({
    globalTotal: JSON.parse(JSON.stringify(initialBilling)),
    byModel: {},
    // ⚠️ 未同步更新各会话的 stats
  }),
```

**风险**：数据不一致，用户看到的"全局总计"与"所有会话累加"可能不匹配。

---

#### 问题5：Token 估算精度问题

**文件**：[src/features/chat/utils/token-counter.ts](file:///Users/promenar/Codex/Nexara/src/features/chat/utils/token-counter.ts#L8-L35)

**估算规则**：
- CJK 字符：1.5 tokens/字
- 英文单词：1.3 tokens/词

**问题**：
1. 不同模型的 tokenizer 差异很大（GPT-4 vs Claude vs Gemini）
2. 代码、Markdown、特殊符号的 tokenization 行为不同
3. 与实际 tokenizer（如 tiktoken）可能存在 20-50% 的偏差

**代码证据**：
```typescript
export function estimateTokens(text: string): number {
  // CJK 字符使用 1.5 系数
  const totalTokens = Math.ceil(cjkCount * 1.5) + englishTokens;
  return totalTokens;
}
```

**风险**：当 API 未返回 `usage` 字段时，估算值可能不准确，影响用户判断。

---

### 🟢 P3 - 低优先级问题

#### 问题6：RAG Token 统计可能不完整

**现象**：
- `ragUsage` 只在部分场景下被设置
- RAG 相关的 Token 消耗（查询重写、Embedding、摘要）可能未被完整追踪

**代码证据**：
```typescript
// chat-store.ts
let ragUsage: { ragSystem: number; isEstimated: boolean } | undefined;
// ragUsage 只在特定条件下被赋值...
```

---

## 三、用户上下文超限检测能力评估

### 3.1 当前能力

| 能力 | 状态 | 说明 |
|------|------|------|
| 显示会话 Token 总计 | ✅ 支持 | TokenStatsModal 显示 |
| 显示输入/输出/RAG 分类 | ✅ 支持 | BillingUsage 结构 |
| 显示估算标记 | ✅ 支持 | `isEstimated` 字段 |
| 显示模型上下文限制 | ❌ 不支持 | 未存储模型信息 |
| 显示上下文使用百分比 | ❌ 不支持 | 无限制数据 |
| 超限预警 | ❌ 不支持 | 无预警机制 |
| 输入栏快速查看 | ❌ 不支持 | UI 未渲染 |

### 3.2 用户无法评估的原因

1. **统计维度不匹配**：显示的是"计费用 Token"，而非"上下文窗口使用量"
2. **缺少限制信息**：没有模型上下文窗口大小的数据
3. **UI 不完整**：输入栏的 tokenUsage 未渲染

---

## 四、改进建议

### 4.1 短期改进（P1）

#### 建议1：完善 ChatInput 的 Token 显示

**方案**：在输入栏顶部栏添加 Token 使用量显示

```typescript
// ChatInput.tsx - 在 topBar 中添加
<View style={styles.topBar}>
  {/* 现有的模型选择器 */}
  <TouchableOpacity onPress={() => setShowSettingsSheet(true)}>
    <Cpu size={10} color={agentColor} />
    <Typography>{currentModel || '选择模型'}</Typography>
  </TouchableOpacity>

  <View style={{ flex: 1 }} />

  {/* 🆕 新增：Token 使用量显示 */}
  {tokenUsage && (
    <TouchableOpacity onPress={handleTokenPress} style={styles.tokenBar}>
      <Calculator size={10} color={isDark ? '#a1a1aa' : '#6b7280'} />
      <Typography style={{ fontSize: 9, marginLeft: 4 }}>
        {formatTokenCount(tokenUsage.total)}
      </Typography>
    </TouchableOpacity>
  )}

  {/* 现有的工作区按钮 */}
  <TouchableOpacity onPress={() => setShowWorkspaceSheet(true)}>
    <FolderOpen size={10} />
    <Typography>工作区</Typography>
  </TouchableOpacity>
</View>
```

---

#### 建议2：添加上下文窗口超限检测

**方案**：

1. **扩展模型配置**：在 `ModelConfig` 中添加 `contextWindow` 字段

```typescript
// types/chat.ts
interface ModelConfig {
  id: string;
  name: string;
  type: 'chat' | 'reasoning' | 'image' | 'embedding' | 'rerank';
  contextWindow?: number;  // 🆕 模型上下文窗口大小
  // ...
}
```

2. **在 TokenStatsModal 中显示上下文使用百分比**

```typescript
// TokenStatsModal.tsx
const contextWindow = modelConfig?.contextWindow || 4096;  // 默认 4K
const usagePercent = (stats.total / contextWindow) * 100;

// UI 中显示进度条和百分比
<View style={styles.contextBar}>
  <Typography>上下文使用</Typography>
  <Typography>{usagePercent.toFixed(1)}%</Typography>
</View>
<ProgressBar progress={usagePercent / 100} />

// 超限预警
{usagePercent > 80 && (
  <WarningBanner message="接近上下文限制，早期消息可能被截断" />
)}
```

---

### 4.2 中期改进（P2）

#### 建议3：统一数据来源

**方案**：将 StatsPanel 改为使用 `session.stats.billing` 作为数据来源

```typescript
// StatsPanel.tsx
const stats = session?.stats?.billing;
const totalTokens = stats?.total || 0;
const inputTokens = stats?.chatInput.count || 0;
const outputTokens = stats?.chatOutput.count || 0;
```

---

#### 建议4：同步全局与会话统计

**方案**：在重置时同步更新两处数据

```typescript
// TokenStatsModal.tsx
const handleReset = () => {
  // 1. 获取当前会话的统计值
  const currentStats = session.stats?.billing;
  
  // 2. 从全局统计中减去
  if (currentStats && session.modelId) {
    useTokenStatsStore.getState().subtractUsage(session.modelId, currentStats);
  }
  
  // 3. 重置会话统计
  updateSession(session.id, { stats: { totalTokens: 0, billing: emptyBilling } });
};
```

---

### 4.3 长期改进（P3）

#### 建议5：引入精确 Token 计数

**方案**：
- 集成 `js-tiktoken` 库进行精确计数
- 按模型类型选择对应的 tokenizer

---

## 五、总结

### 5.1 审计结论

Token 统计模块的**核心统计机制是合理的**：
- ✅ 区分 API 真实值和本地估算值
- ✅ 支持会话级和全局级双重统计
- ✅ 包含 RAG 系统开销追踪

但存在以下**关键缺陷**：
- ❌ 输入栏 Token 显示未实现
- ❌ 缺少上下文窗口超限检测
- ❌ 数据来源不一致

### 5.2 风险评级

| 风险类型 | 评级 | 说明 |
|----------|------|------|
| 数据准确性 | 🟡 中 | 估算值可能偏差 20-50% |
| 数据一致性 | 🟡 中 | 多处数据来源可能不同步 |
| 用户体验 | 🔴 高 | 无法评估上下文是否超限 |
| 功能完整性 | 🟡 中 | 部分 UI 未实现 |

### 5.3 改进优先级

1. **P1 - 立即修复**：完善 ChatInput 的 Token 显示
2. **P1 - 立即修复**：添加上下文窗口超限检测
3. **P2 - 短期改进**：统一数据来源
4. **P2 - 短期改进**：同步全局与会话统计
5. **P3 - 长期优化**：引入精确 Token 计数

---

## 附录：相关文件清单

| 文件 | 职责 |
|------|------|
| [src/types/chat.ts](file:///Users/promenar/Codex/Nexara/src/types/chat.ts) | Token 数据结构定义 |
| [src/store/token-stats-store.ts](file:///Users/promenar/Codex/Nexara/src/store/token-stats-store.ts) | 全局 Token 统计存储 |
| [src/store/chat/post-processor.ts](file:///Users/promenar/Codex/Nexara/src/store/chat/post-processor.ts) | 统计更新入口 |
| [src/features/chat/utils/token-counter.ts](file:///Users/promenar/Codex/Nexara/src/features/chat/utils/token-counter.ts) | Token 估算工具 |
| [src/features/chat/components/TokenStatsModal.tsx](file:///Users/promenar/Codex/Nexara/src/features/chat/components/TokenStatsModal.tsx) | 会话统计弹窗 |
| [src/features/chat/components/ChatInput.tsx](file:///Users/promenar/Codex/Nexara/src/features/chat/components/ChatInput.tsx) | 输入栏组件 |
| [src/features/chat/components/SessionSettingsSheet/StatsPanel.tsx](file:///Users/promenar/Codex/Nexara/src/features/chat/components/SessionSettingsSheet/StatsPanel.tsx) | 会话设置统计面板 |
| [app/settings/token-usage.tsx](file:///Users/promenar/Codex/Nexara/app/settings/token-usage.tsx) | 全局统计页面 |