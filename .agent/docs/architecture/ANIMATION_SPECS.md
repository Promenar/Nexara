# 移动端APP过渡动画实现规范

本文档整理了本项目移动端APP中“五大主页切换”及“文件夹视图层级切换”的动画实现方式及参数。该实现逻辑统一、流畅，具备高度可复用性。

## 1. 技术核心

*   **库**: `react-native-reanimated` (v3+)
*   **API**: Layout Animations (`Entering` / `Exiting`)
*   **核心机制**: **Key-Based Transition**。通过改变包裹容器的 `key` 属性，强制 React 销毁旧组件并挂载新组件，从而触发 Reanimated 的 `entering` (进场) 和 `exiting` (出场) 动画。

## 2. 动画视觉效果

*   **效果**: 类似原生 iOS/Android 的左右推入/推出效果（Push/Pop），但在 Tab 切换时模拟了水平滑动。
*   **方向**:
    *   **前进 (Forward)**: 新页面从右侧淡入 (FadeInRight)，旧页面向左侧淡出 (FadeOutLeft)。
    *   **后退 (Backward)**: 新页面从左侧淡入 (FadeInLeft)，旧页面向右侧淡出 (FadeOutRight)。
*   **参数**:
    *   **Duration**: `300ms`
    *   **Easing**: 默认 (通常为 Quad 或 Cubic，Reanimated 默认值)

## 3. 实现细节

### 3.1 状态管理与方向判断

动画的方向 (`navDirection`) 是根据状态变化动态计算的。

#### Tab 切换方向逻辑
```typescript
// 定义 Tab 顺序
const tabOrder: Tab[] = ['home', 'library', 'folders', 'favorites', 'settings'];

useEffect(() => {
  const currentIndex = tabOrder.indexOf(activeTab);
  const prevIndex = tabOrder.indexOf(prevTab);
  if (activeTab !== prevTab) {
    // 如果新 Tab 索引大于旧 Tab，则为前进（右滑），否则为后退（左滑）
    setNavDirection(currentIndex > prevIndex ? 'forward' : 'backward');
    setPrevTab(activeTab);
  }
}, [activeTab]);
```

#### 文件夹/历史记录层级切换逻辑
```typescript
const [history, setHistory] = useState<string[]>([]); // 记录浏览路径

useEffect(() => {
  if (history.length !== prevHistoryLen) {
    // 历史记录变长说明是深入下一级（前进），变短说明是返回（后退）
    setNavDirection(history.length > prevHistoryLen ? 'forward' : 'backward');
    setPrevHistoryLen(history.length);
  }
}, [history.length]);
```

### 3.2 动画组件定义

根据计算出的 `navDirection` 选择对应的 Reanimated 预设动画。

```typescript
import { FadeInRight, FadeInLeft, FadeOutLeft, FadeOutRight } from 'react-native-reanimated';

// ... 在 render 或组件内部
const enteringAnimation = navDirection === 'forward' ? FadeInRight.duration(300) : FadeInLeft.duration(300);
const exitingAnimation = navDirection === 'forward' ? FadeOutLeft.duration(300) : FadeOutRight.duration(300);
```

### 3.3 视图渲染与Key绑定

#### 主页 Tab 容器
最外层容器使用 activeTab 作为 Key。

```tsx
<Animated.View
  key={activeTab} // 关键：当 activeTab 变化时触发动画
  entering={enteringAnimation}
  exiting={exitingAnimation}
  className="flex-1"
>
  {activeTab === 'home' && <HomeView />}
  {activeTab === 'library' && <LibraryView />}
  {/* ... 其他 Tabs */}
</Animated.View>
```

#### 文件夹层级容器 (嵌套在 Folders Tab 内)
在 Folders Tab 内部，再次使用相同的模式，但 Key 绑定为 `currentPath`。

```tsx
{activeTab === 'folders' && (
  <View className="flex-1">
    <Header ... />

    {/* 文件夹内容的动画容器 */}
    <Animated.View
      key={currentPath || 'root'} // 关键：路径变化触发层级动画
      entering={enteringAnimation} // 复用相同的动画逻辑
      exiting={exitingAnimation}
      style={{ flex: 1 }}
    >
       {/* 列表内容 (Grid 或 Masonry) */}
       <FlashList ... />
    </Animated.View>
  </View>
)}
```

## 4. 复用指南 (Copy & Paste)

要在其他项目中复用此效果：

1.  **安装依赖**: 确保项目中已正确配置 `react-native-reanimated`。
2.  **State 准备**:
    *   `activeTab` (当前的页面ID)
    *   `navDirection` ('forward' | 'backward')
    *   `history` (如果是层级导航)
3.  **Hook 逻辑**: 复制 3.1 中的 `useEffect` 逻辑来自动维护 `navDirection`。
4.  **渲染层**: 使用 `Animated.View` 包裹你的内容，并确保 `key` 属性与你的路由状态（Tab ID 或 路径 ID）绑定。

## 5. 优势

这种方案避免了引入复杂的 Navigation 库（如 React Navigation）的转场配置，完全由数据驱动，非常适合自定义程度高、路由逻辑简单的单页应用或轻量级 App。它特别适合处理混合了“平级切换”（Tabs）和“层级切换”（Folders）的场景，给予用户一致的视觉体验。
