# 主题配置系统使用指南

## 1. 主题配置结构

项目采用分层主题配置系统：

```
AppThemeConfig
├── 卡片/对话框配色
├── 导航栏配色
├── 播放条配色
├── 列表项配色
├── 输入框配色
├── 按钮配色
├── 分隔线和背景
├── 文字配色
└── 特殊效果
```

## 2. 四种主题模式

### 2.1 默认浅色模式
- 使用场景：未开启悬浮底栏 + 浅色主题
- 配置函数：`defaultLightConfig()`
- 特点：白色背景，紫色主色调

### 2.2 默认深色模式
- 使用场景：未开启悬浮底栏 + 深色主题
- 配置函数：`defaultDarkConfig()`
- 特点：深色背景，紫色主色调

### 2.3 悬浮底栏浅色模式
- 使用场景：开启悬浮底栏 + 浅色主题
- 配置函数：`floatingBottomBarLightConfig()`
- 特点：白色背景，播放条白色，导航栏半透明

### 2.4 悬浮底栏深色模式
- 使用场景：开启悬浮底栏 + 深色主题
- 配置函数：`floatingBottomBarDarkConfig()`
- 特点：深色背景，播放条深色，导航栏半透明

## 3. 配色方案详情

### 3.1 卡片/对话框配色

| 属性 | 浅色模式 | 深色模式 | 说明 |
|------|----------|----------|------|
| cardBackground | #FFFFFF | #1E1E1E | 卡片背景色 |
| cardSurface | #FFFFFF | #2D2D2D | 卡片表面色 |
| dialogBackground | #FFFFFF | #1E1E1E | 对话框背景色 |
| dialogSurface | #FFFFFF | #2D2D2D | 对话框表面色 |
| bottomSheetBackground | #FFFFFF | #1E1E1E | 底部表单背景色 |

**重要提示**：所有卡片和对话框使用不透明背景，确保内容清晰可读。

### 3.2 导航栏配色

| 属性 | 默认模式 | 悬浮底栏模式 | 说明 |
|------|----------|--------------|------|
| navBarBackground | #FFFFFF | #FFFFFF(80%) | 导航栏背景 |
| navBarSurface | #FFFFFF | #FFFFFF(90%) | 导航栏表面 |
| navBarItemActive | #6750A4 | #6750A4 | 选中项颜色 |
| navBarItemInactive | #757575 | #757575 | 未选中项颜色 |

### 3.3 播放条配色

| 属性 | 默认模式 | 悬浮底栏模式 | 说明 |
|------|----------|--------------|------|
| playerBarBackground | 主题混合色 | #FFFFFF | 播放条背景 |
| playerBarSurface | 主题混合色 | #FFFFFF | 播放条表面 |

**注意**：悬浮底栏模式下播放条使用固定白色，不受主题颜色影响。

### 3.4 文字配色

| 属性 | 浅色模式 | 深色模式 | 说明 |
|------|----------|----------|------|
| textPrimary | #1C1B1F | #E6E1E5 | 主要文字 |
| textSecondary | #757575 | #9E9E9E | 次要文字 |
| textOnPrimary | #FFFFFF | #381E72 | 主色上的文字 |
| textOnSurface | #1C1B1F | #E6E1E5 | 表面上的文字 |

### 3.5 按钮配色

| 属性 | 浅色模式 | 深色模式 | 说明 |
|------|----------|----------|------|
| buttonPrimary | #6750A4 | #D0BCFF | 主按钮 |
| buttonSecondary | #E8DEF8 | #4A4458 | 次按钮 |
| buttonSurface | #FFFFFF | #2D2D2D | 按钮表面 |

### 3.6 特殊效果

| 属性 | 浅色模式 | 深色模式 | 说明 |
|------|----------|----------|------|
| overlay | #000000(32%) | #000000(50%) | 遮罩层 |
| scrim | #000000(50%) | #000000(70%) | 暗淡层 |
| shadow | #000000(15%) | #000000(30%) | 阴影 |

## 4. 组件使用指南

### 4.1 使用统一组件库

推荐使用 `CommonComponents.kt` 中的组件，自动应用主题配色：

```kotlin
// 卡片
AppCard {
    // 内容
}

// 带标题卡片
AppCardWithTitle(title = "标题") {
    // 内容
}

// 对话框
AppAlertDialog(
    title = "提示",
    message = "确定要删除吗？",
    onConfirm = { /* 确认 */ }
)

// 自定义内容对话框
AppCustomDialog(
    title = "设置",
    onConfirm = { /* 确认 */ }
) {
    // 自定义内容
}

// 设置项
AppSettingsItem(
    title = "标题",
    subtitle = "副标题"
)

// 设置分组
AppSettingsSection(title = "分组标题") {
    // 设置项
}

// 按钮
AppPrimaryButton(onClick = { }, text = "确认")
AppSecondaryButton(onClick = { }, text = "取消")

// 分隔线
AppDivider()
```

### 4.2 获取当前主题配置

```kotlin
@Composable
fun MyComponent() {
    val themeConfig = getCurrentThemeConfig()
    
    // 使用主题配置
    Surface(color = themeConfig.cardBackground) {
        Text(
            text = "内容",
            color = themeConfig.textPrimary
        )
    }
}
```

### 4.3 在对话框中使用

```kotlin
@Composable
fun MyDialog() {
    val themeConfig = getCurrentThemeConfig()
    
    AlertDialog(
        onDismissRequest = { /* 关闭 */ },
        containerColor = themeConfig.dialogBackground,
        titleContentColor = themeConfig.textPrimary,
        textContentColor = themeConfig.textSecondary,
        title = { Text("标题") },
        text = { Text("内容") },
        confirmButton = {
            Button(
                onClick = { /* 确认 */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeConfig.buttonPrimary,
                    contentColor = themeConfig.textOnPrimary
                )
            ) {
                Text("确认")
            }
        }
    )
}
```

## 5. 添加新主题模式

如需添加新主题模式（如"夜间护眼模式"），按以下步骤：

### 5.1 在 AppThemeConfig.kt 中添加配置函数

```kotlin
/**
 * 夜间护眼模式配置
 */
private fun nightEyeProtectionConfig() = defaultDarkConfig().copy(
    background = Color(0xFF0D1117),
    surface = Color(0xFF161B22),
    // 其他自定义颜色...
)
```

### 5.2 修改配置提供者

```kotlin
@Composable
fun getConfig(
    isFloatingBottomBar: Boolean = false,
    isDynamicColor: Boolean = true,
    isNightEyeProtection: Boolean = false,  // 新增参数
    customPrimaryColor: Color? = null
): AppThemeConfig {
    val isDark = isSystemInDarkTheme()
    
    return when {
        isNightEyeProtection -> nightEyeProtectionConfig()
        isFloatingBottomBar -> if (isDark) floatingBottomBarDarkConfig() else floatingBottomBarLightConfig()
        else -> if (isDark) defaultDarkConfig() else defaultLightConfig()
    }
}
```

## 6. 解决透明度问题

### 6.1 问题原因

之前使用 `Color.Transparent` 或低不透明度颜色，导致内容难以看清。

### 6.2 解决方案

1. 所有卡片/对话框使用不透明背景色
2. 使用 `AppCard`、`AppAlertDialog` 等统一组件
3. 需要透明效果时，使用 `themeConfig.overlay` 或 `themeConfig.scrim`

### 6.3 修复示例

**修复前**：
```kotlin
Surface(
    color = Color.White.copy(alpha = 0.7f)
) {
    // 内容难以看清
}
```

**修复后**：
```kotlin
AppCard {
    // 内容清晰可见
}
```

## 7. 主题颜色自定义

### 7.1 动态主题（Android 12+）

当 `isDynamicColor = true` 时，自动跟随系统壁纸颜色。

### 7.2 自定义主色

```kotlin
ThemeConfigProvider.getConfig(
    customPrimaryColor = Color(0xFF00BCD4)  // 自定义青色
)
```

### 7.3 预设主题

通过 `ThemeCustomization` 组件，用户可以创建和保存自定义主题预设。

## 8. 最佳实践

1. **始终使用统一组件**：优先使用 `AppCard`、`AppAlertDialog` 等组件
2. **避免硬编码颜色**：使用 `themeConfig.xxx` 而非 `Color.xxx`
3. **保持一致性**：同一页面使用相同的配色方案
4. **测试两种模式**：确保在浅色和深色模式下都正常显示
5. **考虑无障碍**：确保文字和背景有足够的对比度

## 9. 文件结构

```
ui/theme/
├── AppThemeConfig.kt      # 主题配置系统
├── Color.kt               # 基础颜色定义
├── Theme.kt               # Material3主题配置
├── Type.kt                # 字体排版
└── LiquidGlassTheme.kt   # 液态玻璃主题

ui/components/
├── CommonComponents.kt    # 统一组件库
├── ThemeCustomization.kt # 主题自定义组件
└── ...其他组件
```

## 10. 快速参考

### 获取主题配置
```kotlin
val themeConfig = getCurrentThemeConfig()
```

### 常用颜色
```kotlin
themeConfig.cardBackground      // 卡片背景
themeConfig.dialogBackground    // 对话框背景
themeConfig.textPrimary         // 主要文字
themeConfig.textSecondary       // 次要文字
themeConfig.buttonPrimary       // 主按钮
themeConfig.divider             // 分隔线
```

### 组件选择
- 需要卡片 → `AppCard` 或 `AppCardWithTitle`
- 需要对话框 → `AppAlertDialog` 或 `AppCustomDialog`
- 需要设置项 → `AppSettingsItem`
- 需要按钮 → `AppPrimaryButton` 或 `AppSecondaryButton`
