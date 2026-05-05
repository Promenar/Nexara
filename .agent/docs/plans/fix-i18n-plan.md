# i18n 修复方案总纲

> **日期**: 2026-05-04
> **基准审计**: `.agent/docs/audits/i18n-multilingual-audit-2026-05-04.md`
> **目标**: 全量 ~585 条字符串外部化，支持中文（简体）+ English 应用内切换
> **总工作量**: 5 个 Session

---

## 依赖关系

```
Session I0 (基础设施 + common 组件)
    │
    ├── Session I1 (hub 模块 9 文件)
    │
    ├── Session I2 (chat 模块 5 文件)
    │
    ├── Session I3 (settings 模块 10 文件)
    │
    └── Session I4 (rag 模块 + MainTab + Welcome + 语言切换运行时)
```

---

## 总体规范

### strings.xml key 命名规范

```
{模块}_{组件}_{用途}

模块前缀:
  common_   — 全局组件
  hub_      — Agent 管理模块
  chat_     — 聊天模块
  settings_ — 设置模块
  rag_      — 知识库模块
  nav_      — 导航
  welcome_  — 欢迎页

用途后缀:
  _title     — 页面/区域标题
  _label     — 字段标签
  _placeholder — 输入占位文本
  _desc      — 描述文本
  _btn       — 按钮文本
  _empty     — 空状态提示
  _error     — 错误消息
  _confirm   — 确认文本
  _cancel    — 取消文本
  _cd        — contentDescription (无障碍)
  _section   — Section Header
  _toggle    — 开关标签
  _tab       — Tab 标签
```

### 枚举/非 Composable 中的处理

枚举类持有 `@StringRes Int`:
```kotlin
enum class AppTab(@StringRes val titleRes: Int) {
    CHAT(R.string.nav_tab_chat),
    LIBRARY(R.string.nav_tab_library),
    SETTINGS(R.string.nav_tab_settings)
}

// Composable 中:
Text(text = stringResource(tab.titleRes))
```

### ViewModel 中的文本

通过 resourceId 或在 Composable 层解析:
```kotlin
// ViewModel:
val errorResId: StateFlow<Int?>  // R.string.error_xxx

// Composable:
val errorResId by viewModel.errorResId.collectAsState()
errorResId?.let { Text(stringResource(it)) }
```

### 动态文本拼接

使用 `strings.xml` 格式化:
```xml
<string name="chat_input_placeholder">Message %1$s…</string>
<string name="chat_input_placeholder">发消息给%1$s…</string>
```

---

## Session 分工

| Session | 范围 | 文件数 | 字符串数 | 预估 |
|---------|------|--------|---------|------|
| I0 | 基础设施 + common/ | 24 | ~65 | 高 |
| I1 | hub/ | 9 | ~130 | 高 |
| I2 | chat/ | 5 | ~120 | 高 |
| I3 | settings/ | 10 | ~140 | 高 |
| I4 | rag/ + MainTab + Welcome + 语言切换 | 16 | ~130 | 高 |

---

## 交付物

每个 Session 结束后：
1. `values/strings.xml` 新增对应 key (英文默认值)
2. `values-zh-rCN/strings.xml` 新增对应 key (中文翻译)
3. 对应 .kt 文件中所有硬编码替换为 `stringResource()`
4. 编译通过

---

*以下为各 Session 的 OpenCode 指令模板*
