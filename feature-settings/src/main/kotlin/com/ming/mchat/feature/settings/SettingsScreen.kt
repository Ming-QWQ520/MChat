@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ming.mchat.feature.settings

import android.content.Intent
import androidx.compose.animation.togetherWith
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ming.mchat.core.common.ChatBackground
import com.ming.mchat.core.common.GlassIntensity
import com.ming.mchat.core.common.OutputTokenMode
import com.ming.mchat.core.common.ReasoningEffortLevel
import com.ming.mchat.core.common.ThemeMode
import com.ming.mchat.core.common.TitleLanguage
import com.ming.mchat.core.data.datastore.AppearanceSettings
import com.ming.mchat.core.data.entity.PromptPresetEntity
import com.ming.mchat.core.llm.BalanceService
import com.ming.mchat.core.prompt.TemplateEngine
import com.ming.mchat.core.prompt.VariableDef
import com.ming.mchat.core.theme.IosDivider
import com.ming.mchat.core.theme.IosNavRow
import com.ming.mchat.core.theme.SectionCard
import com.ming.mchat.core.theme.SectionHeader
import com.ming.mchat.core.theme.appSurfaces
import java.io.File

/**
 * 设置主页（kelivo iOS 风格）：
 * - 分组卡片 + iOS 导航行（36dp 图标槽 / 15sp 标签 / chevron）；
 * - 二级页面内推（slide + fade，AnimatedContent），全部设置项分拆到子页；
 * - 液态玻璃开启时分组卡片使用 LiquidGlassSurface。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    var page by remember { mutableStateOf<String?>(null) }

    androidx.compose.animation.AnimatedContent(
        targetState = page,
        transitionSpec = {
            if (targetState != null) {
                (androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn()).togetherWith(
                    androidx.compose.animation.slideOutHorizontally { -it / 3 } + androidx.compose.animation.fadeOut(),
                )
            } else {
                (androidx.compose.animation.slideInHorizontally { -it / 3 } + androidx.compose.animation.fadeIn()).togetherWith(
                    androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut(),
                )
            }
        },
        label = "settingsPage",
    ) { current ->
        when (current) {
            "appearance" -> SettingsSubPage(title = "主题与外观", onBack = { page = null }) {
                AppearanceSettingsPage(viewModel)
            }
            "profile" -> SettingsSubPage(title = "用户资料", onBack = { page = null }) {
                ProfileSettingsPage(viewModel)
            }
            "chatDisplay" -> SettingsSubPage(title = "聊天显示", onBack = { page = null }) {
                ChatDisplaySettingsPage(viewModel)
            }
            "models" -> SettingsSubPage(title = "模型与 Provider", onBack = { page = null }) {
                ModelsSettingsPage(viewModel, onOpenProvider)
            }
            "title" -> SettingsSubPage(title = "会话与标题", onBack = { page = null }) {
                TitleSettingsPage(viewModel)
            }
            "attachment" -> SettingsSubPage(title = "附件", onBack = { page = null }) {
                AttachmentSettingsPage(viewModel)
            }
            "prompt" -> SettingsSubPage(title = "提示词预设", onBack = { page = null }) {
                PromptSettingsPage(viewModel)
            }
            "quickPhrases" -> SettingsSubPage(title = "快捷短语", onBack = { page = null }) {
                QuickPhrasesSettingsPage(viewModel)
            }
            "usage" -> SettingsSubPage(title = "用量统计", onBack = { page = null }) {
                UsageSettingsPage(viewModel)
            }
            "data" -> SettingsSubPage(title = "数据管理", onBack = { page = null }) {
                DataSettingsPage(viewModel)
            }
            "security" -> SettingsSubPage(title = "安全与隐私", onBack = { page = null }) {
                SecuritySettingsPage(viewModel)
            }
            "about" -> SettingsSubPage(title = "关于", onBack = { page = null }) {
                AboutSettingsPage()
            }
            else -> SettingsHomePage(onBack = onBack, viewModel = viewModel) { page = it }
        }
    }
}

/** 设置主页：iOS 分组导航。 */
@Composable
private fun SettingsHomePage(
    onBack: () -> Unit,
    viewModel: SettingsViewModel,
    onOpenPage: (String) -> Unit,
) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val modelBehavior by viewModel.modelBehavior.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val apiKeyStatus by viewModel.apiKeyStatus.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val themeLabel = when (appearance.themeMode) {
        ThemeMode.SYSTEM -> "跟随系统"
        ThemeMode.LIGHT -> "浅色"
        ThemeMode.DARK -> "深色"
        ThemeMode.DYNAMIC -> "动态取色"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(2.dp))

            SectionHeader("通用", first = true)
            SectionCard {
                IosNavRow(Icons.Filled.Palette, "主题与外观", detailText = themeLabel) { onOpenPage("appearance") }
                IosDivider()
                IosNavRow(Icons.Filled.Person, "用户资料", detailText = userProfile.userName.ifBlank { "未设置" }) { onOpenPage("profile") }
                IosDivider()
                IosNavRow(Icons.Filled.ChatBubbleOutline, "聊天显示", detailText = "气泡 / 时间戳 / 滚动按钮") { onOpenPage("chatDisplay") }
            }

            SectionHeader("模型与聊天")
            SectionCard {
                IosNavRow(Icons.Filled.Memory, "模型与 Provider", detailText = "${providers.count { it.enabled }} 个可用") { onOpenPage("models") }
                IosDivider()
                IosNavRow(Icons.Filled.Title, "会话与标题", detailText = if (modelBehavior.reasoningEffort == ReasoningEffortLevel.NONE) "思考关闭" else "思考开启") { onOpenPage("title") }
                IosDivider()
                IosNavRow(Icons.Filled.AttachFile, "附件") { onOpenPage("attachment") }
            }

            SectionHeader("效率")
            SectionCard {
                IosNavRow(Icons.Filled.Notes, "提示词预设") { onOpenPage("prompt") }
                IosDivider()
                IosNavRow(Icons.Filled.Bolt, "快捷短语", detailText = "输入栏闪电按钮") { onOpenPage("quickPhrases") }
            }

            SectionHeader("数据")
            SectionCard {
                IosNavRow(Icons.Filled.BarChart, "用量统计") { onOpenPage("usage") }
                IosDivider()
                IosNavRow(Icons.Filled.FolderOpen, "数据管理") { onOpenPage("data") }
                IosDivider()
                IosNavRow(Icons.Filled.Security, "安全与隐私") { onOpenPage("security") }
            }

            SectionHeader("关于")
            SectionCard {
                IosNavRow(Icons.Filled.Info, "关于 MChat", detailText = "0.1.0") { onOpenPage("about") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 二级页面骨架：返回 + 标题 + 可滚动内容。 */
@Composable
private fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 外观设置页。 */
@Composable
private fun AppearanceSettingsPage(viewModel: SettingsViewModel) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let(viewModel::setBackgroundImage) }

    SectionCard {
        Column(Modifier.padding(16.dp)) {
            LabeledSegmentedRow(
                label = "主题模式",
                options = listOf("跟随系统", "浅色", "深色", "动态取色"),
                selectedIndex = appearance.themeMode.ordinal,
                optionEnabled = { index -> index != ThemeMode.DYNAMIC.ordinal || android.os.Build.VERSION.SDK_INT >= 31 },
            ) { index ->
                val mode = ThemeMode.entries[index]
                if (mode != ThemeMode.DYNAMIC || android.os.Build.VERSION.SDK_INT >= 31) {
                    viewModel.setAppearance(appearance.copy(themeMode = mode))
                }
            }
            SettingCaption("Android 12+ 动态取色跟随壁纸")
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            SwitchRow("液态玻璃", appearance.liquidGlassEnabled) {
                viewModel.setAppearance(appearance.copy(liquidGlassEnabled = it))
            }
            if (appearance.liquidGlassEnabled) {
                LabeledSegmentedRow(
                    label = "玻璃强度",
                    options = listOf("低", "中", "高"),
                    selectedIndex = appearance.liquidGlassIntensity.ordinal,
                ) { index ->
                    viewModel.setAppearance(appearance.copy(liquidGlassIntensity = GlassIntensity.entries[index]))
                }
                SettingCaption("iOS 26 风格：vibrancy + 模糊 + 折射透镜（参考 Fluxhub / AndroidLiquidGlass）")
            }
            SwitchRow("减少动画（尊重系统无障碍）", appearance.reduceMotion) {
                viewModel.setAppearance(appearance.copy(reduceMotion = it))
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            SliderRow(
                label = "字体大小",
                value = appearance.fontScale,
                valueRange = 0.85f..1.3f,
                format = { "${(it * 100).toInt()}%" },
            ) {
                viewModel.setAppearance(appearance.copy(fontScale = it))
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            LabeledSegmentedRow(
                label = "聊天背景",
                options = listOf("纯色", "渐变", "图片"),
                selectedIndex = appearance.chatBackground.ordinal,
            ) { index ->
                viewModel.setAppearance(appearance.copy(chatBackground = ChatBackground.entries[index]))
            }
            if (appearance.chatBackground == ChatBackground.IMAGE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                ) {
                    val bgFile = File(appearance.backgroundImageUri)
                    if (appearance.backgroundImageUri.isNotBlank() && bgFile.exists()) {
                        AsyncImage(
                            model = bgFile,
                            contentDescription = "背景预览",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(14.dp)),
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("未设置", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        TextButton(onClick = {
                            backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text("选择图片") }
                        if (appearance.backgroundImageUri.isNotBlank()) {
                            TextButton(onClick = viewModel::clearBackgroundImage) { Text("清除图片") }
                        }
                    }
                }
            }
        }
    }
}

/** 用户资料页（kelivo user profile）：用户名 + 头像 emoji。 */
@Composable
private fun ProfileSettingsPage(viewModel: SettingsViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val emojis = listOf("🦉", "🐼", "🦊", "🐧", "🐳", "🍀", "⭐️", "🌙", "🤖", "🎨", "📚", "🧭")
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            var name by remember(profile.userName) { mutableStateOf(profile.userName) }
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    viewModel.setUserProfile(profile.copy(userName = it))
                },
                label = { Text("用户名（展示在抽屉与消息头部）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("头像", style = MaterialTheme.typography.bodyMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                emojis.forEach { emoji ->
                    Surface(
                        onClick = { viewModel.setUserProfile(profile.copy(avatarEmoji = emoji)) },
                        shape = CircleShape,
                        color = if (profile.avatarEmoji == emoji) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.appSurfaces.surfaceFill,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp))
                        }
                    }
                }
            }
        }
    }
}

/** 聊天显示页：气泡风格 / 时间戳 / 滚动导航按钮（kelivo message_style_settings）。 */
@Composable
private fun ChatDisplaySettingsPage(viewModel: SettingsViewModel) {
    val display by viewModel.chatDisplay.collectAsStateWithLifecycle()
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            LabeledSegmentedRow(
                label = "聊天气泡风格",
                options = listOf("磨砂", "实底"),
                selectedIndex = display.bubbleStyle.ordinal,
            ) { index ->
                viewModel.setChatDisplay(display.copy(bubbleStyle = com.ming.mchat.core.data.datastore.BubbleStyle.entries[index]))
            }
            SettingCaption("磨砂：半透明 + 发丝描边（kelivo frosted）；实底：不透明卡片")
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            SwitchRow("显示消息时间戳", display.showTimestamp) {
                viewModel.setChatDisplay(display.copy(showTimestamp = it))
            }
            SwitchRow("滚动导航按钮（回到底部 / 上一条提问）", display.showMessageNavButtons) {
                viewModel.setChatDisplay(display.copy(showMessageNavButtons = it))
            }
        }
    }
}

/** 模型与 Provider 页。 */
@Composable
private fun ModelsSettingsPage(viewModel: SettingsViewModel, onOpenProvider: (String) -> Unit) {
    val modelBehavior by viewModel.modelBehavior.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val modelsByProvider by viewModel.modelsByProvider.collectAsStateWithLifecycle()
    val apiKeyStatus by viewModel.apiKeyStatus.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    var showDefaultModelPicker by remember { mutableStateOf(false) }

    SectionCard {
        Column(Modifier.padding(16.dp)) {
            providers.forEach { provider ->
                val hasKey = apiKeyStatus[provider.id] == true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenProvider(provider.id) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (provider.id == BalanceService.DEEPSEEK_ID) MaterialTheme.colorScheme.surfaceContainerHighest
                        else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (provider.id == BalanceService.DEEPSEEK_ID) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(
                                        com.ming.mchat.core.theme.R.drawable.deepseek_logo,
                                    ),
                                    contentDescription = "DeepSeek logo",
                                    modifier = Modifier.size(30.dp),
                                )
                            } else {
                                Text(
                                    provider.displayName.take(1),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(provider.displayName, style = MaterialTheme.typography.bodyLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hasKey) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (hasKey) "API Key 已配置（Keystore 加密）" else "API Key 未配置",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = provider.enabled,
                        onCheckedChange = { viewModel.setProviderEnabled(provider.id, it) },
                    )
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(
                onClick = { onOpenProvider("new") },
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加供应商（Base URL / API Key / 模型）")
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            ListItem(
                headlineContent = { Text("默认模型") },
                supportingContent = { Text("${modelBehavior.defaultProviderId} / ${modelBehavior.defaultModelId}") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showDefaultModelPicker = true },
            )
            SliderRow(
                label = "全局 temperature",
                value = modelBehavior.globalTemperature.toFloat(),
                valueRange = 0f..2f,
                format = { "%.2f".format(it) },
            ) {
                viewModel.setModelBehavior(modelBehavior.copy(globalTemperature = it.toDouble()))
            }
            LabeledSegmentedRow(
                label = "思考强度（聊天页输入栏也可调）",
                options = listOf("关闭", "低", "高", "最高"),
                selectedIndex = modelBehavior.reasoningEffort.ordinal,
            ) { index ->
                viewModel.setModelBehavior(modelBehavior.copy(reasoningEffort = ReasoningEffortLevel.entries[index]))
            }
            SwitchRow("思考过程默认折叠", modelBehavior.collapseReasoning) {
                viewModel.setModelBehavior(modelBehavior.copy(collapseReasoning = it))
            }
            SettingCaption("折叠时显示思考内容摘要，点击可展开（animateContentSize 动画）；关闭后始终展开")
            LabeledSegmentedRow(
                label = "输出 Token 上限",
                options = listOf("默认", "手动", "自动"),
                selectedIndex = modelBehavior.outputTokenMode.ordinal,
            ) { index ->
                viewModel.setModelBehavior(modelBehavior.copy(outputTokenMode = OutputTokenMode.entries[index]))
            }
            SettingCaption(
                when (modelBehavior.outputTokenMode) {
                    OutputTokenMode.DEFAULT -> "默认不设限（不发送 max_tokens，推荐）"
                    OutputTokenMode.MANUAL -> "手动指定 ${modelBehavior.outputTokenManualValue} tokens"
                    OutputTokenMode.AUTO -> "自动安全上限（上下文窗口的 50%）"
                }
            )
            if (modelBehavior.outputTokenMode == OutputTokenMode.MANUAL) {
                var manual by remember(modelBehavior.outputTokenManualValue) {
                    mutableStateOf(modelBehavior.outputTokenManualValue.toString())
                }
                OutlinedTextField(
                    value = manual,
                    onValueChange = {
                        manual = it
                        it.toIntOrNull()?.let { v ->
                            viewModel.setModelBehavior(modelBehavior.copy(outputTokenManualValue = v.coerceIn(64, 131072)))
                        }
                    },
                    label = { Text("手动输出上限（tokens）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            DeepSeekBalanceCard(
                balance = balance,
                threshold = modelBehavior.lowBalanceThreshold,
                onRefresh = viewModel::refreshBalance,
                onThresholdChange = {
                    viewModel.setModelBehavior(modelBehavior.copy(lowBalanceThreshold = it))
                },
            )
        }
    }

    if (showDefaultModelPicker) {
        DefaultModelPickerDialog(
            providers = providers,
            modelsByProvider = modelsByProvider,
            currentProviderId = modelBehavior.defaultProviderId,
            currentModelId = modelBehavior.defaultModelId,
            onSelect = viewModel::setDefaultModel,
            onDismiss = { showDefaultModelPicker = false },
        )
    }
}

/** 会话与标题页。 */
@Composable
private fun TitleSettingsPage(viewModel: SettingsViewModel) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            SwitchRow("AI 生成标题", title.titleGenEnabled) {
                viewModel.setTitle(title.copy(titleGenEnabled = it))
            }
            SettingCaption(
                if (title.titleGenModelId.isBlank()) "标题模型：自动（选最便宜的非推理模型）"
                else "标题模型：${title.titleGenProviderId} / ${title.titleGenModelId}"
            )
            LabeledSegmentedRow(
                label = "标题语言",
                options = listOf("自动", "中文", "英文"),
                selectedIndex = title.titleGenLanguage.ordinal,
            ) { index ->
                viewModel.setTitle(title.copy(titleGenLanguage = TitleLanguage.entries[index]))
            }
            TextButton(onClick = viewModel::regenerateAllTitles) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp)); Text("重新生成所有自动标题")
            }
        }
    }
}

/** 附件页。 */
@Composable
private fun AttachmentSettingsPage(viewModel: SettingsViewModel) {
    val attachment by viewModel.attachment.collectAsStateWithLifecycle()
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            LabeledSegmentedRow(
                label = "图片最大长边",
                options = listOf("1024", "1568", "2048"),
                selectedIndex = listOf(1024, 1568, 2048).indexOf(attachment.imageMaxLongEdge).coerceAtLeast(0),
            ) { index ->
                viewModel.setAttachment(attachment.copy(imageMaxLongEdge = listOf(1024, 1568, 2048)[index]))
            }
            SliderRow(
                label = "JPEG 质量",
                value = attachment.jpegQuality.toFloat(),
                valueRange = 40f..100f,
                format = { "${it.toInt()}" },
            ) {
                viewModel.setAttachment(attachment.copy(jpegQuality = it.toInt()))
            }
            LabeledSegmentedRow(
                label = "单图大小上限",
                options = listOf("2MB", "4MB", "8MB"),
                selectedIndex = listOf(2L, 4L, 8L).indexOf(attachment.imageMaxBytes / (1024 * 1024)).coerceAtLeast(0),
            ) { index ->
                viewModel.setAttachment(attachment.copy(imageMaxBytes = listOf(2L, 4L, 8L)[index] * 1024 * 1024))
            }
            var pdfMax by remember(attachment.pdfMaxChars) { mutableStateOf(attachment.pdfMaxChars.toString()) }
            OutlinedTextField(
                value = pdfMax,
                onValueChange = {
                    pdfMax = it
                    it.toIntOrNull()?.let { v ->
                        viewModel.setAttachment(attachment.copy(pdfMaxChars = v.coerceIn(1000, 500000)))
                    }
                },
                label = { Text("PDF 字数上限") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow("自动剥离 EXIF", attachment.stripExif) {
                viewModel.setAttachment(attachment.copy(stripExif = it))
            }
            TextButton(onClick = viewModel::clearAttachmentCache) { Text("清理附件缓存") }
        }
    }
}

/** 提示词预设页。 */
@Composable
private fun PromptSettingsPage(viewModel: SettingsViewModel) {
    val promptSafety by viewModel.promptSafety.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    var showPresetEditor by remember { mutableStateOf<PromptPresetEntity?>(null) }
    val exportPresetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let(viewModel::exportPresets) }
    val importPresetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::importPresets) }

    SectionCard {
        Column(Modifier.padding(16.dp)) {
            if (promptSafety.defaultPresetId > 0) {
                SettingCaption("全局默认预设：${presets.firstOrNull { it.id == promptSafety.defaultPresetId }?.name ?: "未知"}")
            }
            presets.forEach { preset ->
                val isDefault = promptSafety.defaultPresetId == preset.id
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewModel.setDefaultPreset(preset.id) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            preset.name + if (isDefault) "（默认）" else "",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (preset.description.isNotBlank()) {
                            Text(
                                preset.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    IconButton(onClick = { showPresetEditor = preset }) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { viewModel.deletePreset(preset.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { showPresetEditor = PromptPresetEntity(id = 0L, name = "", systemPrompt = "") }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)); Text("新建")
                }
                TextButton(onClick = { exportPresetLauncher.launch("mchat-presets.json") }) { Text("导出") }
                TextButton(onClick = { importPresetLauncher.launch(arrayOf("application/json")) }) { Text("导入") }
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            SwitchRow("追加全局 system prompt", promptSafety.appendGlobalSystemPrompt) {
                viewModel.setPromptSafety(promptSafety.copy(appendGlobalSystemPrompt = it))
            }
            var globalPrompt by remember(promptSafety.globalSystemPrompt) { mutableStateOf(promptSafety.globalSystemPrompt) }
            OutlinedTextField(
                value = globalPrompt,
                onValueChange = {
                    globalPrompt = it
                    viewModel.setPromptSafety(promptSafety.copy(globalSystemPrompt = it))
                },
                label = { Text("全局 system prompt") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow("附件安全声明（默认开）", promptSafety.attachmentSafetyNoticeEnabled) {
                viewModel.setPromptSafety(promptSafety.copy(attachmentSafetyNoticeEnabled = it))
            }
        }
    }

    showPresetEditor?.let { preset ->
        PresetEditorDialog(
            preset = preset,
            onDismiss = { showPresetEditor = null },
            onSave = {
                viewModel.savePreset(it)
                showPresetEditor = null
            },
        )
    }
}

/** 快捷短语页（kelivo quick phrases）：内置 + 自定义管理。 */
@Composable
private fun QuickPhrasesSettingsPage(viewModel: SettingsViewModel) {
    val customPhrases by viewModel.customQuickPhrases.collectAsStateWithLifecycle()
    var newPhrase by remember { mutableStateOf("") }
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("内置短语（不可删除）", style = MaterialTheme.typography.labelLarge)
            Text(
                com.ming.mchat.core.common.BuiltinQuickPhrases.ALL.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("自定义短语", style = MaterialTheme.typography.labelLarge)
            customPhrases.forEach { phrase ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(phrase, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.removeQuickPhrase(phrase) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newPhrase,
                    onValueChange = { newPhrase = it },
                    placeholder = { Text("新短语") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        viewModel.addQuickPhrase(newPhrase)
                        newPhrase = ""
                    },
                    enabled = newPhrase.isNotBlank(),
                ) { Text("添加") }
            }
            SettingCaption("输入栏闪电按钮展开短语行，点击填入输入框")
        }
    }
}

/** 用量统计页。 */
@Composable
private fun UsageSettingsPage(viewModel: SettingsViewModel) {
    val usage by viewModel.usageSince30Days.collectAsStateWithLifecycle()
    val auxUsage by viewModel.auxUsage.collectAsStateWithLifecycle()
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            if (usage.isEmpty()) {
                SettingCaption("近 30 天暂无记录")
            } else {
                usage.take(12).forEach { row ->
                    ListItem(
                        headlineContent = { Text("${row.day} · ${row.model}") },
                        supportingContent = { Text("输入 ${row.promptTokens} · 输出 ${row.completionTokens} · ${row.requestCount} 次") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            val auxTotal = auxUsage.sumOf { it.totalTokens }
            SettingCaption("辅助消耗（标题/测试连接）：$auxTotal tokens · ${auxUsage.size} 次")
            TextButton(onClick = viewModel::clearUsageStats) { Text("清空统计") }
        }
    }
}

/** 数据管理页。 */
@Composable
private fun DataSettingsPage(viewModel: SettingsViewModel) {
    val exportConvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let(viewModel::exportConversations) }
    val importConvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::importConversations) }
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { exportConvLauncher.launch("mchat-conversations.json") }) { Text("导出会话") }
                TextButton(onClick = { importConvLauncher.launch(arrayOf("application/json")) }) { Text("导入会话") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = viewModel::clearAttachmentCache) { Text("清除附件缓存") }
            }
        }
    }
}

/** 安全与隐私页。 */
@Composable
private fun SecuritySettingsPage(viewModel: SettingsViewModel) {
    val promptSafety by viewModel.promptSafety.collectAsStateWithLifecycle()
    var showClearDataConfirm by remember { mutableStateOf(false) }
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            SettingCaption("API Key 使用 Android Keystore（AES/GCM）加密存储，数据库仅保存密文引用，绝不明文落盘。")
            SwitchRow("注入扫描（只提示不删除）", promptSafety.injectionScanEnabled) {
                viewModel.setPromptSafety(promptSafety.copy(injectionScanEnabled = it))
            }
            SettingCaption("网络日志默认关闭（openai-kotlin LogLevel.None）；LLM 输出永远不会直接执行系统命令。")
            TextButton(onClick = { showClearDataConfirm = true }) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp)); Text("一键清除本地会话数据")
            }
        }
    }
    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("清除本地数据？") },
            text = { Text("将删除全部会话、消息、附件与统计（设置与 API Key 保留），不可恢复。") },
            confirmButton = {
                TextButton(onClick = { showClearDataConfirm = false; viewModel.clearAllLocalData() }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { showClearDataConfirm = false }) { Text("取消") } },
        )
    }
}

/** 关于页。 */
@Composable
private fun AboutSettingsPage() {
    val context = LocalContext.current
    SectionCard {
        Column(Modifier.padding(16.dp)) {
            ListItem(
                headlineContent = { Text("版本") },
                supportingContent = { Text("0.1.0（versionCode 1，全程锁定）") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text("包名") },
                supportingContent = { Text("com.ming.mchat") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            ListItem(
                headlineContent = { Text("GitHub 仓库") },
                supportingContent = { Text("Ming-QWQ520/MChat") },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/Ming-QWQ520/MChat".toUri()))
                        }
                    },
            )
            SettingCaption("隐私说明：所有数据仅保存在本机；API Key 加密存储；不经用户允许不发送任何遥测。")
            SettingCaption("开源许可：Apache-2.0（openai-kotlin / Ktor / AndroidLiquidGlass(backdrop) / PdfBox-Android / JTokkit）与 MIT（Compose 生态相关项目）")
            SettingCaption("UI 参考致谢：kelivo（设置 / 对话 / 侧边栏交互）、Fluxhub（液态玻璃）")
        }
    }
}

// ===================== 通用组件 =====================

/** 开关行。 */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 滑块行：label + 数值 + Slider。 */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column {
        Text("$label：${format(value)}", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = valueRange)
    }
}

/** 说明文字。 */
@Composable
private fun SettingCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

/** 单选分段按钮行。 */
@Composable
private fun LabeledSegmentedRow(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    optionEnabled: (Int) -> Boolean = { true },
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    enabled = optionEnabled(index),
                    label = {
                        Text(
                            option,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }
    }
}

/** DeepSeek 余额卡片：大字余额 + 明细 + 手动刷新 + 低余额阈值。 */
@Composable
private fun DeepSeekBalanceCard(
    balance: com.ming.mchat.core.llm.BalanceState?,
    threshold: Double,
    onRefresh: () -> Unit,
    onThresholdChange: (Double) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(
                        com.ming.mchat.core.theme.R.drawable.deepseek_logo,
                    ),
                    contentDescription = "DeepSeek logo",
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("DeepSeek 余额", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp)); Text("刷新")
                }
            }
            when {
                balance == null -> SettingCaption("尚未查询（使用 DeepSeek 时自动查询）")
                balance.error != null -> Text(
                    balance.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            balance.totalBalance.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            balance.currency,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                    if (!balance.isAvailable) {
                        Text("账户当前不可用", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                    SettingCaption("赠送 ${balance.grantedBalance} · 充值 ${balance.toppedUpBalance} ${balance.currency}")
                    balance.updatedAt?.let {
                        SettingCaption(
                            "更新于 " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))
                        )
                    }
                }
            }
            var thresholdText by remember(threshold) { mutableStateOf(threshold.toString()) }
            OutlinedTextField(
                value = thresholdText,
                onValueChange = {
                    thresholdText = it
                    it.toDoubleOrNull()?.let { v -> onThresholdChange(v) }
                },
                label = { Text("低余额提醒阈值（默认 5）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

// ===================== 弹窗 =====================

@Composable
private fun DefaultModelPickerDialog(
    providers: List<com.ming.mchat.core.data.entity.ProviderConfigEntity>,
    modelsByProvider: Map<String, List<com.ming.mchat.core.data.entity.ModelConfigEntity>>,
    currentProviderId: String,
    currentModelId: String,
    onSelect: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择默认模型") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                providers.filter { it.enabled }.forEach { provider ->
                    val models = modelsByProvider[provider.id].orEmpty().filter { it.enabled }
                    if (models.isEmpty()) return@forEach
                    Text(
                        provider.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    models.forEach { model ->
                        val selected = provider.id == currentProviderId && model.modelId == currentModelId
                        ListItem(
                            headlineContent = { Text(model.displayName) },
                            supportingContent = {
                                val tags = buildList {
                                    add("上下文 ${model.contextTokens / 1024}K")
                                    if (model.supportsVision) add("视觉")
                                    if (model.supportsReasoning) add("推理")
                                }.joinToString(" · ")
                                Text(tags, style = MaterialTheme.typography.labelSmall)
                            },
                            trailingContent = {
                                if (selected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                onSelect(provider.id, model.modelId)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PresetEditorDialog(
    preset: PromptPresetEntity,
    onDismiss: () -> Unit,
    onSave: (PromptPresetEntity) -> Unit,
) {
    var name by remember { mutableStateOf(preset.name) }
    var description by remember { mutableStateOf(preset.description) }
    var systemPrompt by remember { mutableStateOf(preset.systemPrompt) }
    var variablesJson by remember { mutableStateOf(preset.variablesJson) }
    val variables = remember(variablesJson) { VariableDef.parseList(variablesJson) }
    val preview = remember(systemPrompt, variables) {
        TemplateEngine().render(
            systemPrompt,
            variables.associate { it.key to it.defaultValue },
            TemplateEngine.builtinContext("deepseek-flash"),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (preset.id == 0L) "新建预设" else "编辑预设") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("描述") }, singleLine = true)
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt（支持 {{var}}）") },
                    minLines = 3,
                )
                OutlinedTextField(
                    value = variablesJson,
                    onValueChange = { variablesJson = it },
                    label = { Text("变量定义 JSON") },
                    minLines = 2,
                )
                if (variables.isNotEmpty()) {
                    Text("变量：${variables.joinToString { it.key }}", style = MaterialTheme.typography.labelSmall)
                }
                Text("实时预览：\n$preview", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                        onSave(preset.copy(name = name, description = description, systemPrompt = systemPrompt, variablesJson = variablesJson, updatedAt = System.currentTimeMillis()))
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
