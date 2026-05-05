# OpenCode 全量审计指令模板

> **生成时间**: 2026-05-04 11:51
> **审计范围**: B1-D3 + Session A + Session B + Session C + Session D 全量审计
> **用途**: 新建 OpenCode 会话粘贴执行

打开 artifact 文件 `full-audit-opencode-template.md` 获取完整指令模板。

## 快速概览 — 已产出文件统计

### 后端逻辑层 (B1-D3, 93 main + 23 test)
- data/local/db/ → 33 文件 (16 Entity + 15 DAO + Converters + Database)
- data/model/ → 4 文件
- data/remote/ → 12 文件 (parser 4 + protocol 4 + provider 1 + 其他 3)
- data/rag/ → 14 文件
- data/repository/ → 4 文件
- ui/chat/manager/ → 6 文件

### ViewModel 层 (Session A, 新增 4 + 修改 4 + 删除 3)
- ChatViewModel + AgentHubViewModel + SessionListViewModel + RagViewModel
- ChatScreen/AgentHubScreen/AgentSessionsScreen 重构
- NexaraStateStore/SseClient/旧Models.kt 删除

### 通用 UI 组件 (Session B, 新增 9)
- NexaraConfirmDialog/BottomSheet/Snackbar/MarkdownText/LoadingIndicator/SearchBar/CollapsibleSection/PageLayout/AgentAvatar

### 设置页面 (Session C, 新增 6 + 修改 2)
- ProviderForm/ProviderModels/TokenUsage/SearchConfig/Skills/Theme
- NavGraph + UserSettingsHomeScreen 更新

### RAG UI (Session D, 新增 5 + 修改 3)
- RagViewModel + RagFolderScreen + 4 子组件
- RagHomeScreen/GlobalRagConfigScreen/AdvancedRetrievalScreen 重构

### 总文件数
- **预计 93 + 4 + 9 + 6 + 5 = ~117 main 源码**
- **23 test 文件**
- **~140 总 Kotlin 文件**
