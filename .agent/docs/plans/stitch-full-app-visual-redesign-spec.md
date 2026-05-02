# Nexara 全 APP 视觉重构指导文档

> **文档版本**: v1.1 (2026-05-01)
> **v1.0 变更**: 补充 Group B/C/D/E 缺失组件设计要求；新增 Group H (技能与执行系统)；新增 G7~G12 通用组件；各界面详细功能需求参见 `stitch-ui-functional-reference.md`
> **设计系统**: Stitch "Modern Session UI Redesign" — Material Design 3 + Glassmorphism
> **工具**: Google Stitch (通过 MCP 接入)
> **已完成基准**: WebView 聊天主会话界面 (Dark Mode + Light Mode)

---

## 一、设计系统基准 Token

以下是已在 WebView 聊天界面中落地验证的完整设计系统，作为全 APP 所有界面的统一基准。

### 1.1 字体体系

| Token | 字体族 | 字重范围 | 用途 |
|---|---|---|---|
| `--font-heading` | **Manrope** | 400, 600, 700, 800 | 页面标题、区域标题、强调文本 |
| `--font-sans` | **Inter** | 400, 500, 600, 700 | 正文、UI 元素、按钮、标签 |
| `--font-mono` | **Space Grotesk** | 400, 500 | 代码、等宽数字、技术标签 |

> **Stitch 输出要求**: 所有 HTML 中必须通过 `<link>` 加载 Google Fonts CDN 引入这三个字体族。

### 1.2 颜色体系 — Material Design 3 Surface 层级

#### 暗色模式（主推模式）

| Token | 色值 | MD3 对应 | 用途 |
|---|---|---|---|
| `--bg-primary` | `#131315` | surface | 页面主背景 |
| `--bg-secondary` | `#201f22` | surface-container | 卡片、列表分组背景 |
| `--bg-tertiary` | `#2a2a2c` | surface-container-high | 输入框背景、嵌套容器 |
| `--bg-surface-lowest` | `#0e0e10` | surface-container-lowest | 代码块背景、最深凹层 |
| `--bg-surface-low` | `#1c1b1d` | surface-container-low | 搜索栏背景 |
| `--bg-surface-highest` | `#353437` | surface-container-highest | 高亮区域、活跃标签 |
| `--bg-surface-bright` | `#39393b` | surface-bright | 弹窗、模态框背景 |
| `--text-primary` | `#e5e1e4` | on-surface | 主要文本 |
| `--text-secondary` | `#c7c4d7` | on-surface-variant | 次级文本、描述 |
| `--text-tertiary` | `#908fa0` | outline | 辅助文本、占位符 |
| `--border-default` | `#464554` | outline-variant | 标准边框 |
| `--border-glass` | `rgba(255,255,255,0.1)` | — | 毛玻璃边框 |

#### 浅色模式

| Token | 色值 | 用途 |
|---|---|---|
| `--bg-primary` | `#ffffff` | 页面主背景 |
| `--bg-secondary` | `#f4f4f5` | 卡片背景 (Zinc-100) |
| `--bg-tertiary` | `#e4e4e7` | 嵌套容器 (Zinc-200) |
| `--text-primary` | `#09090b` | 主要文本 (Zinc-950) |
| `--text-secondary` | `#52525b` | 次级文本 (Zinc-600) |
| `--text-tertiary` | `#a1a1aa` | 辅助文本 (Zinc-400) |
| `--border-default` | `#e4e4e7` | 标准边框 (Zinc-200) |
| `--border-glass` | `rgba(0,0,0,0.08)` | 毛玻璃边框 |

#### 强调色（默认 Indigo，支持用户动态切换）

| 模式 | 500 (基准) | 主色 | 透明度 |
|---|---|---|---|
| Dark | `#c0c1ff` | `#c0c1ff` | `rgba(192,193,255, 0.1/0.2/0.3)` |
| Light | `#6366f1` | `#6366f1` | `rgba(99,102,241, 0.1/0.2/0.3)` |

#### 状态色

| 状态 | 色值 |
|---|---|
| Success | `#10b981` (Emerald-500) |
| Error | `#ef4444` (Red-500) |
| Warning | `#f59e0b` (Amber-500) |
| Info | `#3b82f6` (Blue-500) |

### 1.3 毛玻璃规范 (Glassmorphism)

```css
/* 标准玻璃面板 */
.glass-panel {
  background: rgba(255, 255, 255, 0.03);   /* Dark */
  backdrop-filter: blur(20px);
  border: 0.5px solid rgba(255, 255, 255, 0.1);
  box-shadow: inset 0 1px 2px rgba(255, 255, 255, 0.05);
}

/* 浅色模式 */
.glass-panel-light {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px);
  border: 0.5px solid rgba(0, 0, 0, 0.08);
  box-shadow: inset 0 1px 2px rgba(255, 255, 255, 0.05);
}
```

> **核心原则**: 毛玻璃边框统一 `0.5px`，内阴影统一 `inset 0 1px 2px rgba(255,255,255,0.05)`。这比苹果流体玻璃更轻量高效，但比纯 MD3 更精致。

### 1.4 间距体系

| Token | 值 | 用途 |
|---|---|---|
| `--spacing-xs` | `4px` | 最小间距 |
| `--spacing-sm` | `8px` | 小间距、内边距 |
| `--spacing-md` | `16px` | 中间距、卡片内边距 |
| `--spacing-lg` | `24px` | 大间距、消息间距 |
| `--spacing-xl` | `32px` | 超大间距 |
| `--spacing-safe-margin` | `20px` | 安全边距 |

### 1.5 圆角体系

| Token | 值 | 用途 |
|---|---|---|
| `--radius-sm` | `8px` | 输入框、小标签 |
| `--radius-md` | `12px` | 代码块、中等卡片 |
| `--radius-lg` | `16px` | 大卡片、面板 |
| `--radius-xl` | `18px` | 消息气泡 |
| `--radius-full` | `9999px` | 胶囊药丸、标签、状态点 |

### 1.6 阴影体系

| 场景 | 值 |
|---|---|
| 玻璃内阴影 | `inset 0 1px 2px rgba(255,255,255,0.05)` |
| 悬浮按钮阴影 | `0 4px 16px rgba(0,0,0,0.3)` |
| 代码块深度 | `0 8px 16px rgba(0,0,0,0.2)` |
| 弹窗阴影 | `0 10px 20px rgba(0,0,0,0.2)` |

### 1.7 图标体系

- **图标库**: Lucide Icons (RN 侧) / Material Symbols (Web 侧)
- **标准尺寸**: `16px` (操作按钮)、`20px` (导航)、`24px` (头部)
- **颜色**: 使用 `currentColor` 继承父级文本色

### 1.8 动画体系

| 动画 | 参数 | 用途 |
|---|---|---|
| 按钮弹性缩放 | `scale(0.96)`, spring(damping:20, stiffness:400) | 按下反馈 |
| 卡片弹性缩放 | `scale(0.97)`, spring(damping:20, stiffness:400) | 按下反馈 |
| 列表项入场 | `FadeInDown` delay=50ms | 列表加载 |
| 弹窗入场 | `SlideInDown` 280ms + `FadeIn` 200ms | 底部弹窗 |
| 脉冲呼吸 | `scale(1→1.15) + opacity(1→0.8)` 1.6s | 加载指示器 |
| 旋转加载 | `rotate(360deg)` 0.8s linear infinite | Spinner |

---

## 二、界面清单与设计分组

以下按功能模块列出所有需要 Stitch 输出设计稿的界面，共分为 **7 个设计分组 (Design Group)**，建议 Stitch 按 Group 粒度逐批生成。

---

### Group A: 应用外壳与全局导航

| # | 界面 ID | 界面名称 | 路由文件 | 描述 |
|---|---|---|---|---|
| A1 | `welcome` | 欢迎页 (Onboarding) | `app/welcome.tsx` | SVG 动画 Logo "Nexara"，语言选择 (中文/English)，首次启动仅显示一次 |
| A2 | `tab-bar` | 底部 Tab 导航 | `app/(tabs)/_layout.tsx` | 3 Tab (Chat/Library/Settings)，绝对定位浮于内容之上，毛玻璃背景 |
| A3 | `root-loading` | 全局加载屏幕 | `app/_layout.tsx` | ActivityIndicator 居中，品牌色，DB 初始化时短暂显示 |

#### A 组 Stitch 设计要求

- **A1 欢迎页**: 深色背景 (`#131315`)，居中 Logo 文字 "Nexara" 使用 `--font-heading: Manrope` 粗体，下方两个语言选择按钮使用 glass-panel 风格 (border-radius: 18px, glass border, 毛玻璃背景)，入场动画为书写描边动画
- **A2 Tab Bar**: 毛玻璃背景 (`blur(20px), rgba(0,0,0,0.6)`)，绝对定位在底部，高度 65px + 底部安全距离，3 个 Lucide 图标 (MessageSquare/Library/Settings)，激活态使用 `--accent` 色，非激活态使用 `--text-tertiary`。底部 0.5px glass border 作为顶部分隔线
- **A3 加载屏幕**: 纯色 `#131315` 背景，居中 ActivityIndicator 使用 `--accent` 色

---

### Group B: 聊天 Tab — 会话列表与 Agent 管理

| # | 界面 ID | 界面名称 | 路由文件 | 描述 |
|---|---|---|---|---|
| B1 | `chat-home` | 助手列表首页 | `app/(tabs)/chat.tsx` | 自定义 Agent 列表，支持搜索、置顶、左滑删除，右下角 FAB |
| B2 | `agent-sessions` | Agent 会话列表 | `app/chat/agent/[agentId].tsx` | 某个 Agent 下的历史会话列表，搜索栏 + 列表 |
| B3 | `agent-edit` | Agent 编辑器 | `app/chat/agent/edit/[agentId].tsx` | 编辑名称/头像/颜色/系统提示词/模型/温度/推理预设 |

#### B 组 Stitch 设计要求

- **B1 助手列表**:
  - 顶部大标题 "对话" + 副标题 "你的智能助手团队" (LargeTitleHeader 组件)
  - 右上角 "+" 按钮: 48x48px, border-radius: 16px, glass-panel 风格
  - 搜索栏: 毛玻璃背景, border-radius: 12px, 内部 Lucide Search 图标 + placeholder
  - Agent 列表项: 左侧头像(40x40, border-radius: 12px, 品牌色背景), 右侧名称+描述, 支持左滑操作(删除/置顶)
  - 列表项之间用 0.5px glass-border 分隔线
  - 右下角 FAB: 56x56px, 圆形, 品牌色背景 + Lucide Sparkles 图标, 悬浮阴影

- **B2 Agent 会话列表**:
  - GlassHeader 毛玻璃顶栏，返回按钮(40x40圆形, glass背景) + Agent 名称
  - 搜索栏 + 会话卡片列表(会话标题、最后消息预览、时间)
  - 右下角 "+" FAB 创建新会话

- **B3 Agent 编辑器**:
  - GlassHeader + 表单滚动区
  - 头像选择区: 中心大头像(80x80) + 10 个预设图标网格 (2 行 5 列, 每个图标 36x36 圆角 10px glass 背景) + 自定义上传按钮 + ColorPickerPanel 颜色选择器 (预设色圆点行 + RainbowSlider 自定义)
  - InferencePresets 三卡片: 精确(紫色 Code 图标, temp=0.1) / 均衡(青色 Zap, temp=0.7) / 创意(琥珀色 BookOpen, temp=1.2), 选中项品牌色边框+着色背景
  - 系统提示词预览: 虚线边框卡片 (rounded-2xl, border-dashed, border-white/20) + "已配置"/"未设置" 状态徽章 (rounded-full 小胶囊)
  - 模型选择器: 点击打开 GlassBottomSheet ModelPicker
  - 表单卡片: glass-panel 风格分组 (基本信息/模型设置/RAG配置/高级设置)
  - SettingsCard 组件统一: `rounded-3xl, border border-indigo-500/10, glass background`

---

### Group C: 聊天 Tab — 对话界面 (RN 侧原生组件)

> 注意: 对话界面中 WebView 消息列表已在 Group F 中完成，此 Group 覆盖 RN 侧的原生 UI 层。

| # | 界面 ID | 界面名称 | 路由文件 | 描述 |
|---|---|---|---|---|
| C1 | `chat-main` | 聊天主页 | `app/chat/[id].tsx` | WebView 消息区 + RN 顶部栏 + 底部输入栏 |
| C2 | `chat-settings` | 会话设置页 | `app/chat/[id]/settings.tsx` | 系统提示词编辑、关联知识库文件夹、导出、删除 |
| C3 | `session-settings-sheet` | 会话设置底部弹窗 | `features/chat/components/SessionSettingsSheet/` | 4 Tab (模型/思考级别/统计/工具) |
| C4 | `workspace-sheet` | 工作区底部弹窗 | `features/chat/components/WorkspaceSheet/` | 多 Tab (产物/文件/任务), 全屏详情模态 |
| C5 | `spa-settings` | 超级助手设置页 | `app/chat/super_assistant/settings.tsx` | 与 C2 类似但面向 SPA |
| C6 | `spa-rag-config` | 超级助手 RAG 配置 | `app/chat/super_assistant/rag-config.tsx` | |
| C7 | `spa-advanced-retrieval` | 超级助手高级检索 | `app/chat/super_assistant/advanced-retrieval.tsx` | |
| C8 | `agent-rag-config` | Agent RAG 配置 | `app/chat/agent/edit/rag-config/[agentId].tsx` | |
| C9 | `agent-advanced-retrieval` | Agent 高级检索 | `app/chat/agent/edit/advanced-retrieval/[agentId].tsx` | |

#### C 组 Stitch 设计要求

- **C1 聊天主页**:
  - 顶部栏: 毛玻璃 GlassHeader (blur: 70, dark overlay: 0.15), 左侧返回圆形按钮, 中间 Agent 名称, 右侧设置齿轮按钮
  - 底部输入栏: 毛玻璃背景, 安全底部距离, 输入框(glass-bg, border-radius: 20px), 发送按钮(圆形, 品牌色), 顶部工具条(模型选择/思考级别/附件)
  - 中间区域: WebView 聊天渲染区 (已完成)

- **C2 会话设置页**:
  - GlassHeader + ScrollView 表单
  - 分组卡片: 系统提示词(textarea with glass border), 关联知识库(folder selector), 操作按钮(导出/清空/删除)
  - 使用统一的 SettingsCard + SettingsItem + SettingsSectionHeader 组件体系

- **C3 会话设置底部弹窗**:
  - GlassBottomSheet: 圆角 32px, 毛玻璃模糊(Header: blur 70, dark overlay 0.15)
  - 内部 Tab 栏: 水平 Tab 按钮 (模型/思考级别/统计/工具)
  - 各面板: 设置项列表, 滑块选择器, Switch 开关

- **C4 工作区底部弹窗**:
  - 与 C3 类似结构, 额外包含文件浏览器、产物卡片(带缩略图)、任务进度列表

- **C5 超级助手设置页**:
  - GlassHeader + ScrollView
  - 标题输入框 + FAB 外观区 (可折叠: 图标网格 + 颜色选择器 + 旋转/发光开关)
  - 模型配置区 (InferenceSettings) + 知识图谱区 (KG 开关 + 链接)
  - RAG 配置入口 + 高级检索入口 + 上下文管理面板
  - 全局知识统计: 3 个 MetricCard (文档数/会话数/向量数) + 清理按钮
  - 危险区: 删除按钮

- **C6~C9 RAG 配置/高级检索页**:
  - GlassHeader + 配置状态卡片 ("继承全局"/"自定义" 标签 + 重置按钮)
  - RAG 面板内容同 E10, 高级检索面板同 E11
  - 重置为全局按钮 (琥珀色, RefreshCw 图标)

#### C 组聊天内联功能组件设计要求

以下组件嵌入在聊天消息流中，需与已完成的主会话界面视觉风格保持一致：

- **ApprovalCard (审批卡片)**:
  - amber 边框容器 (审批场景) / blue 边框容器 (循环继续)
  - AlertTriangle/RotateCw 图标 + 标题 + 工具名 (等宽 Space Grotesk) + JSON 参数预览
  - 底部: 多行文本输入 (干预指令) + Reject (XCircle 中性) / Approve (Play 品牌色) 按钮

- **ExecutionModeSelector (执行模式选择器)**:
  - 紧凑药丸: 当前模式图标 (Zap/Shield/PlayCircle) + 标签, 或 "OFF" 灰色
  - GlassBottomSheet: 时间注入/Agent 技能/严格模式 开关 + auto/semi/manual 分段控制 (滑动指示器) + MCP 服务器/技能列表开关

- **RagOmniIndicator (RAG 进度指示器)**:
  - 内联胶囊: 状态图标 (随阶段变化: Search/BrainCircuit/Database/Brain/Library/Zap) + 状态文本
  - 薄进度条 (品牌色, 底部) + 网络统计 (tx/rx KB)
  - 完成后: Check 绿色图标 + 引用数 + 可展开 Chevron

- **RagReferences (RAG 引用)**:
  - 胶囊按钮: Library 图标 + 状态文本 + 进度条/Spinner (加载中) / Chevron (完成)
  - 展开列表: 引用卡片 — 来源名(粗体) + 相似度徽章 (原始灰色 + 重排绿色) + 内容预览 (5行) + 左边框绿色强调

- **RagDetailPanel (RAG 详情)**:
  - 全屏 Modal (模糊遮罩 + 居中卡片)
  - 2x2 指标网格: 总耗时/最大相似度/召回数/最终数
  - 重排序耗时 + 来源分布 + 查询变体列表

- **SummaryIndicator (摘要指示器)**:
  - 脉冲药丸 + 发光球动画 (scale 1→1.15, opacity 1→0.8, 1.6s 循环)
  - Brain 图标 (蓝色进行中) / Check 图标 (绿色完成) + "COMPRESSING..."/"SAVED X MEMORIES"

- **TaskMonitor (任务监控)**:
  - 可展开 BlurView 容器: 绿色脉冲点(进行中)/静态绿点(完成) + 任务标题 + 进度药丸 ("3/5 - 60%")
  - 展开步骤列表: 每步 — CheckCircle2(绿)/Loader2(旋转蓝)/XCircle(红)/SkipForward/Circle + 标题 + 描述
  - 干预卡片: amber 边框 + "Decision Required" + 干预文本 + 提示

- **TaskFinalResult (任务最终结果)**:
  - BlurView + 绿色 CheckCircle2 + "FINAL RESULT" 大写绿色粗体

- **ProcessingIndicator (处理指示器)**:
  - 胶囊: ActivityIndicator/Check + 状态文本 ("Slicing..."/"Generating summary...")
  - 展开后: 摘要预览 (蓝色强调) + 归档完成 (绿色强调) + 块预览列表

- **StreamCard (流式内容卡片)**:
  - 可选左侧指示条 (品牌色竖条) + 编号圆 + Markdown 内容区

- **消息子组件**:
  - AttachmentBlock: 文件图标 + 文件名 + 大小
  - ErrorBlock: 红色错误卡片 + 错误信息
  - GeneratedImage: 图片缩略图 + 点击放大 (ImageViewerModal)
  - ToolCallBlock: 终端图标 + 工具名 (等宽) + 参数预览

---

### Group D: 知识库 Tab — RAG 与知识管理

| # | 界面 ID | 界面名称 | 路由文件 | 描述 |
|---|---|---|---|---|
| D1 | `rag-home` | 知识库首页 | `app/(tabs)/rag.tsx` | 三大入口卡片(文档/记忆/图谱), 文件夹列表, 文档列表, 拖放上传 |
| D2 | `folder-detail` | 文件夹详情 | `app/rag/[folderId].tsx` | 文件夹内文档列表, 多选批量操作 |
| D3 | `doc-editor` | 文档编辑器 | `app/rag/editor.tsx` | 代码/文本编辑器, 语法高亮, 查看/编辑模式切换 |
| D4 | `knowledge-graph` | 知识图谱查看器 | `app/knowledge-graph.tsx` | 全屏可视化图谱, 过滤器, 节点编辑弹窗 |

#### D 组 RAG 交互组件设计要求

- **ImagePreviewModal**: 全屏 Modal + 图片居中 + 下滑关闭 + 分享按钮
- **KGEdgeEditModal / KGNodeEditModal**: GlassBottomSheet — 表单输入 (标签/关系/属性) + 保存/删除按钮
- **KGExtractionIndicator**: 内联胶囊 — BrainCircuit 图标 + 提取状态文本 + 进度条
- **MemoryItem**: 记忆卡片 — Brain 图标 + 内容文本 + 时间戳 + 删除按钮
- **PdfExtractor**: 进度卡片 — 文件名 + 提取进度条 + 页数/总页数 + 取消按钮
- **TagAssignmentSheet**: GlassBottomSheet — 标签列表 (圆角胶囊 TagCapsule) + 多选开关 + 确认按钮
- **TagCapsule**: 圆角胶囊 — 标签文本 + 品牌色边框 (选中) / glass 边框 (未选中)
- **TagManagerSheet**: GlassBottomSheet — 标签列表 + 新增标签输入框 + 删除按钮

- **D1 知识库首页**:
  - 顶部大标题 "知识库" + 副标题
  - 三大入口卡片: 横向等分 3 列, glass-panel 风格, 圆角 20px, 中心图标(48px) + 数字徽章 + 标签
    - 文档卡片: Lucide BookOpen 图标, accent 色
    - 记忆卡片: Lucide Brain 图标, indigo 色
    - 图谱卡片: Lucide Network 图标, emerald 色
  - 搜索栏: 同 B1
  - 文件夹列表: 文件夹图标(32px, 圆角 8px, 带色底) + 名称 + 文档计数
  - 文档列表: 文档图标 + 标题 + 向量化状态指示(绿色点=已完成, 灰色=待处理)
  - 拖放上传区: 虚线边框 + 上传图标 + 提示文字

- **D2 文件夹详情**:
  - GlassHeader (返回按钮 + 文件夹名称)
  - 控制栏: 全选/批量操作按钮
  - 文档项: 复选框 + 文档图标 + 标题 + 状态标签(已向量化/处理中/失败)

- **D3 文档编辑器**:
  - GlassHeader (返回 + 文档标题 + 编辑/查看切换 + 保存按钮)
  - 编辑区: 全宽代码编辑器, 行号, 语法高亮
  - 底部工具栏: 字符统计 + 只读/编辑模式指示

- **D4 知识图谱**:
  - 全屏暗色背景 (`#0e0e10`)
  - 顶部毛玻璃工具栏 (过滤条件: 文档/文件夹/会话/Agent)
  - 图谱节点: 圆形, 品牌色边框, 悬浮显示详情
  - 节点编辑弹窗: GlassBottomSheet 风格

---

### Group E: 设置 Tab

| # | 界面 ID | 界面名称 | 路由文件 | 描述 |
|---|---|---|---|---|
| E1 | `settings-home` | 设置首页 | `app/(tabs)/settings.tsx` | 所有设置入口汇总(提供商/RAG/搜索/Token/本地模型/工作台/备份/主题/技能) |
| E2 | `provider-form` | 提供商表单 | `src/features/settings/screens/ProviderFormScreen.tsx` | 新增/编辑 API 提供商配置 |
| E3 | `provider-models` | 提供商模型管理 | `src/features/settings/screens/ProviderModelsScreen.tsx` | 管理模型列表, 启用/禁用, 排序 |
| E4 | `search-config` | 搜索配置页 | `app/settings/search.tsx` | 搜索提供商选择/API Key/优先级排序 |
| E5 | `workbench` | 便携工作台 | `app/settings/workbench.tsx` | 启停本地服务器, 连接状态, 客户端列表 |
| E6 | `token-usage` | Token 用量统计 | `app/settings/token-usage.tsx` | 各提供商/模型消耗统计, 费用估算 |
| E7 | `local-models` | 本地模型管理 | `app/settings/local-models.tsx` | 端侧推理模型下载/管理 |
| E8 | `skills-settings` | 技能设置 | `app/settings/skills.tsx` | Agent 可用技能/工具管理 |
| E9 | `theme-settings` | 主题设置 | `src/features/settings/screens/ThemeSettingsScreen.tsx` | 强调色选择, 深浅模式切换 |
| E10 | `rag-config` | 全局 RAG 配置 | `app/settings/rag-config.tsx` | 向量化/分块策略配置 |
| E11 | `advanced-retrieval` | 高级检索配置 | `app/settings/advanced-retrieval.tsx` | 重排序/混合检索配置 |
| E12 | `rag-advanced` | RAG 高级设置 | `src/features/settings/screens/RagAdvancedSettings.tsx` | 分块参数/Embedding 模型配置 |
| E13 | `rag-debug` | RAG 调试面板 | `app/settings/rag-debug.tsx` | 向量状态统计/调试信息 |
| E14 | `backup-settings` | 备份设置 | `src/features/settings/BackupSettings.tsx` | 自动/手动备份, 恢复 |

#### E 组 Stitch 设计要求

- **E1 设置首页**:
  - 用户头像区(圆形, 品牌色渐变边框) + 名称/版本号
  - 分组设置项: 每组使用 SettingsSectionHeader (Manrope 10px, 大写, `--text-tertiary`)
  - SettingsItem: 左侧图标(20px, 圆角 8px, 品牌色底) + 标题 + 右侧箭头/值
  - 分组: "模型与推理" / "知识库" / "搜索" / "工具" / "系统"
  - 每个分组用 glass-panel 卡片包裹

- **E2 提供商表单**:
  - GlassHeader + 表单 ScrollView
  - 表单字段: 输入框(glass-bg, border-radius: 12px), 下拉选择器, 模型列表
  - 保存按钮: 品牌色圆角按钮 (border-radius: 12px)

- **E3 提供商模型管理**:
  - GlassHeader + 搜索栏 + 模型列表
  - 模型项: 模型名 + 启用 Switch + 拖拽手柄
  - 底部添加模型输入框

- **E5 便携工作台**:
  - GlassHeader
  - 服务器状态卡: 大号状态指示器(绿色=运行/灰色=停止), URL 显示, 复制按钮
  - 访问码显示: 等宽字体, 大号数字
  - 连接客户端列表: 设备图标 + IP + 时间

- **E6 Token 用量**:
  - GlassHeader
  - 汇总卡: 总 Token 数, 总费用, 品牌色渐变背景
  - 提供商列表: 按提供商展开显示模型级别统计
  - 清空按钮: danger 变体

- **E8 技能设置**:
  - GlassHeader
  - 循环限制区: +/- 步进器 (数字居中显示, 100="∞" 无限) + 无限模式 amber 警告横幅
  - 3 Tab 动画切换 (预设/用户/MCP): 滑动指示器动画
  - 预设技能: 开关 + 技能 ID 等宽徽章 + 描述
  - 用户技能: 开关 + 编辑/配置/删除操作链接
  - MCP 服务器: 添加表单 (名称+URL+类型SSE/HTTP) + 服务器卡片列表 (连接绿点/断开红点 + 名称 + URL + 同步/删除按钮 + 工具列表 + 调用间隔步进 + 启用/默认开关)

- **E10 全局 RAG 配置**:
  - GlassHeader + ScrollView
  - 3 预设卡片行 (均衡/写作/编程, 各带图标, 选中项品牌色边框+着色背景)
  - 5 个滑块组: 文档分块大小 (200-2000) / 分块重叠 (0-500) / 记忆分块大小 (500-2000) / 上下文窗口 (10-50) / 摘要触发阈值 (5-30)
  - 摘要模板编辑器: 虚线边框预览区 + "已配置"/"使用默认" 状态徽章
  - 向量统计仪表盘: 3 列 MetricCard (文档数/向量数/存储 MB)
  - "清除向量数据" 红色按钮 + "清理孤立数据" 琥珀色按钮

- **E11 高级检索配置**:
  - GlassHeader + ScrollView
  - 5 个 Section (每 Section 用 SettingsSectionHeader 分隔):
    - 记忆检索: 数量限制滑块 (3-10) + 相似度阈值滑块 (50%-95%)
    - 文档检索: 数量限制滑块 (5-15) + 相似度阈值滑块 (30%-80%)
    - 重排序: 启用开关 + 召回数量滑块 (10-100) + 最终数量滑块 (3-20); 启用后记忆/文档滑块置灰并显示 "Rerank" 徽章
    - 查询改写: 启用开关 + 策略 3 按钮 (hyde/multi-query/expansion) + 变体数量滑块 (2-5)
    - 混合搜索: 启用开关 + 向量权重滑块 (0-100%) + BM25 增益滑块 (0.5x-2.0x)
    - 可观测性: 3 个开关 (进度/详情/指标)

- **E12 RAG 高级设置**:
  - GlassHeader "知识图谱"
  - KG 启用开关 + 提取模型选择器 (链接)
  - JIT 微图区: JIT 开关 + 最大块数输入 + 免费模式开关 + 域名自动开关
  - 成本策略: 3 单选按钮 (摘要优先/按需/全扫描)
  - 本地优化: 增量哈希开关 + 规则预过滤开关
  - 提取提示词: 预览卡片 + 重置按钮 + 警告横幅
  - "查看完整图谱" 链接按钮

- **E13 RAG 调试面板**:
  - GlassHeader "向量统计" + Database 图标 + 刷新按钮
  - 概览卡片: 大号总向量数 + 存储大小 (MB)
  - 类型分布: 文档向量数 + 记忆/摘要向量数
  - 存储健康: 冗余率百分比 (>20% 红色, ≤20% 绿色) + 清理按钮 (冗余>1% 时)
  - Top 会话: 会话 ID 列表 + 向量数药丸徽章

- **E14 备份设置**:
  - 可折叠内容选择: 会话/知识库/文件/设置/密钥 5 个开关
  - 本地存储: 导出按钮 + 导入按钮
  - WebDAV 云端: 启用开关 + 自动备份开关 + 上传/下载按钮 + 配置按钮
  - 配置弹窗 (GlassBottomSheet): URL + 用户名 + 密码 + 测试/保存按钮
  - GlassHeader
  - 模式选择: 三个选项(浅色/深色/跟随系统), 选中项有品牌色边框
  - 强调色选择器: 颜色圆点阵列 + 自定义 (RainbowSlider)

---

### Group F: WebView 聊天渲染器 (已完成)

| # | 界面 ID | 状态 |
|---|---|---|
| F1 | WebView 主会话界面 (Dark) | ✅ 已完成 |
| F2 | WebView 主会话界面 (Light) | ✅ 已完成 |
| F3 | WebView 高级组件展示 | ✅ 已完成 |

> 此 Group 无需 Stitch 重新设计，作为设计基准参考。

---

### Group G: 全局覆盖层与通用组件

| # | 组件 ID | 组件名称 | 描述 |
|---|---|---|---|
| G1 | `glass-header` | 毛玻璃顶栏 | 所有二级页面的统一顶部导航 |
| G2 | `glass-bottom-sheet` | 毛玻璃底部弹窗 | 底部弹出的设置/操作面板 |
| G3 | `glass-alert` | 毛玻璃弹窗 | 确认/警告弹窗 |
| G4 | `confirm-dialog` | 确认对话框 | 危险操作确认 |
| G5 | `context-menu` | 上下文菜单 | 长按/右键菜单 |
| G6 | `toast` | Toast 通知 | 操作反馈通知 |
| G7 | `token-stats-modal` | Token 统计弹窗 | 聊天中快速查看 Token 消耗 |
| G8 | `image-viewer-modal` | 图片查看器 | 全屏查看图片 (双指缩放/下滑关闭) |
| G9 | `select-text-modal` | 文本选择模态 | 长按选择文本 |
| G10 | `floating-code-editor` | 浮动代码编辑器 | 全屏编辑技能代码/JSON |
| G11 | `floating-text-editor` | 浮动文本编辑器 | 全屏编辑系统提示词/摘要模板 |
| G12 | `artifact-renderer` | Artifact 渲染器 | 工具执行产物展示 (图表/表格) |

#### G 组 Stitch 设计要求

- **G1 GlassHeader**: 
  - blur(70px) + dark overlay `rgba(0,0,0,0.15)` (dark) / `rgba(255,255,255,0.25)` (light)
  - 高度 64px + 安全区, 左右操作按钮 40x40 圆形 glass 背景
  - 底部分隔线 0.5px `rgba(255,255,255,0.08)`

- **G2 GlassBottomSheet**: 
  - 外层容器: border-radius 32px, 1px glass-border, lg 阴影
  - BlurView intensity 70 + dark overlay 0.15
  - 标题: 22px/900/Manrope, 副标题: 13px/500/Inter
  - 关闭按钮: 36x36 圆形, glass 背景, Lucide X 18px

- **G3 GlassAlert**: 
  - 毛玻璃模糊弹窗, 居中显示
  - 标题 + 描述文字 + 操作按钮(确认/取消)

- **G5 Context Menu**: 
  - 毛玻璃背景, 圆角 12px, sm 阴影
  - 菜单项: 图标(16px) + 文字, hover/active 高亮

- **G6 Toast**: 
  - 底部弹出, 毛玻璃背景, border-radius 16px
  - 图标 + 消息文本, 自动消失

- **G7 Token 统计弹窗**: 
  - GlassBottomSheet 容器
  - 大号 Token 总数在 160px 虚线圆环内 (brand 色)
  - 3 个 MetricRow: Prompt (紫色 MessageSquare 图标) + Completion (琥珀色 Zap 图标) + RAG System (绿色 Database 图标), 各带品牌色进度条
  - 底部: 红色 "重置" 按钮 (RotateCcw 图标) + "预估" 免责声明

- **G8 图片查看器**: 
  - 全屏 Modal (暗色遮罩 rgba(0,0,0,0.9))
  - 图片居中适配, 右上角关闭按钮 (36x36 圆形 glass) + 分享按钮
  - 支持双指缩放 + 双击放大 + 下滑关闭手势

- **G9 文本选择模态**: 
  - GlassBottomSheet 风格
  - 文本内容区 (可选中高亮) + 底部复制按钮

- **G10 浮动代码编辑器**: 
  - 全屏 Modal (深色背景 #0e0e10)
  - 顶部栏: 标题 (Space Grotesk 等宽) + 保存按钮 (品牌色) + 关闭按钮
  - 代码编辑区: 等宽字体 + 行号 + 语法高亮色

- **G11 浮动文本编辑器**: 
  - 全屏 Modal
  - 顶部栏: 标题 + 保存按钮 + 关闭按钮
  - 多行文本编辑区 (glass-bg, 无边框, 大面积)

- **G12 Artifact 渲染器**: 
  - 产物容器: glass-panel 边框 + rounded-2xl
  - 类型徽章头: 图标 (16px) + 大写类型标签 (CHART/TABLE 等, Space Grotesk, 12px)
  - 内容区: 由 RendererRegistry 分派 (ECharts/Mermaid/表格等)

---

### Group H: 技能与执行系统

| # | 组件 ID | 组件名称 | 描述 |
|---|---|---|---|
| H1 | `core-memory-list` | 核心记忆列表 | 用户的核心记忆 (偏好/事实/上下文) |
| H2 | `tool-execution-timeline` | 工具执行时间线 | Agent 执行步骤详情 (思考/工具/搜索/干预) |
| H3 | `model-picker` | 模型选择器 | 底部弹窗选择模型 |

#### H 组 Stitch 设计要求

- **H1 核心记忆列表**:
  - FlashList 卡片列表: 每卡 — Brain 图标 (16px) + 分类标签 (大写药丸: PREFERENCE/FACT/CONTEXT, 不同颜色) + 记忆内容文本 + 右侧红色删除按钮 (Trash2)
  - 卡片: glass-panel 背景, rounded-2xl
  - 空状态: 居中 Brain 图标 + "还没有保存核心记忆" 灰色文本

- **H2 工具执行时间线**:
  - 可折叠 BlurView 容器: Brain 图标 + 摘要 (如 "思考 3 轮, 使用工具 2 轮") + Chevron
  - 时间线 (ScrollView, max-height 35vh): 
    - 每步: 图标圆 (Brain=紫/Terminal=蓝/AlertCircle=红/Globe=绿/Database=青) + 垂直连接线 (--border-default 色) + 标题 + 预览文本
    - 展开后: JSON 参数 (等宽) / Markdown 思考 / RAG 引用列表 / 搜索结果
  - 干预区 (amber 左边框 + amber 背景 5%): "Decision Required" + 工具名 + 原因 + 多行输入 + Reject/Approve 按钮
  - 循环活跃干预: 底部固定输入栏 (发送按钮)

- **H3 模型选择器**:
  - GlassBottomSheet: 搜索栏 (Search 图标, 150ms 防抖)
  - 模型列表: 每项 — 模型图标 (16px) + 模型名 (粗体 Inter) + 提供商名 (Server 图标, secondary 色) + 能力标签 (彩色药丸: Reasoning=紫/Vision=粉/Web=天蓝/Rerank=橙/Embedding=青/Chat=翠绿) + 上下文长度标签 + 选中 ✓ (品牌色)
  - 空状态: Cpu 图标 + "无可用模型"

---

## 三、Stitch 输出规范

### 3.1 输出格式

每个界面请输出完整的 HTML + Tailwind CSS (内联工具类), 符合以下规范:

1. **HTML**: 语义化 HTML5, 使用 Tailwind 工具类
2. **CSS**: 仅使用 CSS 变量 (如 `bg-[var(--bg-primary)]`) 或 Tailwind 内联类 (如 `bg-white dark:bg-[#131315]`)
3. **图标**: 使用 Lucide Icons SVG 内联或 Material Symbols CDN
4. **字体**: 通过 `<link>` 加载 Google Fonts (Manrope/Inter/Space Grotesk)
5. **暗色模式**: 默认展示暗色模式, 使用 `class="dark"` + Tailwind dark: 变体
6. **毛玻璃**: 使用 `backdrop-blur-xl` (20px) + `bg-white/3 dark:bg-white/[0.03]` + `border border-white/10`
7. **边框**: 统一 `0.5px` (Tailwind: `border`)
8. **圆角**: 列表项 `rounded-2xl`, 卡片 `rounded-3xl`, 按钮 `rounded-xl`, 标签 `rounded-full`
9. **尺寸基准**: iPhone 15 Pro (393×852), 内容宽度 `max-w-md mx-auto`

### 3.2 设计语言一致性检查清单

每个界面设计稿必须通过以下检查:

- [ ] 背景色: Dark `#131315` / Light `#ffffff`
- [ ] 毛玻璃: `backdrop-blur-xl` + `0.5px border-white/10` + `inset shadow`
- [ ] 字体: 标题 Manrope, 正文 Inter, 代码 Space Grotesk
- [ ] 间距: 4px 网格系统 (4/8/12/16/20/24/32)
- [ ] 圆角: sm(8px) / md(12px) / lg(16px) / xl(18px) / full(9999px)
- [ ] 品牌色: Indigo `#6366f1`(light) / `#c0c1ff`(dark)
- [ ] 状态色: 成功 `#10b981`, 错误 `#ef4444`, 警告 `#f59e0b`
- [ ] 按钮弹性动画: `active:scale-[0.96]`
- [ ] 列表项入场: FadeInDown 带延迟
- [ ] 分隔线: 0.5px, `rgba(255,255,255,0.08)` (dark) / `rgba(0,0,0,0.08)` (light)

### 3.3 优先级建议

建议 Stitch 按以下优先级顺序生成设计稿:

1. **Group G** (通用组件) — 所有页面复用，包括 G7~G12 新增组件
2. **Group A** (外壳导航) — 全局影响最大
3. **Group B** (聊天 Tab 列表) — 核心入口
4. **Group E** (设置 Tab) — 页面数量最多
5. **Group C** (聊天对话) — 核心交互，含内联功能组件
6. **Group D** (知识库) — 复杂度最高
7. **Group H** (技能与执行系统) — 可与 C 组并行

> **注意**: 各界面的详细功能需求 (UI 元素清单、交互规格、数据依赖、状态变化) 参见 `stitch-ui-functional-reference.md`

---

## 四、技术对接说明

### 4.1 实现方案

Nexara 前端迁移采用 **Jetpack Compose** (Android) 实现，详见 `native-migration-roadmap.md`。

Stitch 输出的 HTML+Tailwind 设计稿将作为视觉基准，最终由 Compose 原生代码 1:1 还原。

### 4.2 RN 侧毛玻璃实现 (当前 RN 版本)

使用 `expo-blur` 的 `<BlurView>` 组件:

```tsx
<BlurView
  intensity={Glass.Header.intensity}  // 70 (iOS) / 50 (Android)
  tint={isDark ? 'dark' : 'default'}
  experimentalBlurMethod="dimezisBlurView"
>
  {/* Semi-transparent overlay */}
  <View style={{
    backgroundColor: isDark 
      ? `rgba(0,0,0,${Glass.Header.opacity.dark})` 
      : `rgba(255,255,255,${Glass.Header.opacity.light})`
  }} />
  {/* Content */}
</BlurView>
```

### 4.3 Stitch HTML → Compose 代码映射

| Stitch (HTML/Tailwind) | Compose 实现 |
|---|---|
| `bg-[#131315]` | `Modifier.background(Color(0xFF131315))` |
| `backdrop-blur-xl` | `Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP) }` |
| `border border-white/10` | `Modifier.border(0.5.dp, Color.White.copy(alpha = 0.1f))` |
| `rounded-2xl` | `Modifier.clip(RoundedCornerShape(16.dp))` |
| `font-['Manrope']` | `FontFamily(Font(R.font.manrope))` |
| `shadow-lg` | `Modifier.shadow(Elevation(Level4))` |
| `active:scale-[0.96]` | `animateFloatAsState(if (pressed) 0.96f else 1f) + Modifier.graphicsLayer` |

> **注意**: Stitch 输出的 HTML+Tailwind 设计稿仍保持原有格式规范，作为视觉参考。Compose 代码映射由开发阶段完成。

---

## 五、参考基准

已完成的 WebView 聊天界面设计稿 (3 个 Stitch Screen) 作为视觉基准:

1. **主会话流 (Dark Mode)** — 消息气泡、代码块、图表卡片、RAG 指示器、操作按钮
2. **暗色模式与图表渲染** — 图表卡片 header/content 结构、环境光效
3. **高级功能组件展示** — 时间线、审批卡片、任务监控、处理指示器

Stitch 项目名称: **"Modern Session UI Redesign"**

---

*文档结束*
