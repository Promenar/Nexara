package com.promenar.nexara.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Nexara 双相视觉主题模式
 */
enum class VisualStyle {
    HAZE_GLASSMORPHISM,  // 高奢极光物理毛玻璃视觉系统
    NATIVE_MATERIAL_3    // 经典 100% 纯正原生 Material 3 扁平视觉
}

/**
 * 全局视觉模式 Local 分发器
 */
val LocalVisualStyle = compositionLocalOf { VisualStyle.HAZE_GLASSMORPHISM }
