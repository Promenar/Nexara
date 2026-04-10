# 原生桥接死锁防御指南

## 快速参考

### 黄金法则
**所有原生桥接调用（Haptics、SecureStore、FileSystem等）必须延迟10ms执行。**

### 标准模式

```tsx
// ✅ 推荐：直接内联
<TouchableOpacity onPress={() => {
    setTimeout(() => {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
        setState(value);
    }, 10);
}}>
```

---

## 高风险场景检查清单

### 状态变更场景
- [ ] `setState` / `zustand.set()` 附近无同步原生调用
- [ ] 语言切换使用延迟模式
- [ ] 主题切换使用延迟模式（除非仅切换 Switch）
- [ ] 导航操作（`router.push`）附近无同步原生调用

### 组件场景
- [ ] 嵌套 TouchableOpacity 使用延迟
- [ ] Modal 打开/关闭使用延迟
- [ ] 条件渲染切换附近无同步原生调用

### 导航器场景
- [ ] Tab 切换使用延迟
- [ ] 任何触发导航器 `key` 变化的操作使用延迟
- [ ] 返回操作附近无同步原生调用

---

## 异常信号速查表

| 用户反馈 | 技术原因 | 立即检查 |
|---------|---------|---------|
| "震动比其他地方强" | 线程阻塞 + 系统补偿 | Haptics 是否延迟 30ms+ |
| "点击后延迟才震动" | JS线程繁忙 | 是否有同步状态变更 |
| "切换页面白屏/黑屏" | 导航重挂载冲突 | 导航附近的原生调用 |
| "触感不一致" | 死锁前兆 | 对比其他交互的实现 |

---

## 排查流程

```
发现问题
  ↓
定位 onPress 回调
  ↓
检查 Haptics 是否在 setTimeout 中？
  NO → 立即修复
  YES → 检查延迟 >= 10ms?
  ↓
检查是否有状态变更？
  YES → 确认是否触发重挂载
  ↓
在低端设备重新测试
```

---

## 代码审查命令

```powershell
# 搜索所有 Haptics 调用
Select-String -Path "*.tsx","*.ts" -Pattern "Haptics\." -Recurse

# 搜索所有 onPress
Select-String -Path "*.tsx" -Pattern "onPress=" -Recurse

# 搜索状态变更
Select-String -Path "*.tsx","*.ts" -Pattern "setState|\.set\(" -Recurse
```

---

## 真实案例：语言切换器死锁

### 问题代码
```tsx
<TouchableOpacity onPress={() => setLanguage('zh')}>
```

### 错误链
```
setLanguage('zh')
  ↓ 立即触发
zustand 状态变更
  ↓
Tab 导航器检测到 key={language} 变化
  ↓
导航器开始重新挂载
  ↓ 同时
Haptics 被调用（如果有）
  ↓
线程竞争 → 死锁 → 崩溃
```

### 修复方案
```tsx
<TouchableOpacity onPress={() => {
    setTimeout(() => {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
        setLanguage('zh');
    }, 10);
}}>
```

### 用户观察到的异常
- 震动"延迟但劲更大"
- 点击感觉"不对劲"

---

**参考**: `.agent/PROJECT_RULES.md` 第8条
