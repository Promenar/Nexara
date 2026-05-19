package com.promenar.nexara.ui.common

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraShapes

/**
 * Nexara 奢华毛玻璃卡片（Stitch Spec API 31+ 黄金版）
 * 
 * 专为 Android 12+ (API 31) 重新定制：
 * 1. 背景层采用 GPU 硬件加速的 `RenderEffect.createBlurEffect` 渲染，抓取底层像素进行 35px 高斯模糊，
 *    呈现极致通透、充满灵动感的磨砂玻璃质感。
 * 2. 交互与内容分离，前景文字和按钮 100% 锐利，杜绝因整体模糊导致的视觉疲劳。
 */
@Composable
fun NexaraGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = NexaraShapes.large as RoundedCornerShape,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    // 裁剪边界并处理点击事件
    val baseModifier = modifier.clip(shape)
    val clickModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }

    Box(
        modifier = clickModifier
    ) {
        // 【第一层】GPU 实时高斯模糊背景层
        // 该层不承载任何子内容，专门用于捕获并模糊卡片底部的像素，生成拟真毛玻璃磨砂效果
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    // API 31+ 系统级硬件高斯模糊，使用双向 35px 像素模糊
                    renderEffect = RenderEffect.createBlurEffect(
                        35f, 35f, Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
                .background(NexaraColors.GlassSurface)
                .border(0.5.dp, NexaraColors.GlassBorder, shape)
        )

        // 【第二层】前景内容图层
        // 完全不应用模糊，确保文字、图标、按钮保持 100% 清晰和极高辨识度
        Box(
            modifier = Modifier,
            content = content
        )
    }
}
