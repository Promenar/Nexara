package com.promenar.nexara.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promenar.nexara.R
import com.promenar.nexara.ui.theme.NexaraColors
import com.promenar.nexara.ui.theme.NexaraShapes
import com.promenar.nexara.ui.theme.NexaraTypography

/**
 * A reusable glassmorphic item for lists, adhering to the Stitch Design Spec.
 * Used primarily in Hub, Settings, and List views.
 * 
 * Auto-integrates position tracking to alignment layout with NexaraGlowBackground,
 * delivering 100% precise physical aligned glass blur.
 */
@Composable
fun NexaraSettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    var cardOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    NexaraGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                cardOffset = coordinates.positionInWindow()
            }
            .clip(NexaraShapes.large)
            .clickable(onClick = onClick),
        shape = NexaraShapes.large as androidx.compose.foundation.shape.RoundedCornerShape,
        underlay = {
            NexaraGlowBackground(alignmentOffset = cardOffset) {}
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(NexaraColors.SurfaceHigh, androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NexaraColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = NexaraTypography.labelMedium,
                        color = NexaraColors.OnSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = NexaraTypography.bodyMedium.copy(fontSize = 14.sp),
                            color = NexaraColors.OnSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.common_cd_navigate),
                tint = NexaraColors.Outline,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
