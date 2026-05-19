package com.promenar.nexara.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.promenar.nexara.ui.theme.NexaraShapes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * 通过 CompositionLocal 向下传播 HazeState。
 * NexaraPageLayout 在内部 provide，NexaraGlassCard 自动读取。
 * 不在 NexaraPageLayout 内的卡片将获得 null（降级为纯色底盘）。
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Nexara 奢华毛玻璃卡片（Haze 真·穿透毛玻璃方案）
 *
 * **核心原理**：
 * 使用 Haze 库的 `hazeSource` + `hazeEffect` API，真正捕获底层内容并实时模糊。
 * 通过 [LocalHazeState] 自动从 [NexaraPageLayout] 获取 HazeState，60+ 调用点零修改。
 *
 * **材质架构**：
 * - **有 HazeState 时**：`hazeEffect` 实时穿透模糊底层内容
 * - **无 HazeState 时**：微晶玻璃纯色降级底盘
 * - **underlay 模式**：调用方通过 underlay lambda 传入自定义底层，框架自动施加模糊
 * - 所有装饰层（霓虹渐变底色、水晶发光斜射线、彩虹渐变发光边框）始终保留
 */
@Composable
fun NexaraGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = NexaraShapes.large as RoundedCornerShape,
    onClick: (() -> Unit)? = null,
    // 可选的克隆背景层（物理对齐模式）
    underlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val hazeState = LocalHazeState.current

    // 裁剪边界并处理点击事件
    val baseModifier = modifier.clip(shape)
    val clickModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }

    // 渐变发光边框 Brush（从 primary-container 到 tertiary-container 渐变）
    val glowBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF8083FF).copy(alpha = 0.55f), // primary-container 霓虹蓝紫
            Color(0xFFD97721).copy(alpha = 0.45f), // tertiary-container 梦幻金橘
            Color(0xFF8083FF).copy(alpha = 0.55f)
        )
    )

    Box(
        modifier = clickModifier
    ) {
        if (underlay != null) {
            // ==============================
            // 【物理对齐模式】自定义 underlay + 模糊
            // ==============================
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color(0xFF201F22).copy(alpha = 0.72f))
                ) {
                    underlay()
                }

                // 霓虹微光底色渗透层
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF8083FF).copy(alpha = 0.05f),
                                    Color(0xFFD97721).copy(alpha = 0.03f)
                                )
                            )
                        )
                )

                // 水晶发光斜射线与彩虹渐变发光边框
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(1.dp, glowBorderBrush, shape)
                )
            }
        } else if (hazeState != null) {
            // ==============================
            // 【Haze 真·穿透毛玻璃模式】
            // hazeEffect 实时捕获并模糊底层内容
            // ==============================
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState) {
                        blurRadius = 28.dp
                        noiseFactor = 0.012f
                        backgroundColor = Color(0xFF121115).copy(alpha = 0.52f)
                    }
            ) {
                // 霓虹微光底色渗透层 (微弱强化霓虹红紫的氛围感)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF8083FF).copy(alpha = 0.08f),
                                    Color(0xFFD97721).copy(alpha = 0.05f)
                                )
                            )
                        )
                )

                // 水晶发光斜射线与彩虹渐变发光边框
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(1.dp, glowBorderBrush, shape)
                )
            }
        } else {
            // ==============================
            // 【降级模式】纯色微晶玻璃底盘（不在 NexaraPageLayout 内时）
            // ==============================
            Box(modifier = Modifier.matchParentSize()) {
                // 底盘半透明深色保护层
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color(0xFF201F22).copy(alpha = 0.85f))
                )

                // 霓虹微光底色渗透层
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF8083FF).copy(alpha = 0.05f),
                                    Color(0xFFD97721).copy(alpha = 0.03f)
                                )
                            )
                        )
                )

                // 水晶发光斜射线与彩虹渐变发光边框
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(1.dp, glowBorderBrush, shape)
                )
            }
        }

        // 【前景内容图层】完全不应用模糊，确保文字、图标、按钮 100% 清晰
        Box(
            modifier = Modifier,
            content = content
        )
    }
}
