package com.promenar.nexara.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import com.promenar.nexara.R
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraTypography
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import com.promenar.nexara.ui.common.LocalHazeState
import com.promenar.nexara.ui.common.NexaraGlowBackground

enum class AppTab(@StringRes val titleRes: Int, val icon: ImageVector) {
    CHAT(R.string.nav_tab_chat, Icons.Rounded.ChatBubble),
    LIBRARY(R.string.nav_tab_library, Icons.Rounded.LocalLibrary),
    SETTINGS(R.string.nav_tab_settings, Icons.Rounded.Settings)
}

@Composable
fun MainTabScaffold(
    onNavigateToSecondary: (String) -> Unit,
    onNavigateToSessionList: (String) -> Unit,
    onNavigateToAgentEdit: (String) -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.CHAT) }
    val mainHazeState = rememberHazeState()

    CompositionLocalProvider(LocalHazeState provides mainHazeState) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Layer 0: 全屏大极光底座
            NexaraGlowBackground(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = mainHazeState)
            ) {}

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NexaraBottomNavigationBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (selectedTab) {
                        AppTab.CHAT -> com.promenar.nexara.ui.hub.AgentHubScreen(
                            onNavigateToSessionList = onNavigateToSessionList,
                            onNavigateToAgentEdit = onNavigateToAgentEdit
                        )
                        AppTab.LIBRARY -> com.promenar.nexara.ui.rag.RagHomeScreen(
                            onNavigateToFolder = { folderId, folderName ->
                                onNavigateToSecondary(com.promenar.nexara.navigation.NavDestinations.ragFolder(folderId, folderName))
                            },
                            onNavigateToConfig = { onNavigateToSecondary("rag_global_config") },
                            onNavigateToGraph = { onNavigateToSecondary("knowledge_graph") },
                            onNavigateToDocEditor = { docId ->
                                onNavigateToSecondary(com.promenar.nexara.navigation.NavDestinations.docEditor(docId))
                            }
                        )
                        AppTab.SETTINGS -> com.promenar.nexara.ui.hub.UserSettingsHomeScreen(onNavigateToSecondary = onNavigateToSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun NexaraBottomNavigationBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    val mainHazeState = LocalHazeState.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .clipToBounds()
    ) {
        // Haze 物理高斯模糊背景层（与顶栏及卡片效果完全对齐，28.dp 极致毛玻璃磨砂感）
        Box(
            modifier = Modifier
                .matchParentSize()
                .hazeEffect(state = mainHazeState) {
                    blurRadius = 28.dp
                    noiseFactor = 0.012f
                    backgroundColor = Color(0xFF121115).copy(alpha = 0.40f) // 极致高档清透偏光
                }
        ) {
            // 水晶微光折射层
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // 顶边超细发光亮边（0.5.dp 晶莹质感）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF8083FF).copy(alpha = 0.35f),
                            Color(0xFFD97721).copy(alpha = 0.25f),
                            Color(0xFF8083FF).copy(alpha = 0.35f)
                        )
                    )
                )
                .align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                TabItem(
                    tab = tab,
                    isSelected = isSelected,
                    onClick = { onTabSelected(tab) }
                )
            }
        }
    }
}


@Composable
private fun TabItem(
    tab: AppTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "TabScale"
    )

    val contentColor = if (isSelected) NexaraColors.Primary else NexaraColors.Outline
    val glowColor = if (isSelected) NexaraColors.Primary.copy(alpha = 0.5f) else Color.Transparent

    Column(
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = stringResource(tab.titleRes),
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
                .drawBehind {
                    if (isSelected) {
                        drawIntoCanvas { canvas ->
                            val paint = Paint().apply {
                                color = glowColor
                            }
                            val frameworkPaint = paint.asFrameworkPaint()
                            frameworkPaint.setShadowLayer(
                                8.dp.toPx(),
                                0f, 0f,
                                android.graphics.Color.argb(128, 192, 193, 255)
                            )
                            canvas.drawCircle(center, center.x * 0.8f, paint)
                        }
                    }
                }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(tab.titleRes),
            style = NexaraTypography.labelMedium.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.1.sp
            ),
            color = contentColor
        )
    }
}
