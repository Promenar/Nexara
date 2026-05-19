package com.promenar.nexara.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.promenar.nexara.ui.theme.NexaraColors

/**
 * Nexara 奢华极光发光背景（物理对齐增强版）
 * 
 * 专为配合 NexaraGlassCard (磨砂毛玻璃卡片) 研发：
 * 1. 在底部绘制超大、低饱和度、极高透明度的渐变彩色光晕，为毛玻璃卡片提供充足的“虚化参照源”。
 * 2. 引入大周期无限线性往复微动画，让发光粒子在后台平缓流淌。
 * 3. 新增 [alignmentOffset] 参数，支持卡片内部的 Canvas 进行物理反向平移，实现极光粒子的完美像素级重合模糊。
 */
@Composable
fun NexaraGlowBackground(
    modifier: Modifier = Modifier,
    alignmentOffset: Offset = Offset.Zero, // 用于克隆物理对齐的反向偏移量
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow_transition")
    
    // 发光气泡 1 缓动偏移量
    val animOffset1 by infiniteTransition.animateValue(
        initialValue = Offset(0f, 0f),
        targetValue = Offset(60f, 40f),
        typeConverter = Offset.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_blob1"
    )
    
    // 发光气泡 2 缓动偏移量
    val animOffset2 by infiniteTransition.animateValue(
        initialValue = Offset(0f, 0f),
        targetValue = Offset(-70f, 50f),
        typeConverter = Offset.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_blob2"
    )

    // 获取绝对屏幕尺寸以保持内外气泡计算标准绝对一致
    val displayMetrics = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics
    val screenWidth = displayMetrics.widthPixels.toFloat()
    val screenHeight = displayMetrics.heightPixels.toFloat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NexaraColors.CanvasBackground)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 使用屏幕的物理分辨率计算圆心，确保多节点大小的一致性
            val width = screenWidth
            val height = screenHeight
            
            // 如果是卡片内部的克隆 Canvas，进行反向偏移量平移，使其在屏幕的绝对像素上与大背景重合！
            if (alignmentOffset != Offset.Zero) {
                drawContext.transform.translate(-alignmentOffset.x, -alignmentOffset.y)
            }
            
            // 粒子 1：偏左上方，柔和的 Primary 紫色霓虹微光 (大幅提高饱和度以折射毛玻璃流动)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NexaraColors.Primary.copy(alpha = 0.32f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.15f + animOffset1.x, height * 0.2f + animOffset1.y),
                    radius = width * 0.65f
                ),
                center = Offset(width * 0.15f + animOffset1.x, height * 0.2f + animOffset1.y),
                radius = width * 0.65f
            )

            // 粒子 2：偏右下方，柔和的 Secondary 霓虹青蓝微光 (大幅提高饱和度以折射毛玻璃流动)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NexaraColors.Secondary.copy(alpha = 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(width * 0.85f + animOffset2.x, height * 0.65f + animOffset2.y),
                    radius = width * 0.75f
                ),
                center = Offset(width * 0.85f + animOffset2.x, height * 0.65f + animOffset2.y),
                radius = width * 0.75f
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}
