# Android UI测试指南

## 预生成指示器UI验证
（略）

## 最终集成方案

### 问题确认
✅ 已确认：统一扩展中心组件未集成到Expo主布局
✅ 已确认：Android应用使用`app/_layout.tsx`作为入口

### 完整集成步骤

1. **编辑 `app/_layout.tsx`**
   - 在导入部分添加：`import { UnifiedExtensionCenter } from '../src/components/extension-framework/UnifiedExtensionCenter';`
   - 在第178行 `<Stack>...</Stack>` 后添加：
     ```tsx
     <UnifiedExtensionCenter style={{ position: 'absolute', bottom: 20, right: 20 }} />
     ```

2. **重新构建应用**
   ```bash
   npm run android -- --no-open
   npx expo run:android
   ```

3. **测试验证清单**
   - [ ] 右下角出现"扩展"按钮
   - [ ] 点击按钮弹出浮动面板（有淡入动画）
   - [ ] 面板包含4个功能类别
   - [ ] 发起对话显示预生成指示器
   - [ ] 动画流畅无卡顿

### 预期效果
- 面板位置：智能定位，避免遮挡输入区域
- 动画效果：平滑淡入/淡出 + 微动效
- 交互体验：触觉反馈 + 响应式设计

请按此方案操作，测试完成后我将根据您的反馈调整UI细节。