# OpenCode 指令模板 — Session I0: 基础设施 + 全局组件 i18n

> **项目**: Nexara Native UI
> **工作目录**: `k:/Nexara/native-ui/`
> **Session**: I0 — i18n 基础设施搭建 + 全局组件字符串外部化
> **前置依赖**: 无 (首个 i18n Session)

---

## 你的任务

1. 从零搭建 Android i18n 基础设施
2. 将 `ui/common/` 目录下全部 23 个组件中的硬编码文本外部化为 string resource
3. 建立 strings.xml 双语文件

---

## 第一步: 创建基础设施

### 1.1 创建资源目录

```
app/src/main/res/
├── values/
│   └── strings.xml          ← 默认 (英文)
└── values-zh-rCN/
    └── strings.xml          ← 简体中文
```

**注意**: `app/src/main/res/` 目录当前不存在，需要创建。

### 1.2 创建 values/strings.xml (默认英文)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ============ Common Components ============ -->
    <!-- ModelPicker -->
    <string name="common_model_picker_title">Select Model</string>
    <string name="common_model_picker_search">Search models…</string>
    <string name="common_model_picker_empty">No models available</string>
    <string name="common_model_picker_k_context">K context</string>
    <string name="common_model_picker_selected">Selected</string>

    <!-- FloatingTextEditor -->
    <string name="common_text_editor_placeholder">Enter text…</string>

    <!-- InferencePresets -->
    <string name="common_preset_precise">Precise</string>
    <string name="common_preset_balanced">Balanced</string>
    <string name="common_preset_creative">Creative</string>
    <string name="common_preset_temp_label">T: %1$s</string>

    <!-- ColorPickerPanel -->
    <string name="common_color_custom">Custom</string>

    <!-- ExecutionMode -->
    <string name="common_mode_auto">Auto</string>
    <string name="common_mode_semi">Semi</string>
    <string name="common_mode_manual">Manual</string>

    <!-- ConfirmDialog -->
    <string name="common_btn_confirm">Confirm</string>
    <string name="common_btn_cancel">Cancel</string>

    <!-- SearchBar -->
    <string name="common_search_placeholder">Search…</string>

    <!-- Content Descriptions (无障碍) -->
    <string name="common_cd_back">Back</string>
    <string name="common_cd_save">Save</string>
    <string name="common_cd_clear">Clear</string>
    <string name="common_cd_collapse">Collapse</string>
    <string name="common_cd_expand">Expand</string>
    <string name="common_cd_navigate">Navigate</string>
    <string name="common_cd_pin">Pin</string>
    <string name="common_cd_selected">Selected</string>

    <!-- ============ Navigation ============ -->
    <string name="nav_tab_chat">Chat</string>
    <string name="nav_tab_library">Library</string>
    <string name="nav_tab_settings">Settings</string>

    <!-- ============ Welcome ============ -->
    <string name="welcome_brand">NEXARA</string>
    <string name="welcome_slogan">INTELLIGENCE REIMAGINED</string>
    <string name="welcome_lang_english">English</string>
    <string name="welcome_lang_chinese">中文 (简体)</string>
    <string name="welcome_cd_language">Select language</string>

    <!-- ============ Shared / Reused ============ -->
    <string name="shared_loading">Loading…</string>
    <string name="shared_error_generic">Something went wrong</string>
    <string name="shared_btn_delete">Delete</string>
    <string name="shared_btn_save">Save</string>
    <string name="shared_btn_edit">Edit</string>
    <string name="shared_btn_add">Add</string>
    <string name="shared_btn_reset">Reset</string>
    <string name="shared_btn_close">Close</string>
    <string name="shared_btn_done">Done</string>
    <string name="shared_btn_retry">Retry</string>
    <string name="shared_confirm_delete_title">Confirm Delete</string>
    <string name="shared_action_cannot_undo">This action cannot be undone.</string>
</resources>
```

### 1.3 创建 values-zh-rCN/strings.xml (简体中文)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ============ Common Components ============ -->
    <!-- ModelPicker -->
    <string name="common_model_picker_title">选择模型</string>
    <string name="common_model_picker_search">搜索模型…</string>
    <string name="common_model_picker_empty">暂无可用模型</string>
    <string name="common_model_picker_k_context">K 上下文</string>
    <string name="common_model_picker_selected">已选中</string>

    <!-- FloatingTextEditor -->
    <string name="common_text_editor_placeholder">请输入文本…</string>

    <!-- InferencePresets -->
    <string name="common_preset_precise">精确</string>
    <string name="common_preset_balanced">均衡</string>
    <string name="common_preset_creative">创意</string>
    <string name="common_preset_temp_label">温度: %1$s</string>

    <!-- ColorPickerPanel -->
    <string name="common_color_custom">自定义</string>

    <!-- ExecutionMode -->
    <string name="common_mode_auto">自动</string>
    <string name="common_mode_semi">半自动</string>
    <string name="common_mode_manual">手动</string>

    <!-- ConfirmDialog -->
    <string name="common_btn_confirm">确认</string>
    <string name="common_btn_cancel">取消</string>

    <!-- SearchBar -->
    <string name="common_search_placeholder">搜索…</string>

    <!-- Content Descriptions -->
    <string name="common_cd_back">返回</string>
    <string name="common_cd_save">保存</string>
    <string name="common_cd_clear">清除</string>
    <string name="common_cd_collapse">收起</string>
    <string name="common_cd_expand">展开</string>
    <string name="common_cd_navigate">导航</string>
    <string name="common_cd_pin">置顶</string>
    <string name="common_cd_selected">已选中</string>

    <!-- ============ Navigation ============ -->
    <string name="nav_tab_chat">对话</string>
    <string name="nav_tab_library">知识库</string>
    <string name="nav_tab_settings">设置</string>

    <!-- ============ Welcome ============ -->
    <string name="welcome_brand">NEXARA</string>
    <string name="welcome_slogan">智能重塑</string>
    <string name="welcome_lang_english">English</string>
    <string name="welcome_lang_chinese">中文 (简体)</string>
    <string name="welcome_cd_language">选择语言</string>

    <!-- ============ Shared / Reused ============ -->
    <string name="shared_loading">加载中…</string>
    <string name="shared_error_generic">出了点问题</string>
    <string name="shared_btn_delete">删除</string>
    <string name="shared_btn_save">保存</string>
    <string name="shared_btn_edit">编辑</string>
    <string name="shared_btn_add">添加</string>
    <string name="shared_btn_reset">重置</string>
    <string name="shared_btn_close">关闭</string>
    <string name="shared_btn_done">完成</string>
    <string name="shared_btn_retry">重试</string>
    <string name="shared_confirm_delete_title">确认删除</string>
    <string name="shared_action_cannot_undo">此操作无法撤销。</string>
</resources>
```

### 1.4 在 build.gradle.kts 中添加资源过滤 (可选但推荐)

在 `android { defaultConfig { ... } }` 中添加:
```kotlin
resourceConfigurations += listOf("en", "zh-rCN")
```

---

## 第二步: 修改全局组件

对 `ui/common/` 下每个含有硬编码文本的文件，将文本替换为 `stringResource(R.string.xxx)`。

### 修改模式

**修改前**:
```kotlin
Text("Select Model")
```

**修改后**:
```kotlin
Text(stringResource(R.string.common_model_picker_title))
```

需要添加的 import:
```kotlin
import androidx.compose.ui.res.stringResource
import com.promenar.nexara.R
```

### 逐文件修改清单

#### 1. ModelPicker.kt

| 原文本 | 替换为 |
|--------|--------|
| `"Select Model"` | `stringResource(R.string.common_model_picker_title)` |
| `"Search models..."` | `stringResource(R.string.common_model_picker_search)` |
| `"No models available"` | `stringResource(R.string.common_model_picker_empty)` |
| `"K context"` | `stringResource(R.string.common_model_picker_k_context)` |
| `"Selected"` | `stringResource(R.string.common_model_picker_selected)` |

#### 2. FloatingTextEditor.kt

| 原文本 | 替换为 |
|--------|--------|
| `"Enter text..."` (placeholder默认值) | `stringResource(R.string.common_text_editor_placeholder)` |
| `"Back"` | `stringResource(R.string.common_cd_back)` |
| `"Save"` | `stringResource(R.string.common_cd_save)` |

#### 3. FloatingCodeEditor.kt

| 原文本 | 替换为 |
|--------|--------|
| `"Back"` | `stringResource(R.string.common_cd_back)` |
| `"Save"` | `stringResource(R.string.common_cd_save)` |

#### 4. InferencePresets.kt

**需要重构**: 预设数据类中的 label 从 String 改为 @StringRes Int:

```kotlin
// 修改前:
data class InferencePreset(
    val id: String,
    val label: String,
    ...
)

// 修改后:
data class InferencePreset(
    val id: String,
    @StringRes val labelRes: Int,
    ...
)

// 预设定义:
InferencePreset("precise", R.string.common_preset_precise, ...)
InferencePreset("balanced", R.string.common_preset_balanced, ...)
InferencePreset("creative", R.string.common_preset_creative, ...)

// 使用处:
Text(text = stringResource(preset.labelRes))
```

#### 5. ColorPickerPanel.kt

| 原文本 | 替换为 |
|--------|--------|
| `"Custom"` | `stringResource(R.string.common_color_custom)` |

#### 6. ExecutionModeSelector.kt

**需要重构**: 枚举改为持有 @StringRes:

```kotlin
enum class ExecutionMode(@StringRes val labelRes: Int) {
    AUTO(R.string.common_mode_auto),
    SEMI(R.string.common_mode_semi),
    MANUAL(R.string.common_mode_manual)
}

// 使用处:
Text(text = stringResource(mode.labelRes))
```

#### 7. ConfirmDialog.kt

| 原文本 | 替换为 |
|--------|--------|
| `"Confirm"` (confirmLabel默认值) | `stringResource(R.string.common_btn_confirm)` |
| `"Cancel"` | `stringResource(R.string.common_btn_cancel)` |

**注意**: confirmLabel 参数类型需要改为 `@StringRes Int confirmLabelRes = R.string.common_btn_confirm`，或保留 String 但在调用处传入已解析的字符串。

#### 8. NexaraConfirmDialog.kt

| 原文本 | 替换为 |
|--------|--------|
| `"Cancel"` (cancelText默认值) | 同上处理 |

#### 9. CollapsibleSection.kt

| 原文本 | 替换为 |
|--------|--------|
| `"Collapse"` | `stringResource(R.string.common_cd_collapse)` |
| `"Expand"` | `stringResource(R.string.common_cd_expand)` |

#### 10. NexaraCollapsibleSection.kt

同上处理。

#### 11. NexaraSearchBar.kt

| 原文本 | 替换为 |
|--------|--------|
| `"Search..."` (placeholder默认值) | `stringResource(R.string.common_search_placeholder)` |
| `"Clear"` | `stringResource(R.string.common_cd_clear)` |

#### 12. NexaraPageLayout.kt

| 原文本 | 替换为 |
|--------|--------|
| `"Back"` | `stringResource(R.string.common_cd_back)` |

#### 13. NexaraSettingsItem.kt

| 原文本 | 替换为 |
|--------|--------|
| `"Navigate"` | `stringResource(R.string.common_cd_navigate)` |

#### 14. SwipeableItem.kt

| 原文本 | 替换为 |
|--------|--------|
| `"Pin"` (如有) | `stringResource(R.string.common_cd_pin)` |

#### 15-23. 无需修改的文件

以下文件无硬编码用户可见文本，无需修改:
- AgentAvatar.kt
- SettingsInput.kt (label/placeholder 由调用者传入)
- SettingsToggle.kt (同上)
- SettingsSectionHeader.kt (同上)
- NexaraGlassCard.kt
- NexaraBottomSheet.kt
- NexaraLoadingIndicator.kt
- MarkdownText.kt
- NexaraSnackbar.kt

---

## 第三步: 修改 MainTabScaffold.kt

### AppTab 枚举重构

```kotlin
// 修改前:
enum class AppTab(val title: String, ...) {
    CHAT("CHAT", ...),
    LIBRARY("LIBRARY", ...),
    SETTINGS("SETTINGS", ...)
}

// 修改后:
enum class AppTab(@StringRes val titleRes: Int, ...) {
    CHAT(R.string.nav_tab_chat, ...),
    LIBRARY(R.string.nav_tab_library, ...),
    SETTINGS(R.string.nav_tab_settings, ...)
}
```

### Composable 中使用:

```kotlin
// 修改前:
Text(text = tab.title, ...)

// 修改后:
Text(text = stringResource(tab.titleRes), ...)
```

```kotlin
// contentDescription 同理:
contentDescription = stringResource(tab.titleRes)
```

---

## 第四步: 修改 WelcomeScreen.kt

| 原文本 | 替换为 |
|--------|--------|
| `"NEXARA"` | `stringResource(R.string.welcome_brand)` |
| `"INTELLIGENCE REIMAGINED"` | `stringResource(R.string.welcome_slogan)` |
| `"English"` | `stringResource(R.string.welcome_lang_english)` |
| `"中文 (简体)"` | `stringResource(R.string.welcome_lang_chinese)` |
| contentDescription (如有) | `stringResource(R.string.welcome_cd_language)` |

---

## 完成标准

- [ ] `app/src/main/res/values/strings.xml` 创建并包含所有 common + nav + welcome key (英文)
- [ ] `app/src/main/res/values-zh-rCN/strings.xml` 创建并包含所有对应中文翻译
- [ ] `ui/common/` 下所有硬编码文本替换为 `stringResource()`
- [ ] `MainTabScaffold.kt` AppTab 枚举使用 `@StringRes Int`
- [ ] `WelcomeScreen.kt` 所有文本使用 `stringResource()`
- [ ] `ExecutionMode` 枚举使用 `@StringRes Int`
- [ ] `InferencePresets` 数据类使用 `@StringRes Int`
- [ ] 编译通过: `./gradlew assembleDebug` 无错误
