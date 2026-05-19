package com.promenar.nexara.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraTypography

/**
 * 视觉测试 Demo 页面 — Haze 穿透毛玻璃效果调试
 *
 * 使用多彩渐变背景为毛玻璃效果提供丰富的色彩层次。
 * 通过实时滑块调节模糊采样半径，探索最佳毛玻璃配方。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualDemoScreen(
    onNavigateBack: () -> Unit
) {
    var blurRadius by remember { mutableStateOf(35f) }

    // 多彩渐变背景（替代已移除的 vision_test_bg 底图）
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1a0533), // 深紫
            Color(0xFF0d1b2a), // 深蓝
            Color(0xFF1b2838), // 蓝灰
            Color(0xFF2a1a1a), // 深红褐
            Color(0xFF0a1628), // 深海蓝
            Color(0xFF1a0a2e), // 深紫黑
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 【第 1 层：多彩渐变底图】
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        )

        // 【第 2 层：内容 Scaffold（透明背景）】
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Haze 穿透毛玻璃视觉测试",
                            style = NexaraTypography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.4f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- 介绍面板卡片 ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "本页面用于真机视觉实测。底层为多彩渐变，为 Haze 穿透毛玻璃提供色彩层次。您可以通过下方滑块动态调节采样半径。",
                        color = Color.White.copy(alpha = 0.85f),
                        style = NexaraTypography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- 核心测试卡片 ---
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
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, glowBorderBrush, RoundedCornerShape(24.dp))
                ) {
                    // 半透明深色保护底盘
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF201F22).copy(alpha = 0.72f))
                    )

                    // 霓虹微光底色渗透层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF8083FF).copy(alpha = 0.05f),
                                        Color(0xFFD97721).copy(alpha = 0.03f)
                                    )
                                )
                            )
                    )

                    // 水晶发光斜折射材质层
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.06f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // 前景内容层
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "纯色渐变底盘（无底图）",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Nexara 微晶玻璃",
                                color = Color.White,
                                style = NexaraTypography.headlineLarge.copy(fontSize = 24.sp),
                                fontWeight = FontWeight.ExtraBold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "当前采样半径: ${blurRadius.toInt()}dp",
                                color = Color.White.copy(alpha = 0.9f),
                                style = NexaraTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }

                        // 显示对比信息
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text("视觉观测指南:", color = Color.White, style = NexaraTypography.labelLarge)
                            Text("1. 注意卡片内的渐变底色是否呈现柔和的毛玻璃质感；", color = Color.White.copy(alpha = 0.75f), style = NexaraTypography.bodySmall)
                            Text("2. 观察白色字体的边缘，它们应该极致锐利；", color = Color.White.copy(alpha = 0.75f), style = NexaraTypography.bodySmall)
                            Text("3. 当半径调大时，色彩过渡会更加丝滑。", color = Color.White.copy(alpha = 0.75f), style = NexaraTypography.bodySmall)
                        }
                    }
                }

                // --- 实时交互滑块控制区 ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("实时模糊采样半径", color = Color.White, style = NexaraTypography.labelLarge)
                            Text("${blurRadius.toInt()} px", color = Color.White, style = NexaraTypography.bodyMedium, fontWeight = FontWeight.Bold)
                        }

                        Slider(
                            value = blurRadius,
                            onValueChange = { blurRadius = it },
                            valueRange = 0f..60f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("无模糊", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                            Text("35px (预设)", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                            Text("极致重影 (60px)", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
