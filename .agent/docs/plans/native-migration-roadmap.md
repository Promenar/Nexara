# Nexara 前端原生迁移 — 分阶段实施计划

> **文档版本**: v2.0 (2026-05-01)
> **v1.0 变更摘要**: 架构从 Android-only Jetpack Compose 调整为 Multiplatform-Ready 策略；新增 SSE 流解析器 Kotlin 协程重写；项目目录从 `android/app/` 迁移到根目录 `compose-ui/`；补充遗漏的 UI 组件和界面覆盖
> **决策背景**: RN 框架 UI 层的"纸糊感"无法达到设计精度要求；WebView 方案在流式 Markdown 渲染中性能和还原度均不理想
> **核心策略**: 迁移 UI 可视前端 + SSE 流解析管线，使用 Multiplatform-Ready 选库策略（当前仅编译 Android，后期 CMP 扩展成本 ~10%）
> **目标平台**: Android (Kotlin/Jetpack Compose) → iOS/桌面 (CMP，后期低成本扩展)
> **已完成**: Phase 1 POC — 主会话界面 Stitch 设计方案落地，视觉验证通过

---

## 一、架构决策摘要

### 1.1 迁移范围界定

```
┌──────────────────────────────────────────────────────────────────┐
│                        Android APK                               │
│                                                                  │
│  ┌──────────────────────────────┐  ┌───────────────────────────┐ │
│  │     RN JS 层（保留不动）       │  │  Kotlin 原生层（新增）      │ │
│  │                              │  │                           │ │
│  │  · LLM Provider × 7          │  │  · Compose UI 渲染        │ │
│  │  · RAG 引擎 (16 文件)         │  │  · 原生动画/毛玻璃         │ │
│  │  · DB Schema + Repository    │  │  · 原生 Markdown 渲染      │ │
│  │  · MCP 协议                  │  │  · Navigation 转场         │ │
│  │  · Zustand Store × 11        │  │  · 主题/动效系统            │ │
│  │  · 技能系统                   │  │  · SSE 流解析管线 ★        │ │
│  │  · 备份/日志/工具              │  │                           │ │
│  └──────────┬───────────────────┘  └──────────┬────────────────┘ │
│             │         Native Module Bridge       │                │
│             │◄──────────────────────────────────►│                │
│  ┌──────────┴───────────────────────────────────┴────────────────┐│
│  │                   Android Framework 层                         ││
│  │  Activity · Window · RenderEffect · DataStore · Ktor          ││
│  └──────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

### 1.2 不迁移的内容（RN JS 层保留）

| 模块 | 路径 | 代码量 |
|------|------|--------|
| LLM Provider × 7 | `src/lib/llm/providers/` | ~163KB |
| RAG 引擎 | `src/lib/rag/` (16 文件) | ~180KB |
| 数据库层 | `src/lib/db/` | ~52KB |
| MCP 协议 | `src/lib/mcp/` | ~10KB |
| Zustand Store × 11 | `src/store/` | ~200KB+ |
| 技能系统核心 | `src/lib/skills/` | ~12KB |
| 工具函数库 | `src/lib/utils/`, `src/lib/file/` | ~15KB |
| 类型定义 | `src/types/` | ~30KB |
| 备份/日志/服务 | `src/lib/backup/`, `src/lib/logging/`, `src/services/` | ~40KB |

### 1.3 迁移替换的内容（Kotlin 原生重写）

| 模块 | 路径 | 替换方案 |
|------|------|---------|
| **SSE 流解析管线 ★** | `src/lib/llm/stream-parser.ts` (17KB) | **Kotlin 协程 Flow 状态机** |
| **思考标签检测器 ★** | `src/lib/llm/thinking-detector.ts` | **Kotlin 协程 Channel** |
| **流式缓冲管理器 ★** | `src/lib/llm/stream-buffer.ts` | **Kotlin StateFlow + 边界检测** |
| 路由系统 (34 文件) | `app/**` (Expo Router) | Jetpack Navigation Compose |
| UI 基础组件 (28 组件) | `src/components/ui/` | Material 3 自定义组件 |
| 聊天 UI 组件 (12 组件) | `src/components/chat/` | Compose Chat UI |
| 聊天功能组件 (20+ 组件) | `src/features/chat/components/` | Compose 重写 |
| RAG UI 组件 (18 组件) | `src/components/rag/` | Compose 重写 |
| 设置 UI 组件 | `src/features/settings/`, `src/components/settings/` | Compose Preference |
| 技能 UI 组件 | `src/components/skills/` | Compose 重写 |
| 主题系统 | `src/theme/` | Material Theme + 自定义 Token |
| WebView 渲染器 | `src/web-renderer/` | **废弃**，原生 Markdown 渲染引擎替代 |
| NativeWind/Tailwind | `global.css`, `tailwind.config.js` | **废弃**，Compose Modifier 体系 |
| 原生桥接模块 | `src/native/` | **直接 Kotlin 实现**（无需桥接） |

### 1.4 SSE 流解析器 Kotlin 协程重写

原 JS 实现运行在主线程中，Kotlin 协程方案将获得显著性能提升：

| JS 模块 | Kotlin 替代 | 实现策略 |
|---------|------------|---------|
| `StreamParser` — 增量流解析 (tool_call XML、plan blocks、代码围栏) | `StreamParser.kt` | `Flow<String>` 增量状态机，避免 O(N²) 正则回溯 |
| `ThinkingDetector` — 多格式思考标签检测 (`<think/>`, `<thought/>`, `<!-- THINKING_* -->`) | `ThinkingDetector.kt` | `Channel<ParseResult>` 背压控制 + 跨 chunk 边界检测 |
| `StreamBufferManager` — 思考/正文分离、边界抖动处理 | `StreamBufferManager.kt` | `MutableStateFlow<ParsedContent>` + 标签匹配状态机 |
| `stream-parser.ts` 中的 Provider 分发 | `KtorSseClient.kt` | Ktor SSE 连接管理 + 协程 Flow 管道 |

**性能预期**：
- 首 Token 延迟: JS ~200-500ms → Kotlin 协程 **< 30ms**
- 流解析吞吐: JS 正则回溯瓶颈 → Kotlin 状态机字节码效率数量级提升
- 背压控制: JS 无背压 → Kotlin Channel 天然背压

### 1.5 Multiplatform-Ready 选库策略

当前仅编译 Android，但所有依赖库选择 KMP 兼容方案，后期 CMP 扩展成本降至 ~10%：

| 模块 | 选用库 | 替代 Android-only 方案 | CMP 兼容性 |
|------|--------|----------------------|-----------|
| 网络/SSE | **Ktor Client + ktor-sse** | ~~OkHttp SSE~~ | ✅ 跨平台 |
| 图片加载 | **Coil 3** | ~~Coil 2~~ | ✅ 跨平台 |
| 数据存储 | **DataStore** | ~~MMKV~~ | ✅ 跨平台 |
| UI 框架 | Jetpack Compose (AndroidX) | 不变 | CMP 自动映射 |
| 导航 | Navigation Compose | 不变 | 后期可换 Voyager |
| Markdown | compose-richtext | 不变 | ✅ |
| 序列化 | kotlinx.serialization | 不变 | ✅ 跨平台 |

### 1.6 编译兼容性确认

RN 和 Compose 在同一 APK 中共存**零冲突**：
- `apply plugin: "com.facebook.react"` — 管理 Metro/JS Bundle 打包
- `apply plugin: "org.jetbrains.kotlin.android"` — 管理 Kotlin 编译
- 添加 Compose 只需在 `dependencies {}` 增加标准 AndroidX 库
- 新 Activity 继承 `ComponentActivity`（非 `ReactActivity`），完全独立

---

## 二、整体迁移路线图

```
Phase 0 (准备)          Phase 1 (核心验证)         Phase 2 (扩展覆盖)        Phase 3 (完善清理)
─────────────          ─────────────────          ──────────────────        ──────────────────
技术验证 + 环境搭建     主会话界面 POC             全部二级界面迁移            清理 RN UI 依赖
                       ↓                          ↓                         ↓
· Gradle 配置          · NativeChatActivity       · Batch 1: 全局组件+导航    · 移除 WebView 渲染器
· Compose 依赖         · 流式 Markdown 引擎       · Batch 2: 聊天 Tab 首页    · 移除 NativeWind
· 设计 Token 映射      · SSE 直连 + 流解析管线     · Batch 3: 设置 Tab        · 移除 expo-blur
· 毛玻璃效果验证        · 毛玻璃 UI 验证            · Batch 4: 聊天二级界面     · APK 体积优化
· Ktor/Coil3 配置      · Stitch 设计稿 1:1 还原    · Batch 5: 知识库 Tab       · ProGuard 调优
                       · Artifact 系统             · Batch 6: 技能+执行系统     · 准备 CMP 扩展
                       ↓                          ↓                         ↓
                      关键决策点:                  全量 UI 切换              最终 APK 纯原生
                      确认继续或调整方向                                      (可选 CMP 跨端)
```

---

## 三、Phase 0 — 技术准备与基础设施 (预计 3-4 天)

### 0.1 目标

搭建 `compose-ui/` 目录的 Kotlin 原生开发基础设施，验证 Compose + RN 混合编译可行性。

### 0.2 任务清单

| # | 任务 | 产出文件 | 说明 |
|---|------|---------|------|
| 0.1 | 创建 `compose-ui/` 项目目录 | `compose-ui/build.gradle.kts` | 独立的 Android Library/Application 模块 |
| 0.2 | Gradle 添加 Compose BOM | `compose-ui/build.gradle.kts` | 添加 `android { buildFeatures { compose = true } }` + Compose BOM |
| 0.3 | 添加 Ktor + Coil 3 依赖 | `compose-ui/build.gradle.kts` | Multiplatform-Ready 选库 |
| 0.4 | 创建 Compose 主题系统 | `compose-ui/.../ui/theme/` | 从 Stitch 设计规范提取 Token，映射为 Compose `Color`/`Typography`/`Shape` |
| 0.5 | 创建基础 Native Activity | `compose-ui/.../MainActivity.kt` | 继承 `ComponentActivity`，`setContent` 加载空 Compose 界面 |
| 0.6 | 验证混合编译 | — | `./gradlew assembleDebug` 确认 RN + Compose 共存无报错 |
| 0.7 | 毛玻璃效果验证 | 测试 Composable | 验证 `Modifier.graphicsLayer { renderEffect = ... }` 在目标设备上的效果 |
| 0.8 | 字体资源导入 | `compose-ui/.../res/font/` | 下载 Manrope/Inter/Space_Grotesk 字体文件 |
| 0.9 | AndroidManifest 注册 | `AndroidManifest.xml` | 注册 `MainActivity` |
| 0.10 | DataStore 初始化 | `compose-ui/.../data/local/DataStoreManager.kt` | 替代 MMKV，RN ↔ Native 数据共享 |

### 0.3 Compose 依赖清单

```groovy
// compose-ui/build.gradle.kts
android {
    buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    // Compose BOM
    def composeBom = platform('androidx.compose:compose-bom:2024.12.01')
    implementation composeBom

    implementation "androidx.compose.ui:ui"
    implementation "androidx.compose.material3:material3"
    implementation "androidx.compose.ui:ui-tooling-preview"
    implementation "androidx.activity:activity-compose:1.9.0"
    implementation "androidx.navigation:navigation-compose:2.7.7"
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0"

    // Markdown 渲染
    implementation "com.halilibo:compose-richtext:0.20.0"

    // 网络 (Multiplatform-Ready)
    implementation "io.ktor:ktor-client-android:2.3.7"
    implementation "io.ktor:ktor-client-okhttp:2.3.7"
    implementation "io.ktor:ktor-client-websockets:2.3.7"
    implementation "io.ktor:ktor-client-content-negotiation:2.3.7"

    // 图片 (Multiplatform-Ready)
    implementation "io.coil-kt.coil3:coil-compose:3.0.0"
    implementation "io.coil-kt.coil3:coil-network-okhttp:3.0.0"

    // 数据存储 (Multiplatform-Ready)
    implementation "androidx.datastore:datastore-preferences:1.0.0"

    // 序列化 (Multiplatform-Ready)
    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2"

    // 协程
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"

    debugImplementation "androidx.compose.ui:ui-tooling"
}
```

### 0.4 设计 Token → Compose 映射

| Stitch Token | Compose 实现 |
|---|---|
| `--bg-primary: #131315` | `val Surface = Color(0xFF131315)` |
| `--bg-secondary: #201f22` | `val SurfaceContainer = Color(0xFF201F22)` |
| `backdrop-filter: blur(20px)` | `Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(20f, 20f, ...) }` |
| `border: 0.5px solid rgba(255,255,255,0.1)` | `Modifier.border(0.5.dp, Color.White.copy(alpha = 0.1f))` |
| `--font-heading: Manrope` | `val Heading = FontFamily(Font(R.font.manrope))` |
| `--radius-xl: 18px` | `val XL = 18.dp` |
| `active:scale(0.96) spring` | `Modifier.pointerInput { detectTapGestures ... } + animateFloatAsState` |

---

## 四、Phase 1 — 主会话界面 POC (预计 5-7 天)

> **这是整个迁移的关键决策点。** POC 成功后才启动后续阶段。

### 1.1 目标

用 Kotlin/Compose 从 0 实现主会话界面，按 Stitch 设计稿达到 1:1 还原，验证原生方案是否显著优于当前 RN+WebView 方案。

### 1.2 任务清单

| # | 任务 | 产出文件 | 依赖 |
|---|------|---------|------|
| **1.1** | **SSE 流处理管线 ★** | `.../data/remote/` | Ktor SSE |
| 1.1a | Ktor SSE 连接管理 | `KtorSseClient.kt` | — |
| 1.1b | 流式 Token 收集器 | `StreamingCollector.kt` | 1.1a |
| 1.1c | **StreamParser 协程重写** | `StreamParser.kt` | 1.1a |
| 1.1d | **ThinkingDetector 协程重写** | `ThinkingDetector.kt` | 1.1a |
| 1.1e | **StreamBufferManager 协程重写** | `StreamBufferManager.kt` | 1.1a |
| 1.1f | 与 RN Store 的数据共享通道 | `.../bridge/StoreBridge.kt` | DataStore |
| **1.2** | **Markdown 渲染引擎** | `.../ui/renderer/` | — |
| 1.2a | 增量 Markdown 解析器 | `IncrementalMarkdownParser.kt` | — |
| 1.2b | Compose 节点渲染器 | `MarkdownRenderer.kt` | 1.2a |
| 1.2c | 代码块高亮组件 | `CodeBlock.kt` | 1.2b |
| 1.2d | 流式光标动画 | `StreamingCursor.kt` | 1.2b |
| 1.2e | 思维链折叠组件 | `ThinkingBlock.kt` | 1.2b |
| **1.3** | **主会话 UI** | `.../ui/chat/` | — |
| 1.3a | 毛玻璃顶栏 | `ChatTopBar.kt` | Phase 0 Token |
| 1.3b | 消息列表 (LazyColumn) | `MessageList.kt` | 1.2b |
| 1.3c | 用户消息气泡 | `UserBubble.kt` | — |
| 1.3d | AI 消息气泡 | `AssistantBubble.kt` | 1.2b |
| 1.3e | 底部输入栏 | `ChatInputBar.kt` | — |
| 1.3f | 输入栏顶部工具条 | `ChatInputTopBar.kt` | — |
| 1.3g | 思考级别选择按钮 | `ThinkingLevelButton.kt` | — |
| 1.3h | 模型/思考级别选择条 | `ModelSelectorBar.kt` | — |
| **1.4** | **Artifact 系统** | `.../ui/renderer/` | — |
| 1.4a | Artifact 解析器 | `ArtifactParser.kt` | — |
| 1.4b | Artifact 模板注册表 | `ArtifactTemplateRegistry.kt` | 1.4a |
| 1.4c | ECharts 嵌入式 WebView | `EChartsView.kt` | AndroidView |
| 1.4d | Mermaid 嵌入式 WebView | `MermaidView.kt` | AndroidView |
| **1.5** | **集成与验证** | — | — |
| 1.5a | RN → NativeChatActivity 跳转桥接 | `NavigationModule.kt` | Native Module |
| 1.5b | 会话数据传递 | Intent extras / DataStore | 1.5a |
| 1.5c | 深色/浅色主题切换 | Theme 切换逻辑 | Phase 0 Token |
| 1.5d | 端到端测试 | — | 全部 |

### 1.3 SSE 流处理管线架构

```
Kotlin 协程流处理管线 (替代 JS 主线程实现)
──────────────────────────────────────────────

┌──────────┐     ┌───────────────┐     ┌──────────────────┐     ┌──────────────┐
│  LLM API  │────►│  KtorSseClient│────►│  StreamParser    │────►│  Compose UI  │
│  Server   │ SSE │  (Kotlin)     │Flow │  (协程状态机)     │Flow │  渲染        │
└──────────┘     └───────┬───────┘     └────────┬─────────┘     └──────────────┘
                         │                      │
                         │              ┌───────┴────────┐
                         │              │                │
                         │     ┌────────▼──────┐  ┌──────▼─────────┐
                         │     │ThinkingDetector│  │StreamBuffer    │
                         │     │(Channel背压)   │  │Manager         │
                         │     │                │  │(StateFlow)     │
                         │     └────────────────┘  └────────────────┘
                         │
                    首 Token: < 30ms
```

**对比 JS 方案**：
- JS: LLM API → RN JS SSE 解析 (主线程) → Native Module Event → Compose UI (~200-500ms)
- Kotlin: LLM API → Ktor SSE → 协程 Flow 管道 → Compose UI (< 30ms)

### 1.4 POC 验收标准

| 验收项 | 标准 |
|--------|------|
| 设计还原度 | 与 Stitch 设计稿对比，视觉差异 < 5%（RN 方案当前约 30-40% 差异） |
| 首 Token 延迟 | < 80ms（RN+WebView 方案当前 200-500ms） |
| 流解析吞吐 | 协程状态机 > JS 正则方案，无卡顿 |
| 滚动流畅度 | 长对话 (100+ 消息) 滚动帧率稳定 60fps |
| 毛玻璃效果 | 系统级 `RenderEffect`，视觉无断层 |
| 混合编译 | `./gradlew assembleDebug` 零错误 |
| 内存占用 | 主会话界面 < 80MB（WebView 方案当前 ~120MB+） |

### 1.5 关键决策点

POC 完成后评估：
- ✅ **通过** → 继续 Phase 2 全界面迁移
- ⚠️ **部分通过** → 分析差距，调整技术选型后继续
- ❌ **未通过** → 回退到 RN 方案继续迭代，不强制推进

---

## 五、Phase 2 — 全界面迁移扩展 (预计 4-5 周)

> Phase 1 POC 通过后启动。按设计组 (Design Group) 分批实施。
> 每个界面的详细功能需求参见 `stitch-ui-functional-reference.md`。

### 2.1 迁移优先级与批次

#### Batch 1: 全局组件 + 导航骨架 (3-5 天)

对应 Stitch 设计组 A + G。全局复用，必须最先完成。

| 界面/组件 | 原文件 | 新文件 |
|----------|--------|--------|
| A1 欢迎页 | `app/welcome.tsx` | `.../ui/welcome/WelcomeScreen.kt` |
| A2 底部 Tab 导航 | `app/(tabs)/_layout.tsx` | `.../ui/navigation/BottomTabBar.kt` |
| A3 全局加载 | `app/_layout.tsx` | `.../ui/common/LoadingScreen.kt` |
| G1 毛玻璃顶栏 | `src/components/ui/GlassHeader.tsx` | `.../ui/component/GlassTopBar.kt` |
| G2 毛玻璃底部弹窗 | `src/components/ui/GlassBottomSheet.kt` | `.../ui/component/GlassBottomSheet.kt` |
| G3 毛玻璃弹窗 | `src/components/ui/GlassAlert.tsx` | `.../ui/component/GlassAlertDialog.kt` |
| G4 确认对话框 | `src/components/ui/ConfirmDialog.tsx` | `.../ui/component/ConfirmDialog.kt` |
| G5 上下文菜单 | `src/components/ui/ContextMenu.tsx` | `.../ui/component/ContextMenu.kt` |
| G6 Toast 通知 | `src/components/ui/Toast.tsx` | `.../ui/component/ToastHost.kt` |
| G7 Token 统计弹窗 | `src/features/chat/components/TokenStatsModal.tsx` | `.../ui/component/TokenStatsModal.kt` |
| G8 图片查看器 | `src/features/chat/components/message/modals/ImageViewerModal.tsx` | `.../ui/component/ImageViewerModal.kt` |
| G9 文本选择模态 | `src/features/chat/components/message/modals/SelectTextModal.tsx` | `.../ui/component/SelectTextModal.kt` |
| G10 浮动代码编辑器 | `src/components/ui/FloatingCodeEditorModal.tsx` | `.../ui/component/FloatingCodeEditorModal.kt` |
| G11 浮动文本编辑器 | `src/components/ui/FloatingTextEditorModal.tsx` | `.../ui/component/FloatingTextEditorModal.kt` |
| G12 Artifact 渲染器 | `src/features/chat/components/ToolArtifacts.tsx` | `.../ui/component/ArtifactRenderer.kt` |
| — 全局搜索栏 | `src/components/ui/AnimatedSearchBar.tsx` | `.../ui/component/AnimatedSearchBar.kt` |
| — 模型选择器 | `src/features/settings/ModelPicker.tsx` | `.../ui/component/ModelPicker.kt` |

产出：
- 完整的 Compose Design System 组件库
- Jetpack Navigation 路由图
- 全局主题切换机制 (MD3 dynamicColor + 自定义 Token)

#### Batch 2: 聊天 Tab 首页 (3-4 天)

对应 Stitch 设计组 B。

| 界面 | 原文件 | 新文件 |
|------|--------|--------|
| B1 助手列表 | `app/(tabs)/chat.tsx` (8KB) | `.../ui/chat/AgentListScreen.kt` |
| B2 Agent 会话列表 | `app/chat/agent/[agentId].tsx` (7KB) | `.../ui/chat/SessionListScreen.kt` |
| B3 Agent 编辑器 | `app/chat/agent/edit/[agentId].tsx` (21KB) | `.../ui/chat/AgentEditorScreen.kt` |

关键组件迁移：
- `SwipeableAgentItem` → Compose `SwipeToDismiss`
- `AgentAvatar` → Compose 自定义 Avatar
- `SuperAssistantFAB` → Compose `FloatingActionButton`
- `InferencePresets` → Compose 三卡片预设选择器
- `ColorPickerPanel` → Compose 颜色选择器

#### Batch 3: 设置 Tab (5-7 天)

对应 Stitch 设计组 E。页面数量最多，但结构高度相似（表单列表），可批量化。

| 界面 | 原文件 | 新文件 |
|------|--------|--------|
| E1 设置首页 | `app/(tabs)/settings.tsx` (33KB) | `.../ui/settings/SettingsHomeScreen.kt` |
| E2 提供商表单 | `src/features/settings/screens/ProviderFormScreen.tsx` | `.../ui/settings/ProviderFormScreen.kt` |
| E3 提供商模型管理 | `src/features/settings/screens/ProviderModelsScreen.tsx` | `.../ui/settings/ProviderModelsScreen.kt` |
| E4 搜索配置 | `app/settings/search.tsx` (15KB) | `.../ui/settings/SearchConfigScreen.kt` |
| E5 便携工作台 | `app/settings/workbench.tsx` (18KB) | `.../ui/settings/WorkbenchScreen.kt` |
| E6 Token 用量 | `app/settings/token-usage.tsx` (15KB) | `.../ui/settings/TokenUsageScreen.kt` |
| E7 本地模型 | `app/settings/local-models.tsx` (25KB) | `.../ui/settings/LocalModelsScreen.kt` |
| E8 技能设置 | `app/settings/skills.tsx` + `src/components/settings/SkillsSettingsPanel.tsx` | `.../ui/settings/SkillsScreen.kt` |
| E9 主题设置 | `src/features/settings/screens/ThemeSettingsScreen.tsx` | `.../ui/settings/ThemeScreen.kt` |
| E10 全局 RAG 配置 | `app/settings/rag-config.tsx` + `GlobalRagConfigPanel.tsx` | `.../ui/settings/RagConfigScreen.kt` |
| E11 高级检索 | `app/settings/advanced-retrieval.tsx` + `AdvancedRetrievalPanel.tsx` | `.../ui/settings/AdvancedRetrievalScreen.kt` |
| E12 RAG 高级 | `src/features/settings/screens/RagAdvancedSettings.tsx` | `.../ui/settings/RagAdvancedScreen.kt` |
| E13 RAG 调试 | `app/settings/rag-debug.tsx` + `RagDebugPanel.tsx` | `.../ui/settings/RagDebugScreen.kt` |
| E14 备份设置 | `src/features/settings/BackupSettings.tsx` (23KB) | `.../ui/settings/BackupScreen.kt` |
| — 设置共享面板 | `AgentRagConfigPanel.tsx` / `AgentAdvancedRetrievalPanel.tsx` | `.../ui/settings/AgentRagConfigPanel.kt` 等 |
| — 上下文管理面板 | `src/features/chat/settings/ContextManagementPanel.tsx` | `.../ui/chat/ContextManagementPanel.kt` |

**批量模板化策略**：设置页的 14 个界面结构高度统一（Header + SectionedList），可抽象出 `SettingsScaffold` + `SettingsItem` + `SettingsSection` 三个基础 Composable，大部分设置页只需填充数据模型。

#### Batch 4: 聊天二级界面 + 聊天功能组件 (4-5 天)

对应 Stitch 设计组 C。

| 界面/组件 | 原文件 | 新文件 |
|----------|--------|--------|
| C1 聊天主页 | `app/chat/[id].tsx` (28KB) | Phase 1 已完成 |
| C2 会话设置 | `app/chat/[id]/settings.tsx` (27KB) | `.../ui/chat/SessionSettingsScreen.kt` |
| C3 会话设置弹窗 | `src/features/chat/components/SessionSettingsSheet/` | `.../ui/chat/SessionSettingsSheet.kt` |
| C4 工作区弹窗 | `src/features/chat/components/WorkspaceSheet/` | `.../ui/chat/WorkspaceSheet.kt` |
| C5 SPA 设置 | `app/chat/super_assistant/settings.tsx` (28KB) | `.../ui/chat/SpaSettingsScreen.kt` |
| C6 SPA RAG 配置 | `app/chat/super_assistant/rag-config.tsx` | `.../ui/chat/SpaRagConfigScreen.kt` |
| C7 SPA 高级检索 | `app/chat/super_assistant/advanced-retrieval.tsx` | `.../ui/chat/SpaAdvancedRetrievalScreen.kt` |
| C8 Agent RAG 配置 | `app/chat/agent/edit/rag-config/[agentId].tsx` | `.../ui/chat/AgentRagConfigScreen.kt` |
| C9 Agent 高级检索 | `app/chat/agent/edit/advanced-retrieval/[agentId].tsx` | `.../ui/chat/AgentAdvancedRetrievalScreen.kt` |
| — 审批卡片 | `ApprovalCard.tsx` | `.../ui/chat/ApprovalCard.kt` |
| — 执行模式选择器 | `ExecutionModeSelector.tsx` | `.../ui/chat/ExecutionModeSelector.kt` |
| — RAG 全局指示器 | `RagOmniIndicator.tsx` | `.../ui/chat/RagOmniIndicator.kt` |
| — RAG 引用组件 | `RagReferences.tsx` + `RagDetailPanel.tsx` | `.../ui/chat/RagReferences.kt` + `RagDetailPanel.kt` |
| — 流式内容卡片 | `StreamCard.tsx` + `StreamingCardList.tsx` | `.../ui/chat/StreamCard.kt` + `StreamingCardList.kt` |
| — 摘要指示器 | `SummaryIndicator.tsx` | `.../ui/chat/SummaryIndicator.kt` |
| — 任务监控 | `TaskMonitor.tsx` + `TaskFinalResult.tsx` | `.../ui/chat/TaskMonitor.kt` + `TaskFinalResult.kt` |
| — 处理指示器 | `ProcessingIndicator.tsx` | `.../ui/chat/ProcessingIndicator.kt` |
| — 消息附件块 | `AttachmentBlock.tsx` | `.../ui/chat/message/AttachmentBlock.kt` |
| — 消息错误块 | `ErrorBlock.tsx` | `.../ui/chat/message/ErrorBlock.kt` |
| — 生成图片块 | `GeneratedImage.tsx` | `.../ui/chat/message/GeneratedImage.kt` |
| — 工具调用块 | `ToolCallBlock.tsx` | `.../ui/chat/message/ToolCallBlock.kt` |
| — 推理设置 | `InferenceSettings.tsx` + `InferencePresets.tsx` | `.../ui/chat/InferenceSettings.kt` |

#### Batch 5: 知识库 Tab (5-7 天)

对应 Stitch 设计组 D。复杂度最高，涉及自定义图表和编辑器。

| 界面/组件 | 原文件 | 新文件 |
|----------|--------|--------|
| D1 知识库首页 | `app/(tabs)/rag.tsx` (50KB，巨型页面) | `.../ui/rag/RagHomeScreen.kt` |
| D2 文件夹详情 | `app/rag/[folderId].tsx` (7KB) | `.../ui/rag/FolderDetailScreen.kt` |
| D3 文档编辑器 | `app/rag/editor.tsx` (10KB) | `.../ui/rag/DocumentEditorScreen.kt` |
| D4 知识图谱 | `app/knowledge-graph.tsx` (5KB) | `.../ui/rag/KnowledgeGraphScreen.kt` |
| — 图片预览弹窗 | `ImagePreviewModal.tsx` | `.../ui/rag/ImagePreviewModal.kt` |
| — KG 边编辑弹窗 | `KGEdgeEditModal.tsx` | `.../ui/rag/KGEdgeEditModal.kt` |
| — KG 节点编辑弹窗 | `KGNodeEditModal.tsx` | `.../ui/rag/KGNodeEditModal.kt` |
| — KG 提取指示器 | `KGExtractionIndicator.tsx` | `.../ui/rag/KGExtractionIndicator.kt` |
| — 记忆条目 | `MemoryItem.tsx` | `.../ui/rag/MemoryItem.kt` |
| — PDF 提取器 | `PdfExtractor.tsx` | `.../ui/rag/PdfExtractor.kt` |
| — 标签分配弹窗 | `TagAssignmentSheet.tsx` | `.../ui/rag/TagAssignmentSheet.kt` |
| — 标签胶囊 | `TagCapsule.tsx` | `.../ui/rag/TagCapsule.kt` |
| — 标签管理弹窗 | `TagManagerSheet.tsx` | `.../ui/rag/TagManagerSheet.kt` |

关键组件迁移：
- `KnowledgeGraphView` → 考虑 Compose Canvas 或 AndroidView 嵌入 vis-network
- `ArtifactLibrary` → Compose LazyGrid
- `FolderItem` / `CompactDocItem` / `RagDocItem` → 统一的 Compose Item 组件
- 文档编辑器 → 考虑 `AndroidView` 嵌入 CodeEditor 库或 WebView

#### Batch 6: 技能与执行系统 (3-4 天)

对应 Stitch 设计组 H。

| 界面/组件 | 原文件 | 新文件 |
|----------|--------|--------|
| — 核心记忆列表 | `src/components/skills/CoreMemoryList.tsx` | `.../ui/skills/CoreMemoryList.kt` |
| — 工具执行时间线 | `src/components/skills/ToolExecutionTimeline.tsx` | `.../ui/skills/ToolExecutionTimeline.kt` |
| — 技能设置面板 | `src/components/settings/SkillsSettingsPanel.tsx` | `.../ui/skills/SkillsSettingsPanel.kt` |

---

## 六、Phase 3 — 清理与优化 (预计 3-5 天)

### 3.1 RN UI 层清理

当所有界面迁移到 Compose 后，逐步移除不再需要的 RN UI 依赖：

| 移除项 | 影响范围 | 风险 |
|--------|---------|------|
| `src/web-renderer/` 整个目录 | WebView 渲染器 | 低 — 已被原生 Markdown 替代 |
| `src/components/ui/` 整个目录 | RN UI 组件库 | 中 — 确认所有界面已迁移 |
| `src/components/chat/` 整个目录 | 聊天 UI 组件 | 中 — 同上 |
| `src/components/rag/` 整个目录 | RAG UI 组件 | 中 — 同上 |
| `src/components/skills/` 整个目录 | 技能 UI 组件 | 中 — 同上 |
| `global.css` | NativeWind CSS 变量 | 低 — 已迁移到 Compose Theme |
| `tailwind.config.js` | NativeWind 配置 | 低 — 同上 |
| `nativewind` 依赖 | package.json | 中 — RN 端仍有非 UI 用途？ |
| `react-native-webview` | package.json | 中 — ECharts/Mermaid 仍使用嵌入式 WebView |
| `expo-blur` | package.json | 低 — 已被 RenderEffect 替代 |

> ⚠️ **注意**: ECharts 和 Mermaid 图表在原生方案中仍使用小型嵌入式 `AndroidView(::WebView)`，因此 `react-native-webview` 的 Kotlin 层等价物 (Android 原生 WebView) 会保留，但不是全页面 WebView 方案。

### 3.2 APK 体积优化

| 优化项 | 预期收益 |
|--------|---------|
| 移除 Hermes JS Runtime (如完全脱离 RN) | -15~25MB |
| R8/ProGuard 深度混淆 | -10~20% |
| 移除未使用的 RN Native Modules | -5~10MB |
| ABI Split (arm64-v8a only) | 当前已配置 |
| 资源压缩 (WebP → AVIF) | -1~3MB |

### 3.3 性能基准测试

| 指标 | RN 方案基准 | 原生方案目标 |
|------|-----------|-------------|
| 冷启动时间 | ~3-5s | < 2s |
| 主会话内存 | ~120MB | < 80MB |
| APK 体积 | ~45MB | < 35MB |
| 首 Token 延迟 | 200-500ms | < 80ms |
| 流解析吞吐 | JS 正则瓶颈 | 协程状态机无卡顿 |
| 滚动帧率 | 30-45fps | 60fps |

### 3.4 CMP 扩展准备 (可选)

当 Android 端稳定后，可低成本启动 CMP 跨端扩展：

| 步骤 | 工作量 | 说明 |
|------|--------|------|
| 添加 KMP 插件 + source sets | 1-2 天 | `commonMain`/`androidMain`/`iosMain` |
| 抽取 expect/actual (RenderEffect, AndroidView) | 2-3 天 | 平台差异代码约 10-15 个文件 |
| Navigation → Voyager (可选) | 2-3 天 | 如需要跨平台导航 |
| iOS 壳工程 + 编译验证 | 1-2 天 | Xcode 项目配置 |
| **总计** | **~1-2 周** | 比重新用 SwiftUI 开发节省 3-5 周 |

---

## 七、Kotlin 项目结构规划

```
compose-ui/                                   # ★ 项目根目录（独立模块）
├── build.gradle.kts                          # Android Library + Compose 配置
├── src/main/
│   ├── AndroidManifest.xml
│   ├── res/font/                             # Manrope / Inter / Space_Grotesk
│   └── java/com/promenar/nexara/
│       │
│       ├── App.kt                            # Application (复用 MainApplication)
│       ├── MainActivity.kt                   # Compose 入口 (ComponentActivity)
│       │
│       ├── navigation/
│       │   └── NavGraph.kt                   # Compose Navigation 路由图
│       │
│       ├── ui/
│       │   ├── theme/                        # 设计 Token
│       │   │   ├── Theme.kt                  # NexaraTheme (MD3 + Glassmorphism)
│       │   │   ├── Color.kt                  # 颜色系统 (Dark/Light)
│       │   │   ├── Type.kt                   # 字体系统 (Manrope/Inter/SpaceGrotesk)
│       │   │   ├── Shape.kt                  # 圆角系统
│       │   │   └── Glass.kt                  # 毛玻璃 Modifier 扩展
│       │   │
│       │   ├── component/                    # 通用 UI 组件
│       │   │   ├── GlassTopBar.kt
│       │   │   ├── GlassBottomSheet.kt
│       │   │   ├── GlassAlertDialog.kt
│       │   │   ├── ConfirmDialog.kt
│       │   │   ├── ContextMenu.kt
│       │   │   ├── ToastHost.kt
│       │   │   ├── ImageViewerModal.kt
│       │   │   ├── SelectTextModal.kt
│       │   │   ├── FloatingCodeEditorModal.kt
│       │   │   ├── FloatingTextEditorModal.kt
│       │   │   ├── AnimatedSearchBar.kt
│       │   │   ├── SettingsScaffold.kt
│       │   │   ├── SettingsItem.kt
│       │   │   ├── SettingsSwitchItem.kt
│       │   │   ├── TokenStatsModal.kt
│       │   │   ├── ArtifactRenderer.kt
│       │   │   └── ModelPicker.kt
│       │   │
│       │   ├── chat/                         # 聊天界面
│       │   │   ├── ChatScreen.kt             # 主会话界面
│       │   │   ├── MessageList.kt            # 消息列表
│       │   │   ├── ChatInputBar.kt           # 输入栏
│       │   │   ├── ChatInputTopBar.kt        # 输入栏顶部工具条
│       │   │   ├── ThinkingLevelButton.kt    # 思考级别选择
│       │   │   ├── ChatTopBar.kt             # 顶栏
│       │   │   ├── AgentListScreen.kt        # 助手列表
│       │   │   ├── SessionListScreen.kt      # 会话列表
│       │   │   ├── AgentEditorScreen.kt      # Agent 编辑器
│       │   │   ├── SessionSettingsScreen.kt  # 会话设置
│       │   │   ├── SessionSettingsSheet.kt   # 会话设置弹窗
│       │   │   ├── WorkspaceSheet.kt         # 工作区弹窗
│       │   │   ├── SpaSettingsScreen.kt      # 超级助手设置
│       │   │   ├── SpaRagConfigScreen.kt     # SPA RAG 配置
│       │   │   ├── SpaAdvancedRetrievalScreen.kt
│       │   │   ├── AgentRagConfigScreen.kt   # Agent RAG 配置
│       │   │   ├── AgentAdvancedRetrievalScreen.kt
│       │   │   ├── ApprovalCard.kt           # 审批卡片
│       │   │   ├── ExecutionModeSelector.kt  # 执行模式选择器
│       │   │   ├── RagOmniIndicator.kt       # RAG 全局指示器
│       │   │   ├── RagReferences.kt          # RAG 引用组件
│       │   │   ├── RagDetailPanel.kt         # RAG 详情面板
│       │   │   ├── StreamCard.kt             # 流式内容卡片
│       │   │   ├── StreamingCardList.kt      # 流式内容列表
│       │   │   ├── SummaryIndicator.kt       # 摘要指示器
│       │   │   ├── TaskMonitor.kt            # 任务监控
│       │   │   ├── TaskFinalResult.kt        # 任务最终结果
│       │   │   ├── ProcessingIndicator.kt    # 处理指示器
│       │   │   ├── InferenceSettings.kt      # 推理设置
│       │   │   ├── ContextManagementPanel.kt # 上下文管理
│       │   │   ├── message/                  # 消息子组件
│       │   │   │   ├── UserBubble.kt
│       │   │   │   ├── AssistantBubble.kt
│       │   │   │   ├── AttachmentBlock.kt
│       │   │   │   ├── ErrorBlock.kt
│       │   │   │   ├── GeneratedImage.kt
│       │   │   │   └── ToolCallBlock.kt
│       │   │   └── input/
│       │   │       ├── ChatInputTopBar.kt
│       │   │       └── ThinkingLevelButton.kt
│       │   │
│       │   ├── rag/                          # 知识库界面
│       │   │   ├── RagHomeScreen.kt
│       │   │   ├── FolderDetailScreen.kt
│       │   │   ├── DocumentEditorScreen.kt
│       │   │   ├── KnowledgeGraphScreen.kt
│       │   │   ├── ImagePreviewModal.kt
│       │   │   ├── KGEdgeEditModal.kt
│       │   │   ├── KGNodeEditModal.kt
│       │   │   ├── KGExtractionIndicator.kt
│       │   │   ├── MemoryItem.kt
│       │   │   ├── PdfExtractor.kt
│       │   │   ├── TagAssignmentSheet.kt
│       │   │   ├── TagCapsule.kt
│       │   │   └── TagManagerSheet.kt
│       │   │
│       │   ├── settings/                     # 设置界面
│       │   │   ├── SettingsHomeScreen.kt
│       │   │   ├── ProviderFormScreen.kt
│       │   │   ├── ProviderModelsScreen.kt
│       │   │   ├── SearchConfigScreen.kt
│       │   │   ├── WorkbenchScreen.kt
│       │   │   ├── TokenUsageScreen.kt
│       │   │   ├── LocalModelsScreen.kt
│       │   │   ├── SkillsScreen.kt
│       │   │   ├── ThemeScreen.kt
│       │   │   ├── RagConfigScreen.kt
│       │   │   ├── AdvancedRetrievalScreen.kt
│       │   │   ├── RagAdvancedScreen.kt
│       │   │   ├── RagDebugScreen.kt
│       │   │   ├── BackupScreen.kt
│       │   │   ├── GlobalRagConfigPanel.kt
│       │   │   ├── AdvancedRetrievalPanel.kt
│       │   │   ├── AgentRagConfigPanel.kt
│       │   │   ├── AgentAdvancedRetrievalPanel.kt
│       │   │   ├── RagDebugPanel.kt
│       │   │   └── ProviderList.kt
│       │   │
│       │   ├── skills/                       # 技能系统
│       │   │   ├── CoreMemoryList.kt
│       │   │   ├── ToolExecutionTimeline.kt
│       │   │   └── SkillsSettingsPanel.kt
│       │   │
│       │   ├── welcome/                      # 欢迎页
│       │   │   └── WelcomeScreen.kt
│       │   │
│       │   └── renderer/                     # Markdown 渲染引擎
│       │       ├── MarkdownRenderer.kt
│       │       ├── IncrementalParser.kt
│       │       ├── CodeBlock.kt
│       │       ├── ThinkingBlock.kt
│       │       ├── EChartsView.kt            # 嵌入式 WebView
│       │       ├── MermaidView.kt            # 嵌入式 WebView
│       │       ├── StreamingCursor.kt
│       │       ├── ArtifactParser.kt
│       │       └── ArtifactTemplateRegistry.kt
│       │
│       ├── data/                             # 数据层
│       │   ├── remote/
│       │   │   ├── KtorSseClient.kt          # Ktor SSE 连接管理
│       │   │   ├── StreamParser.kt           # ★ 协程流解析器
│       │   │   ├── ThinkingDetector.kt       # ★ 协程思考标签检测
│       │   │   ├── StreamBufferManager.kt    # ★ 协程缓冲管理
│       │   │   └── ApiClient.kt              # REST API
│       │   ├── local/
│       │   │   └── DataStoreManager.kt       # DataStore (替代 MMKV)
│       │   ├── model/
│       │   │   ├── Message.kt
│       │   │   ├── Agent.kt
│       │   │   ├── Session.kt
│       │   │   ├── Provider.kt
│       │   │   └── RagConfig.kt
│       │   └── repository/
│       │       ├── ChatRepository.kt
│       │       ├── AgentRepository.kt
│       │       ├── SettingsRepository.kt
│       │       └── RagRepository.kt
│       │
│       └── bridge/                           # RN ↔ Native 桥接
│           ├── NavigationModule.kt           # RN 跳转到原生 Activity
│           └── DataSyncBridge.kt             # 数据同步桥接
```

---

## 八、RN ↔ Native 通信协议

### 8.1 跳转协议

```kotlin
// RN 侧发起跳转
NavigationModule.openNativeChat(conversationId: String, agentId: String)

// Android 侧接收
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val conversationId = intent.getStringExtra("conversationId")
        val agentId = intent.getStringExtra("agentId")
        // ...
    }
}
```

### 8.2 数据共享方案

| 数据类型 | 方案 | 说明 |
|---------|------|------|
| 会话 ID、Agent ID | Intent extras | 简单参数传递 |
| 用户认证状态 | DataStore 共享 | RN 和 Native 共用同一实例 |
| 设置项 (主题/模型) | DataStore 共享 | 双端实时同步 |
| 流式消息数据 | Kotlin SSE 直连 | 不经过 RN |
| 聊天历史 | 复用 RN 的 SQLite (通过 DataStore 标记最后读取位置) 或独立读取 |

### 8.3 长期演进路径

```
当前状态 (Phase 1-2):
  RN 业务逻辑 + Compose UI → 通过 DataStore/Intent 通信

中期状态 (Phase 3 清理后):
  RN 业务逻辑 + Compose UI → 逐步将核心 Store 逻辑迁移到 Kotlin ViewModel

远期目标 A (CMP 跨端):
  KMP 共享模块 → Android + iOS + 桌面 → 完全移除 RN 运行时
  (Multiplatform-Ready 选库使此路径成本极低，约 1-2 周)

远期目标 B (纯 Android):
  完全移除 RN 运行时 → 纯 Kotlin/Compose 应用
```

---

## 九、风险与缓解

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| Compose 毛玻璃性能低于预期 | 低 | 高 | POC 阶段专项验证；备选方案：半透明背景 + 静态模糊图片 |
| SSE 流解析器移植遗漏边界情况 | 中 | 中 | 参考 `stream-parser.ts` (17KB) + `thinking-detector.ts` + `stream-buffer.ts` 的完整逻辑，逐一覆盖；Kotlin 状态机需覆盖所有 JS 正则边界 |
| 图表组件 (ECharts/Mermaid) 嵌入体验不佳 | 低 | 低 | 保留小型 WebView 方案，与 RN 侧效果一致 |
| 设置页面迁移工作量大 | 中 | 低 | 模板化策略 + 批量生产 |
| RN Store 数据同步不一致 | 中 | 中 | DataStore 双向监听 + 事件总线 |
| APK 体积反增 | 低 | 低 | ABI Split + R8 + 资源压缩 |
| Ktor SSE 与 JS SSE 行为差异 | 低 | 中 | 参考 OkHttp SSE 已验证的协议逻辑，在 Ktor 层等价实现 |

---

## 十、里程碑与时间线

| 里程碑 | 预计时间 | 交付物 | 决策门 |
|--------|---------|--------|--------|
| **M0: 环境就绪** | 第 1 周 | `compose-ui/` 目录 + Compose 编译通过 | 继续 |
| **M1: POC 完成** | 第 2-3 周 | 主会话界面 + SSE 流解析管线 | ★ 关键决策点 |
| **M2: 全局组件就绪** | 第 4 周 | 导航 + 通用组件库 (G1~G12) | 继续 |
| **M3: 聊天 Tab 完成** | 第 5 周 | 助手列表 + Agent 管理 (B1~B3) | 继续 |
| **M4: 设置 Tab 完成** | 第 6-7 周 | 全部 14 个设置界面 + 共享面板 | 继续 |
| **M5: 聊天二级完成** | 第 7-8 周 | 聊天二级界面 + 全部功能组件 | 继续 |
| **M6: 知识库完成** | 第 8-9 周 | 全部知识库界面 + RAG 交互组件 | 继续 |
| **M7: 技能系统完成** | 第 9 周 | 技能 UI + 执行时间线 | 继续 |
| **M8: 全量 UI 切换** | 第 10 周 | 默认使用原生 UI | 继续 |
| **M9: 清理优化** | 第 11 周 | APK 体积优化 + 性能调优 | — |

> 以上时间线基于**全职投入**估算。如果兼职推进，每阶段 × 2-3 倍。

---

## 十一、Stitch 设计稿复用策略

已有的 Stitch 设计文档和 `stitch-ui-functional-reference.md` 在原生迁移中配合使用：

| 文档 | 用途 |
|------|------|
| `stitch-full-app-visual-redesign-spec.md` | 设计 Token、毛玻璃规范、动画体系、各界面视觉要求、输出规范 |
| `stitch-ui-functional-reference.md` | 各界面功能需求、UI 元素清单、交互规格、数据依赖、状态变化 |

**Stitch 设计 → Compose 代码映射**：

| Stitch (HTML/Tailwind) | Compose 实现 |
|---|---|
| `bg-[#131315]` | `Modifier.background(Color(0xFF131315))` |
| `backdrop-blur-xl` | `Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP) }` |
| `border border-white/10` | `Modifier.border(0.5.dp, Color.White.copy(alpha = 0.1f))` |
| `rounded-2xl` | `Modifier.clip(RoundedCornerShape(16.dp))` |
| `font-['Manrope']` | `FontFamily(Font(R.font.manrope))` |
| `active:scale-[0.96]` | `animateFloatAsState(if (pressed) 0.96f else 1f) + Modifier.graphicsLayer { scaleX = ...; scaleY = ... }` |

**不再需要的部分**：
- "Stitch 输出规范 (HTML + Tailwind)" → 改为 Compose 实现规范
- "RN 侧样式方案" → 改为 Compose 样式方案
- "从 Stitch HTML 到 RN 代码的映射" → 改为 Stitch → Compose 映射

---

*文档结束*

> **下一步行动**: 将 `stitch-ui-functional-reference.md` 提交给 Stitch，按 G → A → B → E → C → D → H 顺序逐批设计所有界面。设计完成后对照实现 Compose UI。
