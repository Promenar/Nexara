package com.promenar.nexara.ui.common

import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraTheme
import com.promenar.nexara.ui.theme.NexaraTypography
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Nexara 通用次级页面布局（双 HazeState 同级 Overlay 架构）
 *
 * 核心架构与 ChatScreen 完全对齐：
 * ```
 * Box {
 *     // Layer 0: 纯极光背景（cardHazeState 采样源，给卡片模糊用）
 *     NexaraGlowBackground(.hazeSource(cardHazeState)) { 空 }
 *
 *     // Layer 1: 内容区（headerHazeState 采样源，给 Header 模糊用）
 *     Box(.hazeSource(headerHazeState)) {
 *         Column(scroll) { content() }  // 卡片在此渲染，是 Layer 0 的兄弟
 *     }
 *
 *     // Layer 2: Header 悬浮 Overlay（headerHazeState hazeEffect）
 *     Box(.hazeEffect(headerHazeState)) { TopAppBar }
 * }
 * ```
 *
 * 卡片（NexaraGlassCard）通过 LocalHazeState 获取 cardHazeState，
 * 其 hazeEffect 完美模糊 Layer 0 的极光色彩（兄弟关系，非子节点）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexaraPageLayout(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollable: Boolean = true,
    imePadding: Boolean = true,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    // 双 HazeState 架构：彻底分离卡片与 Header 的采样源
    val cardHazeState = rememberHazeState()    // 卡片模糊用：采样纯极光背景
    val headerHazeState = rememberHazeState()  // Header 模糊用：采样内容区（极光+卡片）

    // 向子树派发 cardHazeState，NexaraGlassCard 通过 LocalHazeState.current 读取
    CompositionLocalProvider(LocalHazeState provides cardHazeState) {
        Box(modifier = modifier.fillMaxSize()) {

            // ══════════════════════════════════════════════════════════
            // Layer 0: 纯极光背景层（cardHazeState 的物理采样源）
            // NexaraGlassCard 的 hazeEffect 将捕获此层的绚烂极光色彩
            // ══════════════════════════════════════════════════════════
            NexaraGlowBackground(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = cardHazeState)
            ) {}

            // ══════════════════════════════════════════════════════════
            // Layer 1: 内容区（headerHazeState 的物理采样源）
            // 包含极光透射 + 滚动卡片，Header 的 hazeEffect 将模糊此层
            // ══════════════════════════════════════════════════════════
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = headerHazeState)
            ) {
                // 使用 statusBars 高度 + TopAppBar 典型高度（64.dp）作为顶部留白
                val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val topBarHeight = 64.dp
                val totalTopPadding = statusBarPadding + topBarHeight

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .then(if (imePadding) Modifier.imePadding() else Modifier)
                        .padding(horizontal = 20.dp)
                ) {
                    // 为顶部 Header overlay 留出空间
                    Spacer(modifier = Modifier.height(totalTopPadding))
                    Spacer(modifier = Modifier.height(24.dp))
                    content()
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // ══════════════════════════════════════════════════════════
            // Layer 2: Header 悬浮 Overlay（同级兄弟，非子节点！）
            // hazeEffect 完美捕获 Layer 1 的内容并进行高斯物理卷积模糊
            // ══════════════════════════════════════════════════════════
            val glowBorderBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF8083FF).copy(alpha = 0.55f),
                    Color(0xFFD97721).copy(alpha = 0.45f),
                    Color(0xFF8083FF).copy(alpha = 0.55f)
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .clipToBounds()
                    .drawBehind {
                        val strokeWidth = 1.dp.toPx()
                        val y = size.height - strokeWidth / 2
                        drawLine(
                            brush = glowBorderBrush,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokeWidth
                        )
                    }
            ) {
                // 【Haze 真·穿透毛玻璃 Header 背景层】
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeEffect(state = headerHazeState) {
                            blurRadius = 28.dp
                            noiseFactor = 0.012f
                            backgroundColor = Color(0xFF121115).copy(alpha = 0.52f)
                        }
                ) {
                    // 霓虹微光底色渗透层
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

                    // 水晶发光亮边斜折射
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
                    )
                }

                // 【前景内容图层】完全不应用模糊，保持 100% 清晰
                TopAppBar(
                    title = {
                        Box(modifier = Modifier.padding(start = if (onBack != null) 0.dp else 4.dp)) {
                            Text(
                                text = title,
                                style = NexaraTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = NexaraColors.OnSurface
                            )
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = NexaraColors.OnSurface
                                )
                            }
                        }
                    },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = NexaraColors.OnSurface,
                        navigationIconContentColor = NexaraColors.OnSurface,
                        actionIconContentColor = NexaraColors.OnSurface
                    )
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF131315)
@Composable
private fun NexaraPageLayoutPreview() {
    NexaraTheme {
        NexaraPageLayout(
            title = "Settings",
            onBack = {}
        ) {
            Text(
                text = "Page content goes here",
                style = NexaraTypography.bodyMedium,
                color = NexaraColors.OnSurface
            )
        }
    }
}
