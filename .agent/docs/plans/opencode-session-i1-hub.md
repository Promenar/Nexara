# OpenCode 指令模板 — Session I1: hub 模块 i18n

> **项目**: Nexara Native UI
> **工作目录**: `k:/Nexara/native-ui/`
> **Session**: I1 — hub 模块 9 个文件 i18n
> **前置依赖**: I0 (基础设施 + common 组件已完成)

---

## 你的任务

将 `ui/hub/` 目录下全部 9 个 .kt 文件中的硬编码文本外部化为 string resource。

**核心原则**:
- **中文硬编码的文件**: 替换为 `stringResource()`，默认英文 strings.xml 中写英文翻译，zh-rCN 中写中文
- **英文硬编码的文件**: 同样替换为 `stringResource()`
- 所有文件修改后必须在**英文模式**下显示英文，**中文模式**下显示中文
- 专有名词保持不变: "Nexara", "RAG", "Token", "WebDAV", "GGUF", "MCP", "API Key" 等

---

## 前置检查

确认 `app/src/main/res/values/strings.xml` 和 `values-zh-rCN/strings.xml` 已存在（I0 已创建）。

---

## 需修改的文件清单 (9个)

### 1. AgentHubScreen.kt — 当前中英混合

需要在两个 strings.xml 中追加以下 key:

| key | 英文 | 中文 |
|-----|------|------|
| `hub_title` | Agents | 智能助手 |
| `hub_subtitle` | Your AI Assistant Team | 你的智能助手团队 |
| `hub_search_placeholder` | Search agents… | 搜索助手… |
| `hub_btn_add_agent` | Add Agent | 添加助手 |
| `hub_fab_super` | Super Assistant | 超级助手 |
| `hub_empty_title` | No Agents Yet | 还没有助手 |
| `hub_empty_subtitle` | Tap + to create your first AI agent | 点击 + 创建你的第一个 AI 助手 |
| `hub_dialog_add_title` | Add New Agent | 创建新助手 |
| `hub_dialog_label_name` | Name | 名称 |
| `hub_dialog_label_desc` | Description | 描述 |
| `hub_dialog_label_model` | Model ID | 模型 ID |
| `hub_dialog_label_prompt` | System Prompt | 系统提示词 |
| `hub_dialog_btn_add` | Add | 添加 |
| `hub_dialog_btn_cancel` | Cancel | 取消 |
| `hub_action_pin` | Pin | 置顶 |
| `hub_action_unpin` | Unpin | 取消置顶 |
| `hub_action_delete` | Delete | 删除 |

### 2. AgentSessionsScreen.kt — 当前中文

| key | 英文 | 中文 |
|-----|------|------|
| `sessions_search_placeholder` | Search sessions… | 搜索会话… |
| `sessions_empty_title` | No Conversations Yet | 还没有对话 |
| `sessions_empty_subtitle` | Start your first conversation | 开始第一个吧 |
| `sessions_btn_new` | New Session | 新建会话 |
| `sessions_count_format` | %1$d sessions | %1$d 个会话 |
| `sessions_cd_settings` | Agent Settings | 助手设置 |
| `sessions_cd_back` | Back | 返回 |
| `sessions_cd_new` | New session | 新建会话 |
| `sessions_tag_session` | SESSION | 会话 |
| `sessions_tag_pinned` | PINNED | 已置顶 |

### 3. AgentEditScreen.kt — 当前全中文 (25处)

| key | 英文 | 中文 |
|-----|------|------|
| `agent_edit_title` | Edit Agent | 编辑助手 |
| `agent_edit_section_basic` | Basic Info | 基本信息 |
| `agent_edit_label_name` | Name | 名称 |
| `agent_edit_placeholder_name` | Enter agent name… | 输入助手名称… |
| `agent_edit_label_desc` | Description | 描述 |
| `agent_edit_placeholder_desc` | Describe this agent… | 描述这个助手… |
| `agent_edit_section_appearance` | Appearance | 外观 |
| `agent_edit_section_personality` | Personality | 性格 |
| `agent_edit_prompt_label` | System Prompt | 系统提示词 |
| `agent_edit_prompt_placeholder` | Define agent behavior… | 定义助手行为… |
| `agent_edit_prompt_configured` | Configured | 已配置 |
| `agent_edit_prompt_not_set` | Not Set | 未设置 |
| `agent_edit_section_model` | Model Configuration | 模型配置 |
| `agent_edit_current_model` | Current Model | 当前模型 |
| `agent_edit_section_knowledge` | Knowledge | 知识 |
| `agent_edit_rag_config` | RAG Configuration | RAG 配置 |
| `agent_edit_rag_desc` | Chunking, memory, vectorization | 分块、记忆、向量化 |
| `agent_edit_advanced_retrieval` | Advanced Retrieval | 高级检索 |
| `agent_edit_retrieval_desc` | Reranking, query rewriting, hybrid search | 重排序、查询改写、混合搜索 |
| `agent_edit_section_danger` | Danger Zone | 危险区 |
| `agent_edit_delete_btn` | Delete Agent | 删除助手 |
| `agent_edit_delete_title` | Delete Agent | 删除助手 |
| `agent_edit_delete_message` | Are you sure you want to delete this agent? This action cannot be undone. | 确定要删除此助手吗？此操作无法撤销。 |
| `agent_edit_delete_confirm` | Delete | 删除 |
| `agent_edit_icon_custom` | Custom | 自定义 |
| `agent_edit_icon_upload` | Upload Image | 上传图片 |

### 4. AgentRagConfigScreen.kt — 当前全中文 (17处)

| key | 英文 | 中文 |
|-----|------|------|
| `agent_rag_title` | RAG Configuration | RAG 配置 |
| `agent_rag_status_inherited` | Inherited from Global | 继承全局 |
| `agent_rag_status_custom` | Custom | 自定义 |
| `agent_rag_reset_title` | Reset Configuration | 重置配置 |
| `agent_rag_reset_message` | Are you sure you want to reset to global defaults? All custom settings will be lost. | 确定要重置为全局默认配置吗？所有自定义设置将丢失。 |
| `agent_rag_reset_confirm` | Reset | 重置 |
| `agent_rag_section_chunk` | Document Chunking | 文档分块 |
| `agent_rag_chunk_size` | Chunk Size | 分块大小 |
| `agent_rag_overlap` | Chunk Overlap | 分块重叠 |
| `agent_rag_section_memory` | Memory | 记忆 |
| `agent_rag_memory_chunk` | Memory Chunk Size | 记忆分块大小 |
| `agent_rag_section_context` | Context | 上下文 |
| `agent_rag_active_window` | Active Context Window | 活跃上下文窗口 |
| `agent_rag_summary_threshold` | Summary Trigger Threshold | 摘要触发阈值 |
| `agent_rag_section_summary` | Summary Template | 摘要模板 |
| `agent_rag_summary_placeholder` | Enter summary template… | 输入摘要模板… |
| `agent_rag_summary_configured` | Configured | 已配置 |
| `agent_rag_summary_default` | Using Default | 使用默认 |

### 5. AgentAdvancedRetrievalScreen.kt — 当前全中文 (20处)

| key | 英文 | 中文 |
|-----|------|------|
| `agent_retrieval_title` | Advanced Retrieval | 高级检索 |
| `agent_retrieval_section_memory` | Memory Retrieval | 记忆检索 |
| `agent_retrieval_memory_limit` | Memory Limit | 记忆数量限制 |
| `agent_retrieval_memory_threshold` | Memory Similarity Threshold | 记忆相似度阈值 |
| `agent_retrieval_section_document` | Document Retrieval | 文档检索 |
| `agent_retrieval_doc_limit` | Document Limit | 文档数量限制 |
| `agent_retrieval_doc_threshold` | Document Similarity Threshold | 文档相似度阈值 |
| `agent_retrieval_section_rerank` | Reranking | 重排序 |
| `agent_retrieval_rerank_enable` | Enable Reranking | 启用重排序 |
| `agent_retrieval_recall_count` | Recall Count | 召回数量 |
| `agent_retrieval_final_count` | Final Result Count | 最终结果数量 |
| `agent_retrieval_section_rewrite` | Query Rewriting | 查询改写 |
| `agent_retrieval_rewrite_enable` | Enable Query Rewriting | 启用查询改写 |
| `agent_retrieval_strategy_hyde` | HyDE | HyDE |
| `agent_retrieval_strategy_multi` | Multi-Query | 多查询 |
| `agent_retrieval_strategy_expansion` | Expansion | 扩展 |
| `agent_retrieval_variant_count` | Variant Count | 变体数量 |
| `agent_retrieval_section_hybrid` | Hybrid Search | 混合搜索 |
| `agent_retrieval_vector_weight` | Vector Weight | 向量权重 |
| `agent_retrieval_bm25_boost` | BM25 Boost | BM25 增益 |

### 6. AgentHubViewModel.kt

检查是否有用户可见的硬编码文本（错误消息等），如有则一并处理。

### 7. AgentEditViewModel.kt

检查是否有用户可见的硬编码文本，如有则一并处理。

### 8. SessionListViewModel.kt

检查是否有用户可见的硬编码文本，如有则一并处理。

### 9. UserSettingsHomeScreen.kt — 当前全中文 (51处)

这是**最大的文件**，需追加约 55 个 key:

| key | 英文 | 中文 |
|-----|------|------|
| `settings_title` | Settings | 设置 |
| `settings_tab_app` | App | 应用 |
| `settings_tab_provider` | Providers | 提供商 |
| `settings_section_general` | General | 通用 |
| `settings_language` | Language | 语言 |
| `settings_language_zh` | Chinese | 中文 |
| `settings_language_en` | English | English |
| `settings_appearance` | Appearance | 外观 |
| `settings_theme_light` | Light | 浅色 |
| `settings_theme_system` | System | 跟随系统 |
| `settings_theme_dark` | Dark | 深色 |
| `settings_theme_color` | Theme Color | 主题色 |
| `settings_theme_color_desc` | Custom accent color | 自定义强调色 |
| `settings_haptic` | Haptic Feedback | 触觉反馈 |
| `settings_section_model_presets` | Model Presets | 模型预设 |
| `settings_model_summary` | Summary Model | 摘要模型 |
| `settings_model_image` | Image Model | 图像模型 |
| `settings_model_embedding` | Embedding Model | 嵌入模型 |
| `settings_model_rerank` | Rerank Model | 重排模型 |
| `settings_not_set` | Not Set | 未设置 |
| `settings_section_knowledge` | Knowledge Management | 知识管理 |
| `settings_rag_config` | RAG Configuration | RAG 配置 |
| `settings_rag_desc` | Chunking, memory, vectorization | 分块、记忆、向量化 |
| `settings_advanced_retrieval` | Advanced Retrieval | 高级检索 |
| `settings_retrieval_desc` | Reranking, query rewriting, hybrid search | 重排序、查询改写、混合搜索 |
| `settings_token_usage` | Token Usage | Token 用量 |
| `settings_section_tools` | Tools | 工具 |
| `settings_web_search` | Web Search | Web 搜索 |
| `settings_workbench` | Workbench | 工作台 |
| `settings_workbench_desc` | Portable server · Experimental | 便携服务器 · 实验性 |
| `settings_skills` | Skills | 技能 |
| `settings_local_models` | Local Models | 本地模型 |
| `settings_section_data` | Data | 数据 |
| `settings_backup` | Backup | 备份 |
| `settings_backup_desc` | Local / WebDAV sync | 本地 / WebDAV 同步 |
| `settings_logs` | Logs | 日志 |
| `settings_logs_desc` | Record debug info and error logs | 记录调试信息与错误日志 |
| `settings_export_logs` | Export Logs | 导出日志 |
| `settings_export_logs_desc` | Share log files | 分享日志文件 |
| `settings_section_about` | About | 关于 |
| `settings_about_nexara` | About Nexara | 关于 Nexara |
| `settings_version` | Version %1$s | 版本 %1$s |
| `settings_provider_empty` | No providers configured yet.\nTap the button above to add one. | 暂无提供商配置\n点击上方按钮添加 |
| `settings_add_provider` | Add Provider | 添加提供商 |
| `settings_delete_provider_title` | Delete Provider | 删除提供商 |
| `settings_delete_provider_message` | Are you sure you want to delete this provider? This action cannot be undone. | 确定要删除此提供商吗？此操作无法撤销。 |
| `settings_manage_models` | Manage Models | 管理模型 |
| `settings_edit_name` | Edit Name | 编辑名称 |
| `settings_edit_name_placeholder` | Enter your display name | 输入你的显示名称 |

---

## 修改模式

### 每个文件的标准修改流程:

1. **添加 import**:
```kotlin
import androidx.compose.ui.res.stringResource
import com.promenar.nexara.R
```

2. **替换硬编码**:
```kotlin
// 修改前:
Text("编辑助手")
// 修改后:
Text(stringResource(R.string.agent_edit_title))
```

3. **参数传递**: 如果文本是通过参数传入子组件的，在最外层 Composable 中解析:
```kotlin
// 修改前:
AgentSessionHeader(agentName = agentName, ...)
// agentName 来自 ViewModel，已经是动态数据，无需替换

// 但固定 label 需要替换:
Text("会话数: ${sessions.size}")
// 修改后:
Text(stringResource(R.string.sessions_count_format, sessions.size))
```

---

## 完成标准

- [ ] AgentHubScreen.kt — 所有中英混合文本替换完毕
- [ ] AgentSessionsScreen.kt — 所有中文文本替换完毕
- [ ] AgentEditScreen.kt — 所有 25 处中文文本替换完毕
- [ ] AgentRagConfigScreen.kt — 所有 17 处中文文本替换完毕
- [ ] AgentAdvancedRetrievalScreen.kt — 所有 20 处中文文本替换完毕
- [ ] UserSettingsHomeScreen.kt — 所有 51 处中文文本替换完毕
- [ ] ViewModel 文件中可见文本已处理
- [ ] `values/strings.xml` 新增所有 hub_ 和 settings_ 前缀 key
- [ ] `values-zh-rCN/strings.xml` 新增所有对应中文翻译
- [ ] 编译通过: `./gradlew assembleDebug` 无错误
