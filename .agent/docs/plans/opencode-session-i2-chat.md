# OpenCode 指令模板 — Session I2: chat 模块 i18n

> **项目**: Nexara Native UI
> **工作目录**: `k:/Nexara/native-ui/`
> **Session**: I2 — chat 模块 5 个主要 Screen 文件 i18n
> **前置依赖**: I0 (基础设施已完成)

---

## 你的任务

将 `ui/chat/` 目录下 5 个主要 Screen 文件中的全部英文硬编码文本外部化为 string resource，提供中文翻译。

**当前状态**: 这些文件全部是英文硬编码。修改后英文模式下保持英文，中文模式下显示中文。

---

## 需修改的文件 (5个主要 Screen)

### 1. ChatScreen.kt (~547行, ~12处)

| key | 英文 | 中文 |
|-----|------|------|
| `chat_title_new` | New Chat | 新对话 |
| `chat_menu_session_settings` | Session Settings | 会话设置 |
| `chat_menu_token_stats` | Token Stats | Token 统计 |
| `chat_menu_super_assistant` | Super Assistant | 超级助手 |
| `chat_menu_export` | Export Chat | 导出对话 |
| `chat_input_placeholder` | Message %1$s… | 发消息给%1$s… |
| `chat_input_placeholder_default` | Message… | 发消息… |
| `chat_cd_back` | Back | 返回 |
| `chat_cd_workspace` | Workspace | 工作区 |
| `chat_cd_options` | Options | 选项 |
| `chat_cd_stop` | Stop | 停止 |
| `chat_cd_send` | Send | 发送 |
| `chat_scroll_to_bottom` | Scroll to bottom | 回到底部 |

### 2. SessionSettingsScreen.kt (~559行, ~35处)

| key | 英文 | 中文 |
|-----|------|------|
| `session_settings_title` | Settings | 设置 |
| `session_settings_active_agent` | Active Agent | 当前助手 |
| `session_settings_export` | Export Session | 导出会话 |
| `session_settings_section_info` | Session Info | 会话信息 |
| `session_settings_title_label` | Session Title | 会话标题 |
| `session_settings_title_placeholder` | Enter session title… | 输入会话标题… |
| `session_settings_ai_title` | AI Generated Title | AI 生成标题 |
| `session_settings_section_inference` | Inference Parameters | 推理参数 |
| `session_settings_temperature` | Temperature | 温度 |
| `session_settings_top_p` | Top P | Top P |
| `session_settings_max_tokens` | Max Tokens | 最大 Token 数 |
| `session_settings_label_precise` | Precise | 精确 |
| `session_settings_label_creative` | Creative | 创意 |
| `session_settings_label_focused` | Focused | 聚焦 |
| `session_settings_label_diverse` | Diverse | 多样 |
| `session_settings_label_short` | Short | 短 |
| `session_settings_label_long` | Long | 长 |
| `session_settings_section_rag` | RAG Settings | RAG 设置 |
| `session_settings_memory` | Long-term Memory | 长期记忆 |
| `session_settings_memory_desc` | Semantic memory for context across sessions | 跨会话语义记忆上下文 |
| `session_settings_kg` | Knowledge Graph Extraction | 知识图谱抽取 |
| `session_settings_kg_desc` | Extract entities and relationships from conversations | 从对话中提取实体和关系 |
| `session_settings_kb` | Knowledge Base | 知识库 |
| `session_settings_kb_desc` | Enable document retrieval for this session | 为此会话启用文档检索 |
| `session_settings_section_prompt` | Custom Prompt | 自定义提示词 |
| `session_settings_prompt_editor_title` | Custom Prompt | 自定义提示词 |
| `session_settings_prompt_placeholder` | Enter custom system prompt… | 输入自定义系统提示词… |
| `session_settings_delete_title` | Delete Session | 删除会话 |
| `session_settings_delete_message` | Are you sure you want to delete this session? This action cannot be undone. | 确定要删除此会话吗？此操作无法撤销。 |
| `session_settings_delete_confirm` | Delete | 删除 |
| `session_settings_cd_back` | Back | 返回 |
| `session_settings_configured` | Configured | 已配置 |
| `session_settings_not_set` | Not Set | 未设置 |
| `session_settings_docs_attached` | %1$d documents attached | 已附加 %1$d 个文档 |
| `session_settings_add_docs` | Add Documents | 添加文档 |

### 3. SessionSettingsSheet.kt (~590行, ~30处)

| key | 英文 | 中文 |
|-----|------|------|
| `sheet_title` | Session Settings | 会话设置 |
| `sheet_tab_model` | Model | 模型 |
| `sheet_tab_thinking` | Thinking | 思考级别 |
| `sheet_tab_stats` | Stats | 统计 |
| `sheet_tab_tools` | Tools | 工具 |
| `sheet_thinking_minimal` | Minimal | 最小 |
| `sheet_thinking_minimal_desc` | Fast response, minimal compute | 快速响应，最低计算 |
| `sheet_thinking_low` | Low | 低 |
| `sheet_thinking_low_desc` | Basic reasoning, balanced speed | 基础推理，平衡速度 |
| `sheet_thinking_medium` | Medium | 中 |
| `sheet_thinking_medium_desc` | Deep reasoning, recommended | 深度推理，推荐 |
| `sheet_thinking_high` | High | 高 |
| `sheet_thinking_high_desc` | Strongest reasoning, highest quality | 最强推理，最高质量 |
| `sheet_stats_prompt` | Prompt | 提示词 |
| `sheet_stats_completion` | Completion | 补全 |
| `sheet_stats_rag` | RAG System | RAG 系统 |
| `sheet_stats_total` | Total Tokens | 总 Token |
| `sheet_stats_reset` | Reset Stats | 重置统计 |
| `sheet_stats_disclaimer` | Estimated token usage | 预估 Token 用量 |
| `sheet_tool_time_injection` | Time Injection | 时间注入 |
| `sheet_tool_strict_mode` | Strict Mode | 严格模式 |
| `sheet_tool_skills` | Agent Skills | Agent 技能 |
| `sheet_tool_mcp_servers` | MCP Servers | MCP 服务器 |
| `sheet_tool_user_skills` | User Skills | 用户技能 |

### 4. SpaSettingsScreen.kt (~405行, ~25处)

| key | 英文 | 中文 |
|-----|------|------|
| `spa_title` | Super Assistant Settings | 超级助手设置 |
| `spa_section_fab` | FAB Appearance | FAB 外观 |
| `spa_fab_rotation` | Rotation Animation | 旋转动画 |
| `spa_fab_glow` | Glow Effect | 发光效果 |
| `spa_section_model` | Model Configuration | 模型配置 |
| `spa_section_kg` | Knowledge Graph | 知识图谱 |
| `spa_kg_enable` | Enable Knowledge Graph | 启用知识图谱 |
| `spa_kg_view` | View Full Graph | 查看完整图谱 |
| `spa_section_knowledge` | Knowledge Management | 知识管理 |
| `spa_section_context` | Context | 上下文 |
| `spa_context_window` | Active Context Window | 活跃上下文窗口 |
| `spa_section_stats` | Global Knowledge | 全局知识 |
| `spa_stat_documents` | Documents | 文档 |
| `spa_stat_sessions` | Sessions | 会话 |
| `spa_stat_vectors` | Vectors | 向量 |
| `spa_clean_ghost` | Clean Ghost Data | 清理幽灵数据 |
| `spa_export_history` | Export History | 导出历史 |
| `spa_section_danger` | Danger Zone | 危险区 |
| `spa_delete_btn` | Delete Super Assistant | 删除超级助手 |
| `spa_delete_title` | Delete Super Assistant | 删除超级助手 |
| `spa_delete_message` | Are you sure? This will clear all Super Assistant data. | 确定要删除吗？这将清除所有超级助手数据。 |

### 5. WorkspaceSheet.kt (~562行, ~20处)

| key | 英文 | 中文 |
|-----|------|------|
| `workspace_title` | Workspace | 工作区 |
| `workspace_tab_tasks` | Tasks | 任务 |
| `workspace_tab_artifacts` | Artifacts | 产物 |
| `workspace_tab_files` | Files | 文件 |
| `workspace_task_running` | Running | 运行中 |
| `workspace_task_pending` | Pending | 等待中 |
| `workspace_task_completed` | Completed | 已完成 |
| `workspace_preview` | Preview | 预览 |
| `workspace_edit` | Edit | 编辑 |
| `workspace_save` | Save | 保存 |
| `workspace_empty_tasks` | No tasks yet | 暂无任务 |
| `workspace_empty_artifacts` | No artifacts yet | 暂无产物 |
| `workspace_empty_files` | No files yet | 暂无文件 |
| `workspace_file_preview_title` | File Preview | 文件预览 |

---

## ChatViewModel.kt 检查

如果 ChatViewModel 中有用户可见的错误消息字符串，也需要一并处理:
```kotlin
// 如有:
val error = "Failed to send message"
// 改为传递 resourceId 或在 Composable 层处理
```

---

## 完成标准

- [ ] ChatScreen.kt — 所有 12 处英文替换完毕
- [ ] SessionSettingsScreen.kt — 所有 35 处英文替换完毕
- [ ] SessionSettingsSheet.kt — 所有 30 处英文替换完毕
- [ ] SpaSettingsScreen.kt — 所有 25 处英文替换完毕
- [ ] WorkspaceSheet.kt — 所有 20 处英文替换完毕
- [ ] ChatViewModel.kt — 错误消息已处理
- [ ] `values/strings.xml` 新增所有 chat_ 前缀 key (英文)
- [ ] `values-zh-rCN/strings.xml` 新增所有对应中文翻译
- [ ] 编译通过: `./gradlew assembleDebug` 无错误
