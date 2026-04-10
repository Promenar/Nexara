# 架构决策记录 (ADR) - 文档编辑器路由化重构

## 1. 背景 (Context)
在开发 RAG 文档编辑器 (`FullScreenDocumentEditor`) 时，最初采用了全屏 **Modal** (`transparent={true}`) 方案以实现“轻量级覆盖”的交互感。然而遇到以下核心问题：
1.  **Android Blur 穿透异常**：在 Android 平台上，当 Modal 设置为 `transparent={true}` 时，`expo-blur` 组件无法正确采样 Modal 自身的内容背景，而是直接穿透采样到了底层的 `RagScreen`，导致视觉上的混乱（透过毛玻璃看到重叠的文字）。
2.  **UI/UX 不一致**：项目中的“二级配置页”（如 `RagAdvancedSettings`）均采用标准的 Stack Navigation 路由推入模式，拥有统一的 Slide 动画和 Header 交互。编辑器作为同级功能，使用 Modal 导致了“异类”的交互体验。
3.  **状态管理复杂**：在 `rag.tsx` 中手动维护 `showEditor`、`editingDocId` 等状态，并在 JSX 中条件渲染组件，增加了父组件的逻辑负担。

## 2. 决策 (Decision)
**废弃 Modal 实现，将编辑器迁移为独立的标准路由页面 (`app/rag/editor.tsx`)。**

### 具体实施：
1.  **路由化**：创建 `app/rag/editor.tsx`，接收 `docId` 和 `title` 作为路由参数。
2.  **导航跳转**：在 `rag.tsx` 中，将所有“编辑”操作从 `setState` 改为 `router.push('/rag/editor', ...)`。
3.  **组件复用**：
    *   复用 `PageLayout` 作为页面根容器，确保 SafeArea 和背景色与全局一致。
    *   复用 `GlassHeader`，在非 Modal 模式下，Blur 效果能基于单纯的页面层级正常渲染。
    *   复用 `KeyboardAvoidingView` 处理输入交互。
4.  **清理债务**：删除 `FullScreenDocumentEditor.tsx` 组件文件，以及 `rag.tsx` 中相关的 State 和 Lazy Import 逻辑。

## 3. 后果 (Consequences)
### 正面影响 (Pros)
*   **✅ 修复视觉 Bug**：彻底解决了 Android 端透明 Modal 下的毛玻璃采样错误，现在 Header 背景模糊正常。
*   **✅ 体验统一**：编辑器进出动画与系统默认（或设置页）保持一致（iOS: Cover/Slide, Android: Slide/Check），不再有突兀的自定义动画。
*   **✅ 代码解耦**：`rag.tsx` 减重约 50 行，不再通过 Props 透传编辑逻辑，编辑器自身负责数据加载和保存。
*   **✅ 路由深度链接**：未来支持通过 URL Scheme 直接打开特定文档编辑页（如 `nexara://rag/editor?docId=123`）。

### 负面影响 (Cons)
*   **交互变重**：相比于“轻量弹窗”，页面跳转在心理上感觉更“重”一些（但在移动端 Full Screen Modal 和 Push Screen 差异并不大）。
*   **状态重置**：每次进入编辑器都是新挂载组件，虽然我们在 `useEffect` 中加载数据，但相比 Keep-Alive 的 Modal，如果有未保存数据意外退出可能需要额外处理（目前已依靠返回拦截处理）。

## 4. 验证 (Verification)
*   **Android 测试**：通过真机测试，确认 Header 区域模糊正常，无穿透。
*   **Type Check**：通过 `npx tsc --noEmit`，无类型错误。
*   **功能测试**：打开/保存文档流畅，Toast 提示正常。
