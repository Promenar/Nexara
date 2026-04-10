# Nexara 数据模式与核心类型定义

> **角色**: 数据结构的单一事实来源 (SSOT)
> **状态**: 实时同步 (`src/types` 与 `src/store`)
> **法则**: 任何对 `src/types/*.ts` 或 `src/store/*.ts` 的修改**必须**同步更新此处。

---

## 1. 核心类型 (`src/types/*.ts`)

### 1.1 会话 (Session) - `chat.ts`
| 字段 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `string` (UUID) | 唯一会话识别码 |
| `agentId` | `string` | 关联智能体 ID |
| `title` | `string` | 对话标题 |
| `messages` | `Message[]` | 消息历史记录 (含工具结果/步骤) |
| `executionMode` | `'auto' | 'semi' | 'manual'` | 代理循环执行模式 |
| `loopStatus` | `'idle' | 'running' | ...` | 当前执行状态 |
| `continuationBudget` | `number` | 续杯步数额度 |

### 1.2 消息 (Message) - `chat.ts`
| 字段 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `string` | 唯一消息 ID |
| `role` | `'user' | 'assistant' | ...` | 发送者角色 |
| `content` | `string` | Markdown 内容 |
| `reasoning` | `string?` | 思维链内容 |
| `tool_calls` | `ToolCall[]?` | 挂载的工具调用 |
| `toolResults` | `ToolResultArtifact[]?` | 工具执行产物 (渲染用) |
| `loopCount` | `number?` | 当前执行轮次 |

### 1.3 生成内容 (Artifact) - `artifact.ts`
| 字段 | 类型 | 描述 |
| :--- | :--- | :--- |
| `id` | `string` | 唯一产物 ID |
| `type` | `'echarts' | 'mermaid' | ...` | 产物渲染类型 |
| `title` | `string` | 显示标题 |
| `content` | `string` | 原始代码/内容 |
| `sessionId` | `string` | 所属会话 ID |

---

## 2. 全局状态存储 (`src/store/*.ts`)

### 2.1 ArtifactStore (`artifact-store.ts`)
*Workspace 生成内容持久化中心。*
- **状态**: `artifacts`: `Artifact[]`
- **核心动作**: `createArtifact`, `updateArtifact`, `deleteArtifact`

### 2.2 TokenStatsStore (`token-stats-store.ts`)
*全局使用量与计费审计。*
- **状态**: `globalTotal`, `byProvider`, `byModel`
- **动作**: `recordUsage`, `resetGlobalStats`

### 2.3 ChatStore & RagStore
(详见过往版本，重点在于与 SQLite 同步逻辑)

---

## 3. 数据库模式 (SQLite)

### `artifacts` (产物表) - Migration 11
```sql
CREATE TABLE artifacts (
  id TEXT PRIMARY KEY NOT NULL,
  type TEXT NOT NULL,
  title TEXT NOT NULL,
  content TEXT NOT NULL,
  preview_image TEXT,
  session_id TEXT NOT NULL,
  message_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  tags TEXT,
  FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
);
```

### `sessions` & `messages` (已包含 JSON 元数据扩展)
元数据字段包含 `metadata JSON`，用于存储 `options`, `stats`, `tool_calls`, `reasoning` 等扩展字段。
