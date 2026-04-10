# 项目设置面板架构参考 (v2.0)

> **更新日期**: 2026-01-21  
> **说明**: Nexara 包含四种层级的设置面板，分别对应全局、超级助手、普通助手及单会话配置。

---

## 核心设置面板对应关系

### 1. 全局设置面板 (Global Settings)
*   **入口**: 底部导航栏 "设置" (Settings) 标签
*   **主体路由**: `app/(tabs)/settings.tsx`
*   **结构**: 
    - **应用设置 (App Settings)**: 语言、主题、Haptics、备份及全局 RAG 配置。
    - **服务商管理 (Providers Hub)**: API Key 配置、模型选择、连通性测试。
*   **RAG 集成**: 
    - 使用 `GlobalRagConfigPanel.tsx`。
    - 入口为“应用设置”中的“RAG 知识库配置”分组。
*   **高级调试**: `RagDebugPanel.tsx` (查看全域向量库统计)。

### 2. 超级助手面板 (Super Assistant Hub)
*   **入口**: 超级助手会话界面 → 右上角“设置”图标
*   **主体路由**: `app/chat/super_assistant/settings.tsx`
*   **特色**: 拥有全局 RAG 权限的专属 Agent。
*   **RAG 集成**:
    - `AgentRagConfigPanel.tsx` (基础配置)。
    - `AgentAdvancedRetrievalPanel.tsx` (高级检索参数)。
*   **跳转路由**:
    - `rag-config.tsx`: 细粒度 RAG 设置。
    - `advanced-retrieval.tsx`: 相似度阈值、检索深度等。

### 3. 助手编辑面板 (Agent Edit)
*   **入口**: 侧边栏/首页助手列表 → 长按或点击“编辑” (或 `ModelSettingsModal`)
*   **主体路由**: `app/chat/agent/edit/[agentId].tsx`
*   **用途**: 定义特定 Agent 的人格、关联模型及其默认工具/RAG 范围。
*   **RAG 集成**:
    - `AgentRagConfigPanel.tsx` (关联特定文件夹/文档)。
    - 支持通过 `rag-config` 路由进行深入配置。

### 4. 会话级设置 (Session Settings)
*   **入口**: 普通对话界面 → 右上角“设置”图标
*   **主体路由**: `app/chat/[id]/settings.tsx`
*   **用途**: 对当前会话进行即时调整（推理参数、临时开启/关闭联网）。
*   **RAG 集成**:
    - 实时查看/调整当前会话的检索范围。
    - 优先级高于助手默认设置。

---

## RAG 组件职责分工

| 组件名称 | 职责 | 常用场景 |
| :--- | :--- | :--- |
| **GlobalRagConfigPanel** | 管理全域向量索引、清理孤儿数据、触发全局同步 | 全局设置页 |
| **AgentRagConfigPanel** | 配置 Agent 关联的文档 ID 和文件夹 ID | 助手编辑页、超级助手页 |
| **AgentAdvancedRetrievalPanel** | 调整相似度阈值 (threshold)、检索 Top-K | 高级 RAG 配置页 |
| **RagDebugPanel** | 实时查看 SQLite 中 `vectors` / `kg_nodes` 统计 | 开发者调试/全局设置 |

---

## 命名规范与文件路径速查

| 面板 | 组件/文件路径 | 核心 Store |
| :--- | :--- | :--- |
| **全局设置** | `app/(tabs)/settings.tsx` | `settings-store.ts`, `api-store.ts` |
| **服务商列表** | `src/features/settings/components/ProviderList.tsx` | `api-store.ts` |
| **服务商详情** | `src/features/settings/components/ProviderModal.tsx` | `api-store.ts` |
| **超级助手** | `app/chat/super_assistant/settings.tsx` | `spa-store.ts`, `chat-store.ts` |
| **助手编辑** | `app/chat/agent/edit/[agentId].tsx` | `agent-store.ts` |
| **会话设置** | `app/chat/[id]/settings.tsx` | `chat-store.ts` |

---

## 常见维护任务

1. **新增全局开关**: 在 `settings-store.ts` 添加状态，并在 `app/(tabs)/settings.tsx` 的 App 标签页中添加 `SettingsSwitchItem`。
2. **新增模型参数**: 在 `ModelSettingsModal.tsx` 或 `InferenceSettings.tsx` 中添加 UI。
3. **修复 RAG 统计不刷新**: 检查 `GlobalRagConfigPanel.tsx` 的 `useFocusEffect` 触发逻辑。
