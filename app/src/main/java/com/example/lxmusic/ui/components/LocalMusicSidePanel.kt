package com.example.lxmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lxmusic.R

/**
 * 本地音乐左侧分类面板（图五风格）。
 * 圆角卡片背景，两组菜单项：分类导航 + 操作项。
 * 展开时右边内容冻结不可点击，收起后恢复正常。
 */
@Composable
fun LocalMusicSidePanel(
    currentSection: Int,
    onSectionClick: (Int) -> Unit,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .navigationBarsPadding()
            .padding(horizontal = 12.dp)
            .padding(top = 96.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 分类导航卡片（单曲/专辑/艺术家/文件夹）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = 4.dp)
        ) {
            val navItems = listOf(
                DrawerItem(0, R.string.drawer_songs, Icons.Default.MusicNote),
                DrawerItem(1, R.string.drawer_albums, Icons.Default.Album),
                DrawerItem(2, R.string.drawer_artists, Icons.Default.Person),
                DrawerItem(3, R.string.drawer_folders, Icons.Default.Folder)
            )
            navItems.forEach { item ->
                PanelRow(
                    label = stringResource(item.labelRes),
                    icon = item.icon,
                    selected = currentSection == item.id,
                    onClick = { onSectionClick(item.id) }
                )
            }
        }

        // 操作项卡片（扫描/设置）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = 4.dp)
        ) {
            PanelRow(
                label = stringResource(R.string.drawer_scan),
                icon = Icons.Default.Refresh,
                selected = false,
                onClick = onScanClick
            )
            PanelRow(
                label = stringResource(R.string.drawer_settings),
                icon = Icons.Default.Settings,
                selected = false,
                onClick = onSettingsClick
            )
        }
    }
}

private data class DrawerItem(
    val id: Int,
    val labelRes: Int,
    val icon: ImageVector
)

@Composable
private fun PanelRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else
                    Color.Transparent
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = if (selected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface
        )
    }
}
