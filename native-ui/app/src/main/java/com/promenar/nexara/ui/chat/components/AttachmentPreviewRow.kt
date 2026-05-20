package com.promenar.nexara.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.promenar.nexara.data.model.Attachment
import com.promenar.nexara.data.model.AttachmentType
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraTypography

/**
 * 附件预览条 — 在输入框上方显示已选择的附件。
 * 支持图片缩略图、视频/音频/文档图标 + 文件名 + 大小。
 * 不兼容的附件显示灰色 + 警告标签。
 */
@Composable
fun AttachmentPreviewRow(
    attachments: List<Pair<Attachment, Boolean>>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(attachments.size) { index ->
            val (attachment, isCompatible) = attachments[index]
            AttachmentChip(
                attachment = attachment,
                isCompatible = isCompatible,
                onRemove = { onRemove(index) }
            )
        }
    }
}

@Composable
private fun AttachmentChip(
    attachment: Attachment,
    isCompatible: Boolean,
    onRemove: () -> Unit
) {
    val alpha = if (isCompatible) 1f else 0.5f
    val bgColor = if (isCompatible) NexaraColors.SurfaceHigh else Color(0xFFFFEBEE)

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor.copy(alpha = alpha))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (attachment.type) {
                AttachmentType.IMAGE -> {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                AttachmentType.VIDEO -> {
                    Icon(
                        Icons.Rounded.VideoFile,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = NexaraColors.OnSurfaceVariant.copy(alpha = alpha)
                    )
                }
                AttachmentType.AUDIO -> {
                    Icon(
                        Icons.Rounded.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = NexaraColors.OnSurfaceVariant.copy(alpha = alpha)
                    )
                }
                AttachmentType.DOCUMENT -> {
                    Icon(
                        Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = NexaraColors.OnSurfaceVariant.copy(alpha = alpha)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = attachment.fileName.ifEmpty { "文件" },
                style = NexaraTypography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = NexaraColors.OnBackground.copy(alpha = alpha),
                fontSize = 10.sp
            )

            if (!isCompatible) {
                Text(
                    text = "不兼容",
                    style = NexaraTypography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 9.sp
                )
            } else if (attachment.sizeBytes > 0) {
                Text(
                    text = formatFileSize(attachment.sizeBytes),
                    style = NexaraTypography.labelSmall,
                    color = NexaraColors.OnSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 9.sp
                )
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
