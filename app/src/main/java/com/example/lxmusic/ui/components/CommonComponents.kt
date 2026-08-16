package com.example.lxmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lxmusic.ui.theme.getCurrentThemeConfig

/**
 * 统一主题配置的通用组件库
 * 所有组件自动使用当前主题配置的配色方案
 */

// ==================== 卡片组件 ====================

/**
 * 标准卡片
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    elevation: Dp = 2.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeConfig = getCurrentThemeConfig()
    
    Card(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, ambientColor = themeConfig.shadow),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = themeConfig.cardBackground,
            contentColor = themeConfig.textPrimary
        ),
        content = content
    )
}

/**
 * 带标题的卡片
 */
@Composable
fun AppCardWithTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeConfig = getCurrentThemeConfig()
    
    AppCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = themeConfig.textPrimary
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = themeConfig.textSecondary
                        )
                    }
                }
                trailing?.invoke()
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 内容
            content()
        }
    }
}

// ==================== 对话框组件 ====================

/**
 * 标准对话框
 */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    confirmText: String = "确认",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    val themeConfig = getCurrentThemeConfig()
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = themeConfig.dialogBackground,
        titleContentColor = themeConfig.textPrimary,
        textContentColor = themeConfig.textSecondary,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                content?.invoke()
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeConfig.buttonPrimary,
                    contentColor = themeConfig.textOnPrimary
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss?.invoke() ?: onDismissRequest() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = themeConfig.textSecondary
                )
            ) {
                Text(dismissText)
            }
        }
    )
}

/**
 * 自定义内容对话框
 */
@Composable
fun AppCustomDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String = "确认",
    dismissText: String = "取消",
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val themeConfig = getCurrentThemeConfig()
    
    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = themeConfig.dialogBackground,
        titleContentColor = themeConfig.textPrimary,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = content,
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeConfig.buttonPrimary,
                    contentColor = themeConfig.textOnPrimary
                )
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismiss?.invoke() ?: onDismissRequest() },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = themeConfig.textSecondary
                )
            ) {
                Text(dismissText)
            }
        }
    )
}

// ==================== 表单组件 ====================

/**
 * 设置项
 */
@Composable
fun AppSettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val themeConfig = getCurrentThemeConfig()
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        onClick = onClick ?: {}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.invoke()
            
            if (leading != null) {
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = themeConfig.textPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = themeConfig.textSecondary
                    )
                }
            }
            
            trailing?.invoke()
        }
    }
}

/**
 * 设置分组标题
 */
@Composable
fun AppSettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val themeConfig = getCurrentThemeConfig()
    
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = themeConfig.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        AppCard {
            content()
        }
    }
}

// ==================== 按钮组件 ====================

/**
 * 主按钮
 */
@Composable
fun AppPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String
) {
    val themeConfig = getCurrentThemeConfig()
    
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = themeConfig.buttonPrimary,
            contentColor = themeConfig.textOnPrimary,
            disabledContainerColor = themeConfig.buttonPrimary.copy(alpha = 0.5f),
            disabledContentColor = themeConfig.textOnPrimary.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text = text)
    }
}

/**
 * 次按钮
 */
@Composable
fun AppSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String
) {
    val themeConfig = getCurrentThemeConfig()
    
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = themeConfig.buttonPrimary,
            disabledContentColor = themeConfig.buttonPrimary.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text = text)
    }
}

// ==================== 分隔线 ====================

/**
 * 标准分隔线
 */
@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp
) {
    val themeConfig = getCurrentThemeConfig()
    
    Divider(
        modifier = modifier,
        thickness = thickness,
        color = themeConfig.divider
    )
}

// ==================== 空状态 ====================

/**
 * 空状态占位符
 */
@Composable
fun AppEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null
) {
    val themeConfig = getCurrentThemeConfig()
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            icon?.invoke()
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = themeConfig.textSecondary
            )
        }
    }
}
