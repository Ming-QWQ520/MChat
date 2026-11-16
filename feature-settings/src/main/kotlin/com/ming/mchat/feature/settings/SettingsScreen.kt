package com.ming.mchat.feature.settings

import android.content.Intent
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
import com.ming.mchat.core.theme.LocalLiquidGlass
import com.ming.mchat.core.theme.LiquidGlassSurface
import java.io.File

/**
 * 完整设置页（重构版）：现代分组卡片 + 图标标题 + SegmentedButton 选择器。
 * 分组：外观 / 模型与 Provider / 提示词 / 会话与标题 / 附件 / 用量统计 / 安全与隐私 / 数据管理 / 关于。
 * 液态玻璃开启时分组卡片使用 LiquidGlassSurface。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val modelBehavior by viewModel.modelBehavior.collectAsStateWithLifecycle()
    val promptSafety by viewModel.promptSafety.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val attachment by viewModel.attachment.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val modelsByProvider by viewModel.modelsByProvider.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val apiKeyStatus by viewModel.apiKeyStatus.collectAsStateWithLifecycle()
    val usage by viewModel.usageSince30Days.collectAsStateWithLifecycle()
    val auxUsage by viewModel.auxUsage.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val glass = LocalLiquidGlass.current

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    val exportConvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let(viewModel::exportConversations) }
    val importConvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::importConversations) }
    val exportPresetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let(viewModel::exportPresets) }
    val importPresetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::importPresets) }
    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let(viewModel::setBackgroundImage) }

    var showDefaultModelPicker by remember { mutableStateOf(false) }
    var showPresetEditor by remember { mutableStateOf<PromptPresetEntity?>(null) }
    var showClearDataConfirm by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(2.dp))

            // ================= 1. 外观 =================
            SettingsGroupCard(title = "外观", icon = Icons.Filled.Palette, glassEnabled = glass.enabled) {
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

            // ================= 2. 模型与 Provider =================
            SettingsGroupCard(title = "模型与 Provider", icon = Icons.Filled.Memory, glassEnabled = glass.enabled) {
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
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    provider.displayName.take(1),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
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
                    options = listOf("低", "高", "最高"),
                    selectedIndex = modelBehavior.reasoningEffort.ordinal,
                ) { index ->
                    viewModel.setModelBehavior(modelBehavior.copy(reasoningEffort = ReasoningEffortLevel.entries[index]))
                }
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

            // ================= 3. 提示词 =================
            SettingsGroupCard(title = "提示词", icon = Icons.Filled.Notes, glassEnabled = glass.enabled) {
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

            // ================= 4. 会话与标题 =================
            SettingsGroupCard(title = "会话与标题", icon = Icons.Filled.Title, glassEnabled = glass.enabled) {
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

            // ================= 5. 附件 =================
            SettingsGroupCard(title = "附件", icon = Icons.Filled.AttachFile, glassEnabled = glass.enabled) {
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

            // ================= 6. 用量统计 =================
            SettingsGroupCard(title = "用量统计", icon = Icons.Filled.BarChart, glassEnabled = glass.enabled) {
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

            // ================= 7. 安全与隐私 =================
            SettingsGroupCard(title = "安全与隐私", icon = Icons.Filled.Security, glassEnabled = glass.enabled) {
                SettingCaption("API Key 使用 Android Keystore（AES/GCM）加密存储，数据库仅保存密文引用，绝不明文落盘。")
                SwitchRow("注入扫描（只提示不删除）", promptSafety.injectionScanEnabled) {
                    viewModel.setPromptSafety(promptSafety.copy(injectionScanEnabled = it))
                }
                SettingCaption("网络日志默认关闭；LLM 输出永远不会直接执行系统命令。")
                TextButton(onClick = { showClearDataConfirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp)); Text("一键清除本地会话数据")
                }
            }

            // ================= 8. 数据管理 =================
            SettingsGroupCard(title = "数据管理", icon = Icons.Filled.FolderOpen, glassEnabled = glass.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { exportConvLauncher.launch("mchat-conversations.json") }) { Text("导出会话") }
                    TextButton(onClick = { importConvLauncher.launch(arrayOf("application/json")) }) { Text("导入会话") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { exportPresetLauncher.launch("mchat-presets.json") }) { Text("导出预设") }
                    TextButton(onClick = viewModel::clearAttachmentCache) { Text("清除附件缓存") }
                }
            }

            // ================= 9. 关于 =================
            SettingsGroupCard(title = "关于", icon = Icons.Filled.Info, glassEnabled = glass.enabled) {
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
                SettingCaption("开源许可：Apache-2.0（openai-kotlin / Ktor / PdfBox-Android / JTokkit / Coil）与 MIT（Compose 生态相关项目）")
            }
            Spacer(Modifier.height(24.dp))
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

// ===================== 通用组件 =====================

/** 分组卡片：图标 + 标题 + 内容；液态玻璃开启时使用玻璃容器。 */
@Composable
private fun SettingsGroupCard(
    title: String,
    icon: ImageVector,
    glassEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    val header: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(30.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp)) {
            header()
            content()
        }
    }
    if (glassEnabled) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), content = body)
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            content = body,
        )
    }
}

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
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
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
            TemplateEngine.builtinContext("deepseek-4.1-flash"),
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
