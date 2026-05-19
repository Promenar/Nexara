package com.promenar.nexara.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.promenar.nexara.ui.common.NexaraPageLayout
import com.promenar.nexara.ui.common.NexaraSettingsItem
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraTypography
import java.io.File

@Composable
fun DeveloperScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVisualDemo: () -> Unit
) {
    val context = LocalContext.current

    NexaraPageLayout(
        title = "开发者面板",
        onBack = onNavigateBack
    ) {
        Text(
            text = "系统诊断与调试",
            style = NexaraTypography.labelLarge,
            color = NexaraColors.Primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        NexaraSettingsItem(
            icon = Icons.Rounded.Visibility,
            title = "视觉DEMO",
            subtitle = "测试高级 GPU 高斯模糊毛玻璃与自定义背景图融合效果",
            onClick = onNavigateToVisualDemo
        )

        Spacer(modifier = Modifier.height(8.dp))

        NexaraSettingsItem(
            icon = Icons.Rounded.Share,
            title = "导出运行日志",
            subtitle = "分享 nexara_logs.txt 文件以进行故障诊断",
            onClick = {
                val logFile = File(context.filesDir, "nexara_logs.txt")
                if (logFile.exists()) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        logFile
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "分享日志文件"))
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        NexaraSettingsItem(
            icon = Icons.Rounded.BugReport,
            title = "清除运行日志",
            subtitle = "重置日志文件大小",
            onClick = {
                val logFile = File(context.filesDir, "nexara_logs.txt")
                if (logFile.exists()) {
                    logFile.writeText("")
                }
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "设备信息",
            style = NexaraTypography.labelLarge,
            color = NexaraColors.Primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Card(
            colors = CardDefaults.cardColors(containerColor = NexaraColors.SurfaceContainer.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow("OS Version", android.os.Build.VERSION.RELEASE)
                InfoRow("Model", android.os.Build.MODEL)
                InfoRow("Manufacturer", android.os.Build.MANUFACTURER)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = NexaraTypography.bodySmall, color = NexaraColors.OnSurfaceVariant)
        Text(value, style = NexaraTypography.bodySmall, color = NexaraColors.OnSurface)
    }
}
