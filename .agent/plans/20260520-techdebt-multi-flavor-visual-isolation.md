# 架构决策与技术债务归档：基于 Gradle Product Flavors 的多变体视觉隔离方案

> **文档状态**：已归档（技术债务 / 待实施）  
> **创建时间**：2026-05-20  
> **设计目标**：解决分支爆炸问题，彻底消除 `native-kotlin-refactor`（纯 MD3 扁平版）与 `haze-native-kotlin`（极光毛玻璃版）之间的业务代码同步心智负担。

---

## 1. 背景与现有痛点

当前 Nexara 原生 Kotlin 工程（`native-ui` 模块）正处于活跃的视觉探索与业务演进期。为了验证不同的视觉风格，项目拉取了多条并行分支：
- **`native-kotlin-refactor`**：纯扁平 MD3 微调版本。
- **`haze-native-kotlin`**：基于 `Haze` 库实现的极光霓虹毛玻璃风格版本。
- **`kotlin-haze&md3mixed`**：试图通过代码级整合两者的混合方案（会引入大量运行时配置判断和复杂的 UI 条件分支，大幅增加代码库复杂度，且已在上一阶段宣告搁置）。

### 核心痛点：
1. **多分支阻力**：后续核心业务（如本地推理调通、知识图谱 Canvas、`GenerationService` 后台服务）均在 B 分支（`native-kotlin-refactor`）进行，如何将业务迭代同步至 C 分支（毛玻璃版）面临严峻的 Git 冲突和心智开销。
2. **依赖污染与包体积**：若直接在单分支中使用 `if/else` 等运行时机制切换主题，扁平版分支将不得不被迫引入 `Haze` 库及其相关的底层依赖，且其 `minSdk` 无法降级（毛玻璃版因底层硬件加速要求而强制升级至 `minSdk = 31`，而扁平版在未来理论上本可支持更低的 API 版本）。
3. **运行时开销**：在运行时通过配置项进行大量复杂的视觉组件分支切换，容易导致界面在初次渲染时发生微小的布局抖动（Layout Jittering），且使核心组件的代码极难维护。

---

## 2. 终极架构设计：基于 Build Flavors 的变体隔离

本方案的核心思想是：**“业务归 main，视觉归 flavor；单分支开发，编译期注入”**。

通过利用 Android Gradle 插件强大的 **Product Flavors (产品渠道/变体)** 机制，配合 **SourceSets (源码集)** 物理路径隔离，在不拆分 Git 分支的前提下，完美实现“同一套业务逻辑，两套完全不同视觉呈现”的高内聚、低耦合架构。

### 2.1 整体架构图

```mermaid
graph TD
    subgraph 编译期变体构建 (Build Variant)
        A[flatDebug / flatRelease] -->|使用 flat 源码集| C[纯 MD3 扁平版 Nexara App]
        B[auroraDebug / auroraRelease] -->|使用 aurora 源码集| D[极光水晶毛玻璃版 Nexara App]
    end

    subgraph 物理代码目录划分 (native-ui/app/src/)
        E[main 源码集 <br>公共业务逻辑] -.->|引用通用视觉接口/签名| F[Flat 专属 UI 源码 <br>src/flat/kotlin/]
        E[main 源码集 <br>公共业务逻辑] -.->|引用通用视觉接口/签名| G[Aurora 专属 UI 源码 <br>src/aurora/kotlin/]
    end

    E -->|100% 共享| C
    E -->|100% 共享| D
    F -->|只注入 flat| C
    G -->|只注入 aurora| D
```

---

## 3. 具体实施方案

### 3.1 第一步：Gradle 变体与依赖隔离配置

修改 `native-ui/app/build.gradle.kts`，定义两个 Product Flavors，并针对不同渠道应用特定的物理配置和专属依赖。

```kotlin
android {
    ...
    // 1. 定义变体维度
    flavorDimensions.add("visual")

    productFlavors {
        // 纯 MD3 扁平版：对系统版本要求低，包体积最轻量
        create("flat") {
            dimension = "visual"
            applicationIdSuffix = ".flat"
            minSdk = 26 // 可支持到 Android 8.0
        }
        
        // 极光霓虹毛玻璃版：极致视觉震撼力，需要 Android 12+ 硬件级毛玻璃支持
        create("aurora") {
            dimension = "visual"
            applicationIdSuffix = ".aurora"
            minSdk = 31 // 强制 Android 12
        }
    }
}

dependencies {
    // 2. 通用业务依赖 (所有渠道共享)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.room.runtime)
    // ... 其他核心业务库

    // 3. 渠道专属视觉依赖 (Aurora 独享，Flat 零污染)
    "auroraImplementation"("dev.chrisbanes.haze:haze:0.7.0") 
    // 注：flat 编译时完全不打包 haze 库，从而完美优化了扁平版的包体积与性能
}
```

### 3.2 第二步：源码集目录设计与隔离

在物理文件层面上，废弃原有分支的管理方式，统一在主干分支的 `native-ui/app/src/` 下创建独立的视觉文件夹：

```text
native-ui/app/src/
├── main/                   <-- 【主干：通用业务】
│   ├── java/com/nexara/
│   │   ├── database/       <-- 本地数据库逻辑
│   │   ├── service/        <-- GenerationService 后台生成服务
│   │   ├── viewmodel/      <-- 主会话 ViewModel (处理消息发送、推理调用等核心业务)
│   │   └── ui/
│   │       └── ChatScreen.kt  <-- 页面骨架：只负责状态分发，不直接实现局部精细组件
│   └── res/
├── flat/                   <-- 【变体 A：纯 MD3 扁平版专属】
│   └── java/com/nexara/ui/components/
│       ├── ChatInputIsland.kt  <-- 【扁平输入岛】普通 MD3 风格底座
│       └── ChatHeader.kt       <-- 【扁平页头】标准 Flat 材质
└── aurora/                 <-- 【变体 B：极光水晶毛玻璃专属】
    └── java/com/nexara/ui/components/
        ├── ChatInputIsland.kt  <-- 【毛玻璃输入岛】内嵌 HazeState, 具备发光霓虹边框与磨砂半透质感
        └── ChatHeader.kt       <-- 【毛玻璃页头】通过 HazeChild 产生动态背景模糊
```

### 3.3 第三步：物理隔离组件的统一链接

为了让 `main` 源码集中的 `ChatScreen.kt` 能够在不需要知道当前是哪种视觉风格的情况下，正确加载对应的输入岛和 Header，我们有以下两种经典实现手法：

#### 方法 A：类/函数签名完全一致（强力推荐，零运行时负担）
- 在 `flat` 的 `ChatInputIsland.kt` 中声明：
  ```kotlin
  package com.nexara.ui.components
  
  @Composable
  fun ChatInputIsland(
      text: String,
      onTextChange: (String) -> Unit,
      modifier: Modifier = Modifier
  ) {
      // 纯 MD3 TextField 扁平实现
  }
  ```
- 在 `aurora` 的 `ChatInputIsland.kt` 中以**完全一致的包名和函数签名**声明：
  ```kotlin
  package com.nexara.ui.components
  
  @Composable
  fun ChatInputIsland(
      text: String,
      onTextChange: (String) -> Unit,
      modifier: Modifier = Modifier
  ) {
      // 基于 Haze 的毛玻璃+霓虹发光实现
  }
  ```
- **工作机制**：在 `main/ChatScreen.kt` 中只需直接 `import com.nexara.ui.components.ChatInputIsland`。
  - 当构建 `flatDebug` 变体时，编译器会将 `src/flat/` 中的代码与 `src/main/` 融合，编译出扁平版。
  - 当构建 `auroraDebug` 变体时，编译器会自动链接 `src/aurora/` 中的对应实现，编译出毛玻璃水晶版。
  - **没有任何 `if/else`，没有任何性能和包大小溢出，100% 编译期静态决定**。

#### 方法 B：统一视觉主题提供者接口 (VisualThemeProvider)
如果需要更高程度的动态性，可以在 `main` 中声明一个通用主题接口，并在两边提供其 `actual` 或特定的注入实现：
```kotlin
interface NexaraVisualTheme {
    @Composable
    fun ChatInput(text: String, onTextChange: (String) -> Unit, modifier: Modifier)
    
    @Composable
    fun PageHeader(title: String, modifier: Modifier)
}
```
然后在 `flat` 和 `aurora` 中分别创建该接口的单例或局部实例，由 Compose 的 `CompositionLocalProvider` 统一向全包分发。

---

## 4. 本架构的核心价值

1. **零分支同步心智负担**：
   - 所有的功能性调整（如增加大模型推理后端、本地数据库字段迁移、GenerationService 新增功能等）均只需在 `src/main/` 目录下编写一次。
   - 提交代码时只需提交这一个分支，两个变体就会立刻自动同步所有的业务逻辑更新，一劳永逸。
2. **极高的包体和兼容性安全性**：
   - 保证了 `flat`（纯 MD3）版本极度轻量，绝不打包 `Haze` 库相关的硬件依赖。
   - 完美释放了 API 支持的灵活性：`flat` 可以设置较低的 `minSdk = 26`（满足广泛机型）；而 `aurora` 设置 `minSdk = 31`，保证高端机型能享受极速无卡顿的毛玻璃硬件渲染。
3. **极佳的工程结构整洁度**：
   - 使得业务研发人员在写功能时只需全神贯注在 `src/main/` 的控制流中；
   - 视觉设计师与动效开发人员在调优风格时只需在专属的变体目录下专注界面雕琢，两者职责清晰、物理隔离。

---

## 5. 归档与债务激活时机

- **当前处理**：本方案以**技术债务**形式在 `.agent/plans/` 目录归档留存，当前不启动任何物理代码拆分，保持 `native-kotlin-refactor` 作为最纯粹的 MD3 基座用于后续业务特性的快速攻坚。
- **债务激活时机**：当核心业务功能开发稳定（如本地推理、KG Canvas、后台生成等均以 100% 的完成度稳定合入主基座）且后续确认需要长期并行发布“极简扁平版”与“水晶毛玻璃版”两个市场渠道包时，再激活本方案，通过一次性的代码物理路径重组，彻底闭环此架构债务。
