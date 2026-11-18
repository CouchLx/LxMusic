package com.example.lxmusic.ui.roaming

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lxmusic.data.roaming.PersonalRoamingController
import com.example.lxmusic.data.roaming.RoamingChoice

/**
 * 私人漫游弹窗（首页入口用）：模式单选 + 场景九宫格 + 设置 + 开始/切换/结束。
 *
 * 布局对照 CurrentMusicX 的 PersonalRoamingDialog，玻璃对话框换成本项目 Material3 BottomSheet。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PersonalRoamingSheet(
    controller: PersonalRoamingController,
    isLoggedIn: Boolean,
    onDismiss: () -> Unit,
    onLogin: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
        ) {
            // 模式 + 场景：只有这块滚动
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                PersonalRoamingContent(
                    controller = controller,
                    isLoggedIn = isLoggedIn,
                    onLogin = onLogin,
                    onContinue = onDismiss,
                    embedded = false,
                    showActions = false
                )
            }
            // 开始/切换/结束按钮常驻底部，不用翻到最下面
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
            PersonalRoamingActions(
                controller = controller,
                isLoggedIn = isLoggedIn,
                onLogin = onLogin,
                onContinue = onDismiss,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 12.dp, bottom = 24.dp)
            )
        }
    }
}

/**
 * 漫游内容本体。`embedded=true` 时给播放器侧页复用（不自己滚动，由外层提供滚动）。
 */
@Composable
fun PersonalRoamingContent(
    controller: PersonalRoamingController,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onContinue: () -> Unit,
    embedded: Boolean,
    modifier: Modifier = Modifier,
    // onDark=true：播放器漫游页，内容直接压在动效背景上，用播放器的前景色
    onDark: Boolean = false,
    tint: Color = Color.Unspecified,
    tintVariant: Color = Color.Unspecified,
    // 弹窗要把按钮常驻底部，就自己在外面渲染 PersonalRoamingActions，这里关掉
    showActions: Boolean = true
) {
    val state by controller.state.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    val puzzleMode = state.choice.code == "PUZZLE_MODE_RCMD"
    // 点模式/场景 = 直接开始漫游（不再要求滚到底部点「开始漫游」）：
    // 拉歌成功后关掉弹窗或翻回播放器页面
    val startNow: (RoamingChoice) -> Unit = { choice ->
        controller.select(choice)
        controller.start(choice) { onContinue() }
    }

    val titleColor = if (onDark) tint else MaterialTheme.colorScheme.onSurface
    val subColor = if (onDark) tintVariant else MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (onDark) tint else MaterialTheme.colorScheme.primary
    val dividerColor = if (onDark) tint.copy(alpha = .18f)
    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "私人漫游",
                Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            TextButton(onClick = { settingsOpen = true }) {
                Text("设置", color = accent)
            }
        }
        Text(
            if (state.active) "漫游中 · ${state.choice.title}" else "按你的喜好，连续听点好音乐",
            fontSize = 12.sp,
            color = subColor
        )

        if (state.active && puzzleMode) {
            RoamingCoverPuzzle(
                cover = state.currentCover,
                songKey = state.currentSongId,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            Column(Modifier.fillMaxWidth()) {
                state.catalog.modes.forEachIndexed { index, mode ->
                    RoamingRow(
                        choice = mode,
                        selected = state.choice.key == mode.key,
                        enabled = !state.loading,
                        onClick = { startNow(mode) },
                        titleColor = titleColor,
                        subColor = subColor,
                        accent = accent
                    )
                    if (index < state.catalog.modes.lastIndex) {
                        HorizontalDivider(color = dividerColor)
                    }
                }
            }

            Text(
                "场景模式",
                Modifier.padding(top = 4.dp),
                fontSize = 13.sp,
                color = subColor
            )
            SceneGrid(
                scenes = state.catalog.scenes,
                selectedKey = state.choice.key,
                enabled = !state.loading,
                onSelect = { startNow(it) },
                titleColor = titleColor,
                subColor = subColor,
                accent = accent
            )
        }

        state.error?.let {
            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }

        if (showActions) {
            PersonalRoamingActions(
                controller = controller,
                isLoggedIn = isLoggedIn,
                onLogin = onLogin,
                onContinue = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("漫游设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingRow(
                        title = "提前补充推荐",
                        summary = "快播完时自动拉下一批，避免中途断流",
                        checked = state.settings.preload,
                        onChange = { controller.updateSettings(state.settings.copy(preload = it)) }
                    )
                    SettingRow(
                        title = "结束时恢复原队列",
                        summary = if (state.settings.restoreQueue) "结束后回到之前的队列与进度，保持暂停"
                        else "结束后保留当前歌曲与队列",
                        checked = state.settings.restoreQueue,
                        onChange = { controller.updateSettings(state.settings.copy(restoreQueue = it)) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { settingsOpen = false }) { Text("返回模式选择") }
            }
        )
    }
}

@Composable
/** 底部动作区：登录 / 开始漫游 / 切换 / 结束。弹窗里常驻在底部，播放器页里跟在内容后面。 */
fun PersonalRoamingActions(
    controller: PersonalRoamingController,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsState()
    when {
        !isLoggedIn -> Button(onClick = onLogin, modifier = modifier.fillMaxWidth()) {
            Text("登录网易云")
        }
        state.active -> Row(
            modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = { controller.stop() }, modifier = Modifier.weight(1f)) {
                Text("结束漫游", color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    if (state.loading) Unit
                    else if (state.choice.key != state.settings.selection) controller.start(state.choice)
                    else onContinue()
                },
                enabled = !state.loading,
                modifier = Modifier.weight(1f)
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when {
                        state.loading -> "寻找中…"
                        state.choice.key != state.settings.selection -> "切换到${state.choice.title}"
                        else -> "继续聆听"
                    }
                )
            }
        }
        else -> Button(
            onClick = { if (!state.loading) controller.start(state.choice) },
            enabled = !state.loading,
            modifier = modifier.fillMaxWidth()
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("正在寻找推荐…")
            } else {
                Text("开始漫游")
            }
        }
    }
}

@Composable
private fun RoamingRow(
    choice: RoamingChoice,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    titleColor: Color,
    subColor: Color,
    accent: Color
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = accent,
                unselectedColor = subColor
            )
        )
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                choice.title,
                fontSize = 15.sp,
                color = if (selected) accent else titleColor
            )
            if (choice.subtitle.isNotBlank()) {
                Text(
                    choice.subtitle,
                    fontSize = 12.sp,
                    color = subColor
                )
            }
        }
    }
}

@Composable
private fun SceneGrid(
    scenes: List<RoamingChoice>,
    selectedKey: String,
    enabled: Boolean,
    onSelect: (RoamingChoice) -> Unit,
    titleColor: Color,
    subColor: Color,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        scenes.chunked(4).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                group.forEach { scene ->
                    val selected = selectedKey == scene.key
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) accent.copy(alpha = .16f)
                                else Color.Transparent
                            )
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onSelect(scene) }
                            )
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = roamingSceneIcon(scene.code),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (selected) accent else subColor
                        )
                        Text(
                            scene.title,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) accent else titleColor
                        )
                    }
                }
                repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 场景码 → 单色图标（对齐参考项目的单色 SVG 画风） */
internal fun roamingSceneIcon(code: String) = when (code) {
    "EXERCISE" -> Icons.Rounded.FitnessCenter
    "FOCUS" -> Icons.Rounded.CenterFocusStrong
    "NIGHT_EMO" -> Icons.Rounded.DarkMode
    "SLEEP_HELP" -> Icons.Rounded.Bedtime
    "RELAX" -> Icons.Rounded.Spa
    "COMMUTE" -> Icons.Rounded.DirectionsCar
    "CHEERFUL" -> Icons.Rounded.Celebration
    "LYRICAL" -> Icons.Rounded.Edit
    "CURE" -> Icons.Rounded.Healing
    "SWEET" -> Icons.Rounded.Favorite
    "RHYTHM_BLUES" -> Icons.Rounded.MusicNote
    "RAINY" -> Icons.Rounded.Umbrella
    "GAMES" -> Icons.Rounded.SportsEsports
    "RAP" -> Icons.Rounded.Mic
    "K_POP" -> Icons.Rounded.Stars
    "ORIGINAL_MUSICAL" -> Icons.Rounded.AutoAwesome
    "ELECTRONIC" -> Icons.Rounded.Headphones
    "TAKE_SHOWER" -> Icons.Rounded.Bathtub
    "COFFEE_SHOP" -> Icons.Rounded.LocalCafe
    "ROCK", "PUNK" -> Icons.Rounded.Bolt
    "INSPIRATIONAL" -> Icons.Rounded.TrendingUp
    "CHINESE", "ENGLISH", "YUEYU", "JAPANESE", "FRANCH" -> Icons.Rounded.Language
    "MANYAO" -> Icons.Rounded.GraphicEq
    "JINGDIAN" -> Icons.Rounded.Album
    "LIGHT" -> Icons.Rounded.Waves
    "GUOFENG" -> Icons.Rounded.MenuBook
    "FOLK" -> Icons.Rounded.QueueMusic
    "ACG" -> Icons.Rounded.EmojiEmotions
    "GUDIAN", "JAZZ" -> Icons.Rounded.MusicNote
    "GLOBAL" -> Icons.Rounded.Public
    "BLUE" -> Icons.Rounded.ColorLens
    "DANCE" -> Icons.Rounded.DirectionsRun
    "LATIN" -> Icons.Rounded.LocalFireDepartment
    "COUNTRY" -> Icons.Rounded.Landscape
    "MUSICAL" -> Icons.Rounded.Theaters
    "YINGSHI" -> Icons.Rounded.Movie
    else -> Icons.Rounded.MusicNote
}
