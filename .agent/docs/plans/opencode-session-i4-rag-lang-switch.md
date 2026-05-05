# OpenCode 指令模板 — Session I4: rag 模块 + 语言切换运行时

> **项目**: Nexara Native UI
> **工作目录**: `k:/Nexara/native-ui/`
> **Session**: I4 — rag 模块 i18n + 应用内语言切换机制
> **前置依赖**: I0 (基础设施已完成)

---

## 你的任务

1. 将 `ui/rag/` 目录下全部 .kt 文件中的英文硬编码文本外部化
2. 实现应用内语言切换运行时机制（用户在设置中切换语言后立即生效）

---

## Part A: rag 模块 i18n

### 需修改的文件 (8个 Screen + 4个组件)

#### 1. RagHomeScreen.kt (~1045行, ~30处)

| key | 英文 | 中文 |
|-----|------|------|
| `rag_home_title` | Knowledge Base | 知识库 |
| `rag_home_subtitle` | Centralized intelligence repository and vector storage. | 集中式智能知识库与向量存储。 |
| `rag_home_documents` | Documents | 文档 |
| `rag_home_indexed` | %1$d Indexed | %1$d 已索引 |
| `rag_home_memory` | Memory | 记忆 |
| `rag_home_graph` | Graph | 图谱 |
| `rag_home_entities` | %1$d Entities | %1$d 实体 |
| `rag_home_section_collections` | Collections | 集合 |
| `rag_home_empty_title` | No Collections Yet | 暂无集合 |
| `rag_home_empty_subtitle` | Upload documents or create a folder to get started. | 上传文档或创建文件夹以开始。 |
| `rag_home_portal_docs` | Documents | 文档 |
| `rag_home_portal_memory` | Memories | 记忆 |
| `rag_home_portal_graph` | Knowledge Graph | 知识图谱 |
| `rag_home_upload_area` | Drop files here to upload | 拖放文件到此处上传 |
| `rag_home_select_files` | Select Files | 选择文件 |
| `rag_home_indexing` | Indexing… | 正在向量化… |
| `rag_home_selected_count` | %1$d Selected | 已选择 %1$d 项 |
| `rag_home_reindex` | Re-index | 重新向量化 |
| `rag_home_move` | Move | 移动 |

#### 2. RagFolderScreen.kt (~450行, ~15处)

| key | 英文 | 中文 |
|-----|------|------|
| `rag_folder_empty` | This folder is empty | 此文件夹为空 |
| `rag_folder_select_all` | Select All | 全选 |
| `rag_folder_deselect` | Deselect | 取消选择 |
| `rag_folder_move_to` | Move to… | 移动到… |
| `rag_folder_delete_selected` | Delete Selected | 删除所选 |
| `rag_folder_reindex` | Re-index | 重新向量化 |
| `rag_folder_select_target` | Select Target Folder | 选择目标文件夹 |

#### 3. DocEditorScreen.kt (~519行, ~20处)

| key | 英文 | 中文 |
|-----|------|------|
| `doc_editor_preview` | Preview | 预览 |
| `doc_editor_edit` | Edit | 编辑 |
| `doc_editor_save` | Save | 保存 |
| `doc_editor_large_file` | Large File Warning | 大文件警告 |
| `doc_editor_large_file_desc` | This file is large. Editing may affect performance. | 文件较大，编辑可能影响性能。 |
| `doc_editor_dismiss` | Dismiss | 关闭 |
| `doc_editor_unsaved` | Unsaved Changes | 未保存的更改 |
| `doc_editor_words` | %1$d words | %1$d 词 |
| `doc_editor_chars` | %1$d characters | %1$d 字符 |
| `doc_editor_utf8` | UTF-8 | UTF-8 |
| `doc_editor_editing` | Editing | 编辑中 |
| `doc_editor_readonly` | Read Only | 只读 |

#### 4. KnowledgeGraphScreen.kt (~450行, ~15处)

| key | 英文 | 中文 |
|-----|------|------|
| `kg_title` | Knowledge Graph | 知识图谱 |
| `kg_filter_documents` | Documents | 文档 |
| `kg_filter_folders` | Folders | 文件夹 |
| `kg_filter_concepts` | Concepts | 概念 |
| `kg_node_details` | Node Details | 节点详情 |
| `kg_node_type` | Type | 类型 |
| `kg_node_connections` | Connections | 连接数 |
| `kg_empty` | No graph data available | 暂无图谱数据 |
| `kg_zoom_in` | Zoom In | 放大 |
| `kg_zoom_out` | Zoom Out | 缩小 |

#### 5. RagAdvancedScreen.kt (~481行, ~20处)

| key | 英文 | 中文 |
|-----|------|------|
| `rag_advanced_title` | Knowledge Graph Settings | 知识图谱设置 |
| `rag_advanced_kg_enable` | Enable Knowledge Graph | 启用知识图谱 |
| `rag_advanced_extract_model` | Extraction Model | 提取模型 |
| `rag_advanced_section_jit` | JIT Micro-Graph | JIT 微图 |
| `rag_advanced_jit_enable` | Enable JIT | 启用 JIT |
| `rag_advanced_jit_max_blocks` | Max Blocks | 最大块数 |
| `rag_advanced_jit_free_mode` | Free Mode | 免费模式 |
| `rag_advanced_jit_domain` | Auto Domain Detection | 域名自动检测 |
| `rag_advanced_section_cost` | Cost Strategy | 成本策略 |
| `rag_advanced_cost_summary` | Summary First | 摘要优先 |
| `rag_advanced_cost_on_demand` | On Demand | 按需 |
| `rag_advanced_cost_full_scan` | Full Scan | 全扫描 |
| `rag_advanced_section_optimization` | Local Optimization | 本地优化 |
| `rag_advanced_incremental_hash` | Incremental Hash | 增量哈希 |
| `rag_advanced_rule_prefilter` | Rule Pre-filtering | 规则预过滤 |
| `rag_advanced_section_prompt` | Extraction Prompt | 提取提示词 |
| `rag_advanced_view_graph` | View Full Graph | 查看完整图谱 |

#### 6. RagDebugScreen.kt (~324行, ~15处)

| key | 英文 | 中文 |
|-----|------|------|
| `rag_debug_title` | Vector Stats | 向量统计 |
| `rag_debug_refresh` | Refresh | 刷新 |
| `rag_debug_total_vectors` | Total Vectors | 总向量数 |
| `rag_debug_storage` | Storage | 存储大小 |
| `rag_debug_section_types` | Type Distribution | 类型分布 |
| `rag_debug_doc_vectors` | Document Vectors | 文档向量 |
| `rag_debug_memory_vectors` | Memory / Summary Vectors | 记忆/摘要向量 |
| `rag_debug_section_health` | Storage Health | 存储健康 |
| `rag_debug_redundancy` | Redundancy Rate | 冗余率 |
| `rag_debug_cleanup` | Clean Up | 清理 |
| `rag_debug_section_sessions` | Top Sessions | Top 会话 |
| `rag_debug_vectors_count` | %1$d vectors | %1$d 向量 |

#### 7. GlobalRagConfigScreen.kt (~680行, ~20处)

| key | 英文 | 中文 |
|-----|------|------|
| `rag_config_title` | RAG Settings | RAG 设置 |
| `rag_config_preset_balanced` | Balanced | 均衡 |
| `rag_config_preset_writing` | Writing | 写作 |
| `rag_config_preset_coding` | Coding | 编程 |
| `rag_config_chunk_size` | Document Chunk Size | 文档分块大小 |
| `rag_config_overlap` | Chunk Overlap | 分块重叠 |
| `rag_config_memory_chunk` | Memory Chunk Size | 记忆分块大小 |
| `rag_config_context_window` | Active Context Window | 活跃上下文窗口 |
| `rag_config_summary_threshold` | Summary Trigger Threshold | 摘要触发阈值 |
| `rag_config_section_template` | Summary Template | 摘要模板 |
| `rag_config_stats_documents` | Documents | 文档数 |
| `rag_config_stats_vectors` | Vectors | 向量数 |
| `rag_config_stats_storage` | Storage | 存储 |
| `rag_config_advanced_link` | Advanced | 高级 |
| `rag_config_details_link` | More Details | 更多详情 |
| `rag_config_clear_vectors` | Clear Vector Data | 清除向量数据 |
| `rag_config_clear_message` | This will permanently delete all vector data. Continue? | 确定要永久删除所有向量数据吗？ |
| `rag_config_clean_orphans` | Clean Orphaned Data | 清理孤立数据 |

#### 8. AdvancedRetrievalScreen.kt (~515行, ~15处)

已在 AgentAdvancedRetrieval 中定义了部分 key，此处补充全局级:

| key | 英文 | 中文 |
|-----|------|------|
| `retrieval_title` | Advanced Retrieval | 高级检索 |
| `retrieval_section_observability` | Observability | 可观测性 |
| `retrieval_show_progress` | Show Retrieval Progress | 显示检索进度 |
| `retrieval_show_details` | Show Retrieval Details | 显示检索详情 |
| `retrieval_track_metrics` | Track Retrieval Metrics | 跟踪检索指标 |

#### 9-12. rag/components/ 子目录

- `FolderItem.kt` — 检查并处理
- `IndexingProgressBar.kt` — 检查并处理
- `RagDocItem.kt` — 检查并处理
- `RagStatusChip.kt` — 检查并处理

---

## Part B: 应用内语言切换运行时

### B1. 创建 `LocaleHelper.kt`

**路径**: `app/src/main/java/com/promenar/nexara/util/LocaleHelper.kt`

```kotlin
package com.promenar.nexara.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {
    fun applyLanguage(context: Context, languageCode: String): Context {
        val locale = when (languageCode) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.ENGLISH
        }
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("nexara_prefs", Context.MODE_PRIVATE)
        return prefs.getString("language", "zh") ?: "zh"
    }

    fun saveLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences("nexara_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("language", languageCode).apply()
    }
}
```

### B2. 修改 `NexaraApplication.kt`

在 Application 类中添加:

```kotlin
override fun attachBaseContext(base: Context) {
    val lang = LocaleHelper.getSavedLanguage(base)
    super.attachBaseContext(LocaleHelper.applyLanguage(base, lang))
}
```

### B3. 修改 MainActivity (如果存在)

在 Activity 中添加 `attachBaseContext`:

```kotlin
override fun attachBaseContext(newBase: Context) {
    val lang = LocaleHelper.getSavedLanguage(newBase)
    super.attachBaseContext(LocaleHelper.applyLanguage(newBase, lang))
}
```

### B4. 语言切换后刷新

在 `SettingsViewModel.setLanguage()` 中:
```kotlin
fun setLanguage(lang: String) {
    prefs.edit().putString("language", lang).apply()
    _language.value = lang
    // 需要在 Composable 层调用 activity.recreate() 刷新
}
```

在 `UserSettingsHomeScreen.kt` 的语言设置项点击回调中:
```kotlin
val activity = LocalContext.current as? Activity
// 语言选择后:
viewModel.setLanguage(selectedLang)
activity?.recreate()  // 刷新 Activity 使新语言生效
```

### B5. 创建语言选择对话框

替换当前的简单 language 显示为可点击的语言选择弹窗:

```kotlin
@Composable
fun LanguageSelectorDialog(
    currentLanguage: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                // English 选项
                LanguageOption(
                    label = "English",
                    selected = currentLanguage == "en",
                    onClick = { onSelect("en") }
                )
                // 中文选项
                LanguageOption(
                    label = "中文 (简体)",
                    selected = currentLanguage == "zh",
                    onClick = { onSelect("zh") }
                )
            }
        },
        confirmButton = {},
        containerColor = NexaraColors.SurfaceDim
    )
}
```

---

## 完成标准

- [ ] RagHomeScreen.kt — 英文替换完毕
- [ ] RagFolderScreen.kt — 英文替换完毕
- [ ] DocEditorScreen.kt — 英文替换完毕
- [ ] KnowledgeGraphScreen.kt — 英文替换完毕
- [ ] RagAdvancedScreen.kt — 英文替换完毕
- [ ] RagDebugScreen.kt — 英文替换完毕
- [ ] GlobalRagConfigScreen.kt — 英文替换完毕
- [ ] AdvancedRetrievalScreen.kt — 英文替换完毕
- [ ] rag/components/ 所有组件处理完毕
- [ ] LocaleHelper.kt 创建完毕
- [ ] NexaraApplication.kt 添加 attachBaseContext
- [ ] UserSettingsHomeScreen 语言切换可点击并弹出选择弹窗
- [ ] 语言切换后 Activity recreate 生效
- [ ] `values/strings.xml` 新增所有 rag_ 相关 key
- [ ] `values-zh-rCN/strings.xml` 新增所有对应中文翻译
- [ ] 编译通过: `./gradlew assembleDebug` 无错误
- [ ] 功能验证: 设置中切换语言后 UI 文本立即切换
