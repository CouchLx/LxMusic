package com.example.lxmusic.ui.pages

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lxmusic.data.plugin.PluginStore
import com.example.lxmusic.ui.components.IOLoadingIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 音乐源插件管理页：导入 / 启用 / 停用 / 删除。
 * 只管理用户自己导入的 JS 插件；App 不内置任何音源。
 */
@Composable
internal fun SettingsPluginsContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf(PluginStore.list(context)) }
    var importing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PluginStore.PluginInfo?>(null) }

    fun refresh() {
        plugins = PluginStore.list(context)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        importing = true
        scope.launch(Dispatchers.IO) {
            val displayName = queryDisplayName(context, uri)
            val code = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            val info = if (code.isNullOrBlank()) null else {
                PluginStore.import(context, displayName.removeSuffix(".js"), code)
            }
            withContext(Dispatchers.Main) {
                importing = false
                when {
                    code.isNullOrBlank() -> Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
                    info == null -> Toast.makeText(context, "导入失败", Toast.LENGTH_SHORT).show()
                    info.error != null -> Toast.makeText(
                        context, "插件加载失败：${info.error}", Toast.LENGTH_LONG
                    ).show()
                    else -> {
                        refresh()
                        Toast.makeText(context, "已导入：${info.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column {
        Text(
            text = "音乐源插件",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "导入兼容落雪音乐格式的 JS 音源脚本。插件在本机沙箱运行，只通过 lx.request 访问网络；" +
                "App 不内置、不提供任何音源。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // 全局开关：插件作为解析器参与播放取流/歌词
        var resolveFirst by remember {
            mutableStateOf(com.example.lxmusic.data.plugin.PluginSourceProvider.isResolveFirst(context))
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("优先用插件解析播放地址", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "开启后：酷狗/网易云的歌曲会先交给插件取流，失败自动回退内置链路",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = resolveFirst,
                    onCheckedChange = {
                        resolveFirst = it
                        com.example.lxmusic.data.plugin.PluginSourceProvider.setResolveFirst(context, it)
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { picker.launch("application/javascript") },
                shape = RoundedCornerShape(10.dp),
                enabled = !importing
            ) { Text("导入插件（.js）") }
            if (importing) {
                Spacer(Modifier.padding(horizontal = 6.dp))
                IOLoadingIndicator(modifier = Modifier.height(20.dp))
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text("正在加载音源（最多 15 秒）…", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (plugins.isEmpty()) {
            Text(
                text = "还没有导入任何插件。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plugins.forEach { plugin ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(plugin.name, style = MaterialTheme.typography.bodyLarge)
                                    val meta = listOfNotNull(
                                        plugin.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                                        plugin.author.takeIf { it.isNotBlank() }
                                    ).joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = plugin.enabled && plugin.error == null,
                                    enabled = plugin.error == null,
                                    onCheckedChange = { checked ->
                                        PluginStore.setEnabled(context, plugin.id, checked)
                                        refresh()
                                    }
                                )
                            }
                            if (!plugin.description.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = plugin.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val platformNames =
                                com.example.lxmusic.data.plugin.PluginSourceProvider.supportedPlatformNames(plugin)
                            if (platformNames.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "支持：$platformNames（取流/歌词按源能力）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (plugin.error != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "加载失败：${plugin.error}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = { pendingDelete = plugin },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text("删除", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    val deleting = pendingDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("删除插件") },
            text = { Text("确定删除「${deleting.name}」吗？已收藏的该源歌曲将无法继续播放。") },
            confirmButton = {
                TextButton(onClick = {
                    PluginStore.delete(context, deleting.id)
                    pendingDelete = null
                    refresh()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull() ?: "plugin.js"
}
