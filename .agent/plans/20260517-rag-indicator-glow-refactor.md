# 💡 RAG 纯色发光进度轨（Neon Glow）与历史会话状态隔离实施方案

本方案旨在针对用户对 RAG 霓虹指示器的视觉细节与历史会话重启生命周期 Bug 提出两项极致优化：
1. **纯色高发光霓虹管质感（Neon Glow Effect）**：移除原先的 Brush 渐变（颜色不聚焦），为各阶段采用经典纯霓虹色，并通过 Canvas “底层半透明宽轨（光晕层）+ 顶层高亮窄轨（核心实体层）+ 呼吸微动画” 的三层叠加绘制公式，在暗黑卡片上完美呈现出高保真荧光管质感。
2. **根治历史会话重启不加载与消息传染 Bug（State Flow Isolation）**：
   - 彻底阻断全局 `ragPhases` 状态流对历史消息块的“传染”：仅在当前正在生成的消息组传入 `ragPhases`；历史组传入 `emptyList()` 并将 `isComplete` 置为 `true`。
   - 保证重启 App 历史消息立即渲染：在 `RagProgressCard` 内部，若 `phases` 为空但 `isComplete` 为真，自动退回并加载高保真的 8 步默认已完成状态 `RAG_DEFAULT_PHASES` 进行光轨 and 文本渲染。

---

## 🎨 霓虹光晕渲染设计（Neon Glow Canvas Formula）

在 `NeonMicroRail` 组件中，不再使用水平渐变（导致多色混杂不聚焦），将每一段轨道精细化为如下单端高亮纯色：
- **`DONE`（通过态）**：霓虹翠绿（`Color(0xFF00FF66)`）。
- **`ACTIVE`（进行态）**：高亮霓虹紫（`Color(0xFFB026FF)`），带有电荷传输跑马灯动画与呼吸光晕。
- **`PENDING`（待命态）**：半透明暗灰色（`Color(0xFF3F3F46).copy(alpha = 0.4f)`），无光晕。

### Canvas 三层荧光管绘制公式：
我们将容器高度从 `3.dp` 略微扩展至 `8.dp`（给上下溢出的光晕留足物理空腔，不改变视觉重心），通过 `Canvas` 在各阶段对应的宽度内分层绘制：
1. **光晕层（Glow Layer）**：
   - 绘制高度为 `6.dp` 的圆角矩形，设定其 `alpha` 为 `0.28f`。这可以在圆角边缘渲染出一层毛绒绒的柔光圈，满足用户对“光晕饱满”的要求。
   - 对于 `ACTIVE`（进行中）阶段，利用 `infiniteTransition` 获取 `0.7f` 到 `1.0f` 的正弦波动系数，使光晕产生极具未来感的“能量呼吸”波动。
2. **高亮核心层（Core Solid Layer）**：
   - 在光晕层中央绘制高度为 `3.dp` 的正常圆角矩形，设定其 `alpha` 为 `0.95f` 的高饱和纯色（绿/紫）。
3. **高光中心骨架（Inner Highlight）**（仅针对 `ACTIVE` 状态）：
   - 在核心层正中绘制高度为 `1.dp` 的极细极亮高光细线（如接近白色的浅粉紫 `Color(0xFFFDF4FF)`），模拟物理荧光灯管内部的电离核心，实现史诗级的科技美感！

---

## ⚙️ 双层气泡数据流隔离与生命周期根治架构（State Flow Isolation）

### 1. 新旧气泡 phases 数据源隔离
在 [ChatScreen.kt](file:///k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt#L321) 中，当遍历渲染消息流列表时：
- 判断当前消息气泡是否为正在生成的最末消息气泡（`isGeneratingGroup` 为 `true`）：
  - **是**：将实时变化的全局 `ragPhases` 传入 `RagProgressCard` 的 `phases`。
  - **否（历史气泡）**：传入 `emptyList()`，并将 `isComplete` 参数强行赋为 `true`。
- **成效**：瞬间阻断了 VM 中全局单例 `ragPhases` 在新消息检索时，对所有历史 RAG 气泡造成的不良“进度闪烁传染”，确保历史气泡拥有绝对静止的完成态状态。

### 2. 默认检索步骤退回（Default Phase Fallback）
在 [ChatInlineComponents.kt](file:///k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatInlineComponents.kt#L363) 内部，定义高保真 8 阶段默认模板 `RAG_DEFAULT_PHASES`：
```kotlin
private val RAG_DEFAULT_PHASES = listOf(
    RagPhase("query_intent", "分析查询意图", PhaseStatus.DONE, 100),
    RagPhase("vector_search", "向量库检索", PhaseStatus.DONE, 100),
    RagPhase("keyword_search", "关键词检索", PhaseStatus.DONE, 100),
    RagPhase("hybrid_merge", "混合检索融合", PhaseStatus.DONE, 100),
    RagPhase("kg_retrieval", "知识图谱关系检索", PhaseStatus.DONE, 100),
    RagPhase("rerank", "相关性重排过滤", PhaseStatus.DONE, 100),
    RagPhase("context_compress", "上下文提示词压缩", PhaseStatus.DONE, 100),
    RagPhase("prompt_build", "注入大模型上下文", PhaseStatus.DONE, 100)
)
```
在 `RagProgressCard` 开头进行适配：
- 定义实际展示列表 `val displayPhases = if (phases.isEmpty() && isComplete) RAG_DEFAULT_PHASES else phases`。
- **成效**：当重启 App 进入历史会话时，即使后台尚未初始化当前的 `ragPhases`（VM 里的 StateFlow 为空），只要此消息带有历史引用且处于完成态，UI 就会自动为其退回并渲染 8 个 Done 状态的极致纯绿高亮霓虹发光轨，彻底根治重启后指示器离奇失踪的缺陷！

---

## 📝 实施步骤

1. **[MODIFY]** [ChatInlineComponents.kt](file:///k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatInlineComponents.kt)：
   - 定义静态 `RAG_DEFAULT_PHASES`。
   - 重构 `RagProgressCard` 的入参 phases 到 `displayPhases` 转换逻辑。
   - 完全重写 `NeonMicroRail` Canvas 绘制，引入半透明三层 Glow Glow Glow 质感。
2. **[MODIFY]** [ChatScreen.kt](file:///k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/chat/ChatScreen.kt)：
   - 精细隔离 `phases` 传参：如果是 `isGeneratingGroup` 传入 `ragPhases`，否则传入 `emptyList()` 且 `isComplete = true`。
3. **[COMPILE]**：
   - 运行 `./gradlew compileDebugKotlin` 进行全量编译校验。
4. **[DOCUMENTATION]**：
   - 执行 DIA 文档影响结项审计，更新 `CHANGELOG.md` 与 `.agent/handover.md`，宣告完美关闭！
