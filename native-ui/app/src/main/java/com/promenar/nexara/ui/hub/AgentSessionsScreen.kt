// UNIT TEST EXEMPTION: Pure UI and layout rendering

package com.promenar.nexara.ui.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.promenar.nexara.R
import com.promenar.nexara.ui.common.NexaraBackButton
import com.promenar.nexara.ui.common.NexaraGlassCard
import com.promenar.nexara.ui.common.NexaraSearchBar
import com.promenar.nexara.ui.common.SwipeableItem
import com.promenar.nexara.ui.common.ConfirmDialog
import com.promenar.nexara.ui.common.LocalHazeState
import com.promenar.nexara.ui.common.NexaraGlowBackground
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraShapes
import com.promenar.nexara.ui.theme.NexaraTypography
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AgentSessionsScreen(
    agentId: String,
    onNavigateBack: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToAgentEdit: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SessionListViewModel = viewModel(factory = SessionListViewModel.factory(context.applicationContext as android.app.Application))
    val sessions by viewModel.sessions.collectAsState()
    val agentName by viewModel.agentName.collectAsState()
    val agentColor by viewModel.agentColor.collectAsState()
    var sessionToDelete by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val visualStyle = com.promenar.nexara.ui.theme.LocalVisualStyle.current
    val isM3 = visualStyle == com.promenar.nexara.ui.theme.VisualStyle.NATIVE_MATERIAL_3

    val statusBarPadding = androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val safeTopPadding = statusBarPadding + 64.dp + 16.dp

    LaunchedEffect(agentId) {
        viewModel.loadSessions(agentId)
    }

    val parsedAgentColor = try {
        Color(android.graphics.Color.parseColor(agentColor))
    } catch (e: Exception) {
        if (isM3) MaterialTheme.colorScheme.primary else NexaraColors.Primary
    }

    val hazeState = rememberHazeState()
    val currentHazeState = if (isM3) null else hazeState

    CompositionLocalProvider(LocalHazeState provides currentHazeState) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isM3) {
                // UI 布局与样式代码，根据全局开发规范 §3.4，在此声明豁免单元测试
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        AgentSessionHeader(
                            agentName = agentName,
                            sessionCount = sessions.size,
                            onBack = onNavigateBack,
                            onSettings = onNavigateToAgentEdit
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                viewModel.createSession(agentId) { sessionId ->
                                    onNavigateToChat(sessionId)
                                }
                            },
                            containerColor = parsedAgentColor,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.sessions_cd_new), modifier = Modifier.size(28.dp))
                        }
                    }
                ) { paddingValues ->
                    ConfirmDialog(
                        show = sessionToDelete != null,
                        onDismiss = { sessionToDelete = null },
                        onConfirm = {
                            sessionToDelete?.let { viewModel.deleteSession(it) }
                            sessionToDelete = null
                        },
                        title = stringResource(R.string.session_settings_delete_title),
                        description = stringResource(R.string.session_settings_delete_message),
                        confirmLabel = stringResource(R.string.shared_btn_delete),
                        destructive = true
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        if (sessions.isEmpty() && searchQuery.isEmpty()) {
                            EmptySessionsState(
                                onCreateSession = {
                                    viewModel.createSession(agentId) { sessionId ->
                                        onNavigateToChat(sessionId)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 20.dp, end = 20.dp,
                                    top = 16.dp, bottom = 120.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item(key = "search_bar") {
                                    NexaraSearchBar(
                                        value = searchQuery,
                                        onValueChange = {
                                            searchQuery = it
                                            viewModel.searchSessions(it)
                                        },
                                        placeholder = stringResource(R.string.sessions_search_placeholder),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                    )
                                }

                                itemsIndexed(sessions, key = { _, s -> s.id }) { _, session ->
                                    val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                    val timeString = formatter.format(Date(session.updatedAt))

                                    SwipeableItem(
                                        onPin = { viewModel.pinSession(session.id) },
                                        onDelete = { sessionToDelete = session.id },
                                        isPinned = session.isPinned
                                    ) {
                                        SessionCard(
                                            title = session.title,
                                            time = timeString,
                                            preview = session.lastMessage,
                                            isPinned = session.isPinned,
                                            onClick = {
                                                viewModel.selectSession(session.id)
                                                onNavigateToChat(session.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // 背景与物理模糊采样源层：包裹极光背景与 Scaffold，整轨应用 hazeSource 捕获极光与列表内容
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = hazeState)
                ) {
                    NexaraGlowBackground(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Scaffold(
                            containerColor = Color.Transparent,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            floatingActionButton = {
                                FloatingActionButton(
                                    onClick = {
                                        viewModel.createSession(agentId) { sessionId ->
                                            onNavigateToChat(sessionId)
                                        }
                                    },
                                    containerColor = parsedAgentColor,
                                    contentColor = Color.White,
                                    shape = CircleShape,
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.sessions_cd_new), modifier = Modifier.size(28.dp))
                                }
                            }
                        ) { paddingValues ->
                            ConfirmDialog(
                                show = sessionToDelete != null,
                                onDismiss = { sessionToDelete = null },
                                onConfirm = {
                                    sessionToDelete?.let { viewModel.deleteSession(it) }
                                    sessionToDelete = null
                                },
                                title = stringResource(R.string.session_settings_delete_title),
                                description = stringResource(R.string.session_settings_delete_message),
                                confirmLabel = stringResource(R.string.shared_btn_delete),
                                destructive = true
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                            ) {
                                if (sessions.isEmpty() && searchQuery.isEmpty()) {
                                    EmptySessionsState(
                                        onCreateSession = {
                                            viewModel.createSession(agentId) { sessionId ->
                                                onNavigateToChat(sessionId)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(
                                            start = 20.dp, end = 20.dp,
                                            top = safeTopPadding, bottom = 120.dp
                                        ),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 搜索栏做为普通滚动 item，彻底移除有色粘连遮罩
                                        item(key = "search_bar") {
                                            NexaraSearchBar(
                                                value = searchQuery,
                                                onValueChange = {
                                                    searchQuery = it
                                                    viewModel.searchSessions(it)
                                                },
                                                placeholder = stringResource(R.string.sessions_search_placeholder),
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                                            )
                                        }

                                        itemsIndexed(sessions, key = { _, s -> s.id }) { _, session ->
                                            val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                                            val timeString = formatter.format(Date(session.updatedAt))

                                            SwipeableItem(
                                                onPin = { viewModel.pinSession(session.id) },
                                                onDelete = { sessionToDelete = session.id },
                                                isPinned = session.isPinned
                                            ) {
                                                SessionCard(
                                                    title = session.title,
                                                    time = timeString,
                                                    preview = session.lastMessage,
                                                    isPinned = session.isPinned,
                                                    onClick = {
                                                        viewModel.selectSession(session.id)
                                                        onNavigateToChat(session.id)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isM3) {
                // 顶栏悬浮 Overlay：层叠在 Scaffold 之上，仅在 Haze 物理毛玻璃下悬浮渲染以实现物理模糊穿透
                AgentSessionHeader(
                    agentName = agentName,
                    sessionCount = sessions.size,
                    onBack = onNavigateBack,
                    onSettings = onNavigateToAgentEdit
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentSessionHeader(
    agentName: String,
    sessionCount: Int,
    onBack: () -> Unit,
    onSettings: () -> Unit
) {
    val hazeState = LocalHazeState.current
    val visualStyle = com.promenar.nexara.ui.theme.LocalVisualStyle.current
    val isM3 = visualStyle == com.promenar.nexara.ui.theme.VisualStyle.NATIVE_MATERIAL_3
    val glowBorderBrush = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0x00FFFFFF),
                Color(0x1AFFFFFF),
                Color(0x33FFFFFF),
                Color(0x1AFFFFFF),
                Color(0x00FFFFFF)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .clipToBounds()
            .drawBehind {
                val strokeWidth = if (isM3) 0.5.dp.toPx() else 1.dp.toPx()
                val y = size.height - strokeWidth / 2
                if (isM3) {
                    drawLine(
                        color = NexaraColors.OutlineVariant,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth
                    )
                } else {
                    drawLine(
                        brush = glowBorderBrush,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth
                    )
                }
            }
    ) {
        if (isM3) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface)
            )
        } else if (hazeState != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeEffect(state = hazeState) {
                        blurRadius = 28.dp
                        noiseFactor = 0.012f
                        backgroundColor = Color(0xFF121115).copy(alpha = 0.52f)
                    }
            ) {
                // 水晶亮边及氛围光
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x10FFFFFF),
                                    Color(0x02FFFFFF)
                                )
                            )
                        )
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(NexaraColors.CanvasBackground.copy(alpha = 0.85f))
            )
        }

        TopAppBar(
            title = {
                Column {
                    Text(
                        text = agentName,
                        style = NexaraTypography.titleMedium,
                        color = if (isM3) MaterialTheme.colorScheme.onSurface else NexaraColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.sessions_count_format, sessionCount),
                        style = NexaraTypography.labelSmall,
                        color = if (isM3) MaterialTheme.colorScheme.onSurfaceVariant else NexaraColors.OnSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                NexaraBackButton(onClick = onBack)
            },
            actions = {
                IconButton(onClick = onSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.sessions_cd_settings),
                        tint = if (isM3) MaterialTheme.colorScheme.onSurface else NexaraColors.OnSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(0, 0, 0, 0), // 彻底移除 TopAppBar 默认自带的 windowInsets 状态栏高度消费，防止内容往下掉导致重合
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        )
    }
}

@Composable
private fun SessionCard(
    title: String,
    time: String,
    preview: String?,
    isPinned: Boolean = false,
    onClick: () -> Unit
) {
    var cardOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val visualStyle = com.promenar.nexara.ui.theme.LocalVisualStyle.current
    val isM3 = visualStyle == com.promenar.nexara.ui.theme.VisualStyle.NATIVE_MATERIAL_3

    val cardColor = if (isM3) {
        if (isPinned) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        }
    } else null

    NexaraGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                cardOffset = coordinates.positionInWindow()
            },
        shape = RoundedCornerShape(12.dp),
        underlay = if (isM3) null else {
            {
                com.promenar.nexara.ui.common.NexaraGlowBackground(alignmentOffset = cardOffset) {}
            }
        },
        containerColor = cardColor,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    style = NexaraTypography.headlineMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (isM3) MaterialTheme.colorScheme.onSurface else NexaraColors.OnSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = time,
                    style = NexaraTypography.labelMedium.copy(fontSize = 11.sp),
                    color = if (isM3) MaterialTheme.colorScheme.onSurfaceVariant else NexaraColors.OnSurfaceVariant
                )
            }

            if (!preview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = preview,
                    style = NexaraTypography.bodyMedium.copy(fontSize = 13.sp),
                    color = if (isM3) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else NexaraColors.OnSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }

            if (isPinned) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isM3) MaterialTheme.colorScheme.primaryContainer else NexaraColors.GlassSurface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.sessions_tag_pinned),
                            style = NexaraTypography.labelMedium.copy(fontSize = 10.sp),
                            color = if (isM3) MaterialTheme.colorScheme.onPrimaryContainer else NexaraColors.Primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySessionsState(
    onCreateSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visualStyle = com.promenar.nexara.ui.theme.LocalVisualStyle.current
    val isM3 = visualStyle == com.promenar.nexara.ui.theme.VisualStyle.NATIVE_MATERIAL_3

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(text = "💬", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.sessions_empty_title),
                style = NexaraTypography.headlineMedium,
                color = if (isM3) MaterialTheme.colorScheme.onSurface else NexaraColors.OnSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sessions_empty_subtitle),
                style = NexaraTypography.bodyMedium,
                color = if (isM3) MaterialTheme.colorScheme.onSurfaceVariant else NexaraColors.OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(
                onClick = onCreateSession,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isM3) MaterialTheme.colorScheme.primaryContainer else NexaraColors.Primary.copy(alpha = 0.15f),
                    contentColor = if (isM3) MaterialTheme.colorScheme.onPrimaryContainer else NexaraColors.Primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sessions_btn_new))
            }
        }
    }
}
