# OpenCode 指令模板 — Session G0: 全局组件基座

> **项目**: Nexara Native UI  
> **工作目录**: `k:/Nexara/native-ui/`  
> **Session**: G0 — 全局可复用组件基座  
> **前置依赖**: 无 (首个 Session)  
> **设计参考根目录**: `k:/Nexara/.stitch/`

---

## 你的任务

在 `app/src/main/java/com/promenar/nexara/ui/common/` 目录下创建 13 个全局可复用 Composable 组件。这些组件将被所有后续页面的 Session 引用。

**核心原则**:
- 视觉样式**完全以 `.stitch/` 中的 Stitch MD3 设计稿为准**
- 绝不参考原 RN UI 样式，Stitch 设计稿是唯一视觉权威
- 所有组件必须使用现有的 `NexaraColors`, `NexaraTypography`, `NexaraShapes`, `NexaraCustomShapes`, `NexaraGlassCard` 设计 Token
- 每个组件必须是独立、可复用的 Composable 函数

---

## 设计系统基准

先读取以下文件建立设计 Token 认知：
- `.stitch/design_system/global_theme_specs.md` — 完整颜色/字体/间距/毛玻璃 Token
- `.stitch/design_system/stitch-full-app-visual-redesign-spec.md` — MD3 + Glassmorphism 设计规范
- `.stitch/design_system/stitch-ui-functional-reference.md` — 每个组件的功能需求

现有 Kotlin 设计 Token 位置：
- `ui/theme/NexaraColors.kt`
- `ui/theme/NexaraTypography.kt`
- `ui/theme/NexaraShapes.kt`
- `ui/theme/NexaraCustomShapes.kt`
- `ui/common/NexaraGlassCard.kt`
- `ui/common/NexaraSettingsItem.kt`

---

## 逐个组件规格

### 1. `ModelPicker.kt`

**Stitch 参考**: 用浏览器打开 `.stitch/screens/f7e063d9f3714701adbaa3b3cad56538.html` 查看视觉设计

**功能参考**: `stitch-ui-functional-reference.md` → H3 模型选择器

**组件签名**:
```kotlin
@Composable
fun ModelPicker(
    show: Boolean,
    onDismiss: () -> Unit,
    onSelect: (modelId: String, modelName: String) -> Unit,
    currentModelId: String = "",
    models: List<ModelItem> = emptyList()
)

data class ModelItem(
    val id: String,
    val name: String,
    val providerName: String,
    val capabilities: List<ModelCapability>,  // REASONING, VISION, WEB, RERANK, EMBEDDING, CHAT
    val contextLength: Int? = null
)

enum class ModelCapability {
    REASONING, VISION, WEB, RERANK, EMBEDDING, CHAT
}
```

**UI 要求**:
- `ModalBottomSheet` + 搜索栏 (Search 图标, 150ms 防抖)
- 模型列表: 每项 — 模型图标 + 模型名(粗体) + 提供商名(Server 图标) + 能力标签(彩色胶囊: Reasoning=紫 / Vision=粉 / Web=天蓝 / Rerank=橙 / Embedding=青 / Chat=翠绿) + 上下文长度标签 + 选中 ✓ 图标
- 空状态: Cpu 图标 + "无可用模型"
- 能力标签是小彩色胶囊(背景色+文字色)
- 选中项右侧品牌色勾号

---

### 2. `FloatingTextEditor.kt`

**Stitch 参考**: `.stitch/screens/ffa06f4ce51b43079a5623e723eaef04.html`

**组件签名**:
```kotlin
@Composable
fun FloatingTextEditor(
    show: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    title: String,
    initialText: String = "",
    placeholder: String = "Enter text..."
)
```

**UI 要求**:
- 全屏 `Dialog` (暗色遮罩)
- 顶部栏: 返回按钮 + 标题(Manrope 粗体) + 保存按钮(品牌色)
- 多行 `BasicTextField` 区域，全屏高度
- 保存按钮 disabled 态灰色

---

### 3. `FloatingCodeEditor.kt`

**组件签名**:
```kotlin
@Composable
fun FloatingCodeEditor(
    show: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    title: String,
    initialCode: String = "",
    language: String = "json"  // json, python, javascript 等
)
```

**UI 要求**:
- 同 FloatingTextEditor 结构
- 使用等宽字体 (`FontFamily.Monospace`)
- 行号显示(行首灰色数字)
- 基础语法高亮(关键词着色) — 可用简单的正则匹配实现

---

### 4. `ColorPickerPanel.kt`

**Stitch 参考**: `stitch-ui-functional-reference.md` → E9 主题设置 / B3 Agent 编辑器外观区

**组件签名**:
```kotlin
@Composable
fun ColorPickerPanel(
    selectedColor: androidx.compose.ui.graphics.Color,
    onColorSelected: (androidx.compose.ui.graphics.Color) -> Unit,
    presetColors: List<androidx.compose.ui.graphics.Color> = defaultPresetColors,
    showCustomSlider: Boolean = true
)
```

**UI 要求**:
- 一行预设色圆点 (8-10个, 32dp 圆形, 间距 12dp)
- 选中圆点有品牌色外圈 (2dp border)
- 选中圆点内部 ✓ 图标
- 自定义色: 一个 Slider (色相) + 当前色预览矩形
- 预设色建议: Indigo, Rose, Emerald, Amber, Cyan, Purple, Orange, Teal, Fuchsia, Sky

---

### 5. `InferencePresets.kt`

**Stitch 参考**: `stitch-ui-functional-reference.md` → B3 Agent 编辑器模型配置

**组件签名**:
```kotlin
data class InferencePreset(
    val id: String,           // "precise", "balanced", "creative"
    val label: String,        // "精确", "均衡", "创意"
    val icon: ImageVector,
    val iconTint: Color,
    val temperature: Float,
    val topP: Float
)

@Composable
fun InferencePresets(
    selected: String,         // preset id
    onSelect: (InferencePreset) -> Unit
)
```

**UI 要求**:
- 3 张等宽卡片横向排列
- 精确: Purple 色调 + Code 图标 → temp=0.2, topP=0.8
- 均衡: Cyan 色调 + Zap 图标 → temp=0.7, topP=0.9
- 创意: Amber 色调 + BookOpen 图标 → temp=1.0, topP=0.95
- 选中卡片: 品牌色边框(2dp) + 品牌色着色背景(alpha=0.1)
- 未选中: GlassSurface 背景 + GlassBorder 边框
- 按压缩放动画 (0.97)

---

### 6. `ExecutionModeSelector.kt`

**Stitch 参考**: `stitch-ui-functional-reference.md` → C3 会话设置弹窗 工具面板

**组件签名**:
```kotlin
enum class ExecutionMode { AUTO, SEMI, MANUAL }

@Composable
fun ExecutionModeSelector(
    selected: ExecutionMode,
    onSelect: (ExecutionMode) -> Unit
)
```

**UI 要求**:
- 3 段水平分段控制
- 选中段: 品牌色背景 + 白色文字
- 未选中段: GlassSurface 背景 + secondary 色
- 圆角边框包裹
- 滑动动画指示器

---

### 7. `SwipeableItem.kt`

**Stitch 参考**: `stitch-ui-functional-reference.md` → B1/B2 滑动操作

**组件签名**:
```kotlin
@Composable
fun SwipeableItem(
    onPin: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    isPinned: Boolean = false,
    content: @Composable () -> Unit
)
```

**UI 要求**:
- 使用 `Modifier.offset` + `Animatable` + 手势检测实现
- 左滑露出: 置顶按钮(品牌色背景, PushPin 图标, 80dp 宽)
- 右滑露出: 删除按钮(红色背景, Delete 图标, 80dp 宽)
- 弹性回弹动画 (spring)
- 滑动阈值: 25% 宽度触发操作

---

### 8. `ConfirmDialog.kt`

**组件签名**:
```kotlin
@Composable
fun ConfirmDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    title: String,
    description: String,
    confirmLabel: String = "Confirm",
    confirmColor: Color = NexaraColors.Error,
    destructive: Boolean = true
)
```

**UI 要求**:
- `AlertDialog` + NexaraGlassCard 风格
- 品牌风格按钮
- destructive=true 时确认按钮红色

---

### 9. `SettingsSectionHeader.kt`

**Stitch 参考**: `stitch-ui-functional-reference.md` → E1 设置首页

**组件签名**:
```kotlin
@Composable
fun SettingsSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
)
```

**UI 要求**:
- Manrope 字体, 10sp, 大写, tertiary 色
- 可选右侧行动链接(品牌色文字)

---

### 10. `SettingsInput.kt`

**Stitch 参考**: `stitch-ui-functional-reference.md` → B3 Agent 编辑器

**组件签名**:
```kotlin
@Composable
fun SettingsInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5
)
```

**UI 要求**:
- Glass-panel 背景 (NexaraGlassCard)
- border-radius: 12dp
- 焦点时边框色过渡动画 (透明 → 品牌色)
- label 在输入框上方，Manrope 粗体
- 使用 BasicTextField + 自定义 decorationBox

---

### 11. `SettingsToggle.kt`

**组件签名**:
```kotlin
@Composable
fun SettingsToggle(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
)
```

**UI 要求**:
- 左侧: 图标(可选) + 标题 + 描述(secondary 色)
- 右侧: MD3 Switch (品牌色)
- 点击整行切换
- NexaraGlassCard 容器

---

### 12. `CollapsibleSection.kt`

**组件签名**:
```kotlin
@Composable
fun CollapsibleSection(
    title: String,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit
)
```

**UI 要求**:
- 标题行 + ChevronDown 图标(展开时旋转 180°)
- `AnimatedVisibility` 展开/折叠动画
- 点击标题行切换

---

### 13. `AgentAvatar.kt`

**Stitch 参考**: `stitch-ui-functional-reference.md` → B3 Agent 编辑器

**组件签名**:
```kotlin
@Composable
fun AgentAvatar(
    icon: ImageVector? = null,
    customImageUri: String? = null,
    backgroundColor: Color,
    size: Dp = 80.dp,
    onClick: (() -> Unit)? = null
)
```

**UI 要求**:
- 80dp 圆形(默认) + 品牌色背景
- 中心图标(白色, 36dp)
- 支持自定义图片(Uri → Coil/AsyncImage)
- onClick 时按压缩放 0.95

---

## 完成标准

- [ ] 13 个文件全部创建于 `ui/common/` 目录
- [ ] 每个组件可独立编译（无 unresolved reference）
- [ ] 所有组件使用 NexaraColors/NexaraTypography/NexaraShapes 设计 Token
- [ ] 无硬编码颜色值（必须使用 NexaraColors 的属性）
- [ ] 每个 Composable 有 `@Composable` 注解和 KDoc 注释
- [ ] 编译通过: `./gradlew assembleDebug` 无错误
