# OpenCode UI 组件修复指令模板 (v2 — Stitch MD3 版)

> **版本**: v2 (2026-05-04)
> **核心变更**: 视觉风格完全以 `.stitch/` 目录的 MD3 + Glassmorphism 设计系统为准，**绝不参考原 RN UI 样式**
> **Session A 已完成**: ViewModel 层 + 双状态系统消除（不涉及 UI 组件）

---

## 设计系统规范（必读）

### 设计资源位置

| 资源 | 路径 | 用途 |
|------|------|------|
| **全局主题规范** | `.stitch/design_system/global_theme_specs.md` | 颜色/字体/间距/圆角/阴影完整 Token |
| **全 APP 视觉重构文档** | `.stitch/design_system/stitch-full-app-visual-redesign-spec.md` | 所有界面的详细设计要求 |
| **功能需求参考** | `.stitch/design_system/stitch-ui-functional-reference.md` | 每个界面的交互/数据/状态需求 |
| **49 个 HTML 设计稿** | `.stitch/screens/*.html` | 可浏览器打开的像素级参考 |
| **设计稿索引** | `.stitch/screens_index.md` | HTML 文件名 → 功能映射表 |

### Kotlin 侧已有设计 Token

- `NexaraColors` — 所有颜色
- `NexaraTypography` — 所有字体排版
- `NexaraShapes` — 所有圆角
- `NexaraCustomShapes` — 特殊形状
- `NexaraGlassCard` — 标准玻璃卡片组件

### 核心设计原则

1. **Material Design 3 + Glassmorphism**
2. **0.5dp hairline borders**
3. **20dp+ backdrop blur (Android 12+)**
4. **暗色优先**: 主推暗色模式 #131315
5. **Manrope 标题 + Inter 正文 + Space Grotesk 代码**
6. **Indigo-500 (#6366f1) / #c0c1ff 暗色** 品牌强调色
7. **按压缩放**: press 时 scale(0.96-0.97) + spring
8. **不引入任何第三方 UI 库**

---

*三个 Session (B/C/D) 的完整指令模板请参见 artifact 文件。*
