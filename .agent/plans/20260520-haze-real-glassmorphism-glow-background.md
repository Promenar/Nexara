# Nexara 奢华真·毛玻璃极光背景重构实施计划

解决由于背景纯色导致 Haze 穿透模糊失效（看起来像纯透明）的缺陷。通过将通用次级布局 `NexaraPageLayout.kt` 大背景升级为极光流光背景 `NexaraGlowBackground`，并完美绑定为 Haze 实时模糊物理采样源，彻底点亮全站十几处二级界面的极致磨砂毛玻璃视效！

## 1. 缺陷分析

在引入了 Haze 2.0 库用于实现真正的穿透高斯模糊后，目前面临的主要视觉问题是**毛玻璃卡片和 Header 看起来是“纯透明”的**。其深层物理原因如下：

1. **背景缺乏变化源**：`NexaraPageLayout.kt` 中大背景为 `Scaffold` 的 `containerColor = NexaraColors.CanvasBackground`（这是一个纯黑/深灰的单色背景）。
2. **高斯模糊物理规律**：高斯模糊的作用是将某一区域内的像素与其邻域的像素进行加权平均。如果这片区域是完全一致的纯色，由于邻域像素与中心像素值完全一样，高斯模糊前后的像素颜色将没有任何改变！
3. **视觉误判**：由于模糊前后没有颜色和纹理变化，在叠加了带微弱透明度的微晶玻璃偏光层（如 `Color(0xFF201F22).copy(alpha = 0.72f)`）后，最终呈现的视觉效果依然是平平无奇的单色半透明，完全失去了磨砂毛玻璃（Glassmorphism）的质感与高级感，看起来如同纯透明的普通色块。

---

## 2. 解决方案

要让 Haze 真正的物理模糊产生叹为观止的高级质感，必须在被模糊的组件（Header 以及卡片）下方放置**极富色彩渐变、充满细节的背景源**。

本方案的核心做法是将 `NexaraPageLayout.kt` 升级为全局极光流光背景：

1. **大背景极光化**：使用专为毛玻璃研制的极光流动背景 `NexaraGlowBackground` 包裹 `Scaffold`，取代单调的单色背景，并设 `Scaffold` 为全透明。
2. **完美绑定模糊源**：将 `hazeSource(state = hazeState)` 绑定到最外层 `NexaraGlowBackground` 容器上，使其能实时捕获极光气泡流光，以及在它之上的列表内容。
3. **极致动效穿透**：当用户滚动列表时，底部的发光气泡在后台平滑往复微动，卡片在前景滚动。磨砂卡片 and Header 将完美折射、模糊后方的流光背景与滚动穿过的下层文字，呈现极致奢华的视差和偏光毛玻璃，并且性能由于全局单一 Canvas 大幅优化。

---

## 3. 架构设计与流程推演

### 3.1 渲染层级对比

**重构前 (纯色暗背景，毛玻璃退化为纯透明)**：
```
+------------------------------------------+  <- 最顶层：TopAppBar / Cards (HazeEffect)
| [  Haze Effect  ] (模糊 35.dp, 无变化)   |
+------------------------------------------+  <- 中间层：Scroll Content (平铺，无重叠)
| [  Scroll List  ]                        |
+------------------------------------------+  <- 最底层：Scaffold containerColor (纯灰)
| [ NexaraColors.CanvasBackground (单色)  ] |
+------------------------------------------+
```

**重构后 (极光大背景，Haze 完美采样折射)**：
```
+------------------------------------------+  <- 最顶层：TopAppBar / Cards (HazeEffect)
| [  Haze Effect  ] (捕获下方极光/内容模糊)|
+------------------------------------------+  <- 中间层：Scaffold & Scroll Content (透明)
| [ Scroll Content ]                       |
+------------------------------------------+  <- 最底层：NexaraGlowBackground (极光流光)
| [  LinearGradient Bubble 1 + Bubble 2  ] |
+------------------------------------------+
```

### 3.2 流程推演
- `NexaraPageLayout` 在被加载时，在最外层渲染 `NexaraGlowBackground`。
- `NexaraGlowBackground` 应用了 `hazeSource(state = hazeState)`，它在绘制其极光 Canvas 后，将整个子层级注册为模糊内容源。
- 局部的 `NexaraGlassCard` 和 `topBar` 的 `hazeEffect` 自动捕获到它们下方的极光流光和文字，进行高斯模糊 35.dp，产生真正的晶莹剔透磨砂毛玻璃。
- 全站所有的二级设置表单（例如记忆设置、检索设置、工具设置等十几处页面）全部一键升级，在不更改任何具体业务代码的前提下，瞬间点亮全站奢华视觉。

---

## 4. 拟做出的修改

### 4.1 核心组件

#### [MODIFY] [NexaraPageLayout.kt](file:///k:/Nexara/native-ui/app/src/main/java/com/promenar/nexara/ui/common/NexaraPageLayout.kt)
- 引入最外层 `NexaraGlowBackground` 包装。
- 将 `hazeSource` 移入 `NexaraGlowBackground`。
- 将 `Scaffold` 的 `containerColor` 设为 `Color.Transparent`，使其透明悬浮。
- 确保传入的 `modifier` 传给最外层容器。

### 4.2 文档同步

#### [MODIFY] [CHANGELOG.md](file:///k:/Nexara/CHANGELOG.md)
- 记录此次 Haze 毛玻璃全局升级与极光背景重构。

#### [MODIFY] [.agent/handover.md](file:///k:/Nexara/.agent/handover.md)
- 更新会话状态和已完成内容。

---

## 5. 验证计划

### 5.1 编译验证
- 运行 Gradle 编译命令确保代码无语法错误：
  ```bash
  ./gradlew assembleDebug
  ```

### 5.2 视觉与交互验证
- 使用 Haze 库后，确认局部卡片和 Header 在有渐变发光的极光背景上，完全亮起奢华的高斯磨砂质感。
- 向上滑动列表时，文字和卡片在 Header 底下穿过时，被平滑模糊虚化，展现动态遮罩毛玻璃。
