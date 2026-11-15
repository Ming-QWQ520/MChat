package com.ming.mchat.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.core.net.toUri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

/**
 * 完整设置页：外观 / 模型与 Provider / 提示词 / 会话与标题 / 附件 /
 * 用量统计 / 安全与隐私 / 数据管理 / 关于。
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ================= 1. 外观 =================
            SettingsGroup(title = "外观", glassEnabled = glass.enabled) {
                ThemeModeRow(appearance.themeMode) { mode ->
                    if (mode != ThemeMode.DYNAMIC || android.os.Build.VERSION.SDK_INT >= 31) {
                        viewModel.setAppearance(appearance.copy(themeMode = mode))
                    }
                }
                SwitchRow("液态玻璃（默认关闭）", appearance.liquidGlassEnabled) {
                    viewModel.setAppearance(appearance.copy(liquidGlassEnabled = it))
                }
                if (appearance.liquidGlassEnabled) {
                    ChipRow(
                        label = "玻璃强度",
                        options = listOf("低", "中", "高"),
                        selected = appearance.liquidGlassIntensity.ordinal,
                    ) { index ->
                        viewModel.setAppearance(
                            appearance.copy(liquidGlassIntensity = GlassIntensity.entries[index])
                        )
                    }
                }
                SwitchRow("减少动画（尊重系统无障碍设置）", appearance.reduceMotion) {
                    viewModel.setAppearance(appearance.copy(reduceMotion = it))
                }
                Text("字体大小：${"%.0f%%".format(appearance.fontScale * 100)}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = appearance.fontScale,
                    onValueChange = { viewModel.setAppearance(appearance.copy(fontScale = it)) },
                    valueRange = 0.85f..1.3f,
                )
                ChipRow("聊天背景", listOf("纯色", "主题渐变"), appearance.chatBackground.ordinal) { index ->
                    viewModel.setAppearance(appearance.copy(chatBackground = ChatBackground.entries[index]))
                }
            }

            // ================= 2. 模型与 Provider =================
            SettingsGroup(title = "模型与 Provider", glassEnabled = glass.enabled) {
                providers.forEach { provider ->
                    val hasKey = apiKeyStatus[provider.id] == true
                    ListItem(
                        headlineContent = { Text(provider.displayName) },
                        supportingContent = {
                            Text(
                                if (hasKey) "API Key：已配置（Keystore 加密）" else "API Key：未配置",
                                color = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        },
                        trailingContent = {
                            Switch(checked = provider.enabled, onCheckedChange = { viewModel.setProviderEnabled(provider.id, it) })
                        },
                        leadingContent = { Text(provider.displayName.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { onOpenProvider(provider.id) },
                    )
                }
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("默认模型") },
                    supportingContent = { Text("${modelBehavior.defaultProviderId} / ${modelBehavior.defaultModelId}") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable { showDefaultModelPicker = true },
                )
                Text("全局 temperature：%.2f".format(modelBehavior.globalTemperature), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = modelBehavior.globalTemperature.toFloat(),
                    onValueChange = { viewModel.setModelBehavior(modelBehavior.copy(globalTemperature = it.toDouble())) },
                    valueRange = 0f..2f,
                )
                ChipRow(
                    "思考强度（默认最高）",
                    listOf("低", "高", "最高"),
                    modelBehavior.reasoningEffort.ordinal,
                ) { index ->
                    viewModel.setModelBehavior(modelBehavior.copy(reasoningEffort = ReasoningEffortLevel.entries[index]))
                }
                Text(
                    when (modelBehavior.outputTokenMode) {
                        OutputTokenMode.DEFAULT -> "输出上限：不设限（推荐，不发送 max_tokens）"
                        OutputTokenMode.MANUAL -> "输出上限：手动指定 ${modelBehavior.outputTokenManualValue} tokens"
                        OutputTokenMode.AUTO -> "输出上限：自动（上下文窗口的 50%）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipRow(
                    "输出 Token 上限",
                    listOf("默认", "手动", "自动"),
                    modelBehavior.outputTokenMode.ordinal,
                ) { index ->
                    viewModel.setModelBehavior(modelBehavior.copy(outputTokenMode = OutputTokenMode.entries[index]))
                }
                if (modelBehavior.outputTokenMode == OutputTokenMode.MANUAL) {
                    var manual by remember(modelBehavior.outputTokenManualValue) {
                        mutableStateOf(modelBehavior.outputTokenManualValue.toString())
                    }
                    OutlinedTextField(
                        value = manual,
                        onValueChange = {
                            manual = it
                            it.toIntOrNull()?.let { v -> viewModel.setModelBehavior(modelBehavior.copy(outputTokenManualValue = v.coerceIn(64, 131072))) }
                        },
                        label = { Text("手动输出上限（tokens）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // DeepSeek 余额卡片
                Text("DeepSeek 余额", style = MaterialTheme.typography.titleSmall)
                if (balance == null) {
                    Text("尚未查询（使用 DeepSeek 时自动查询）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (balance?.error != null) {
                    Text(balance?.error ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    balance?.let { b ->
                        Column {
                            Text("可用：${if (b.isAvailable) "是" else "否（账户当前不可用）"}")
                            Text("总额：${b.totalBalance} ${b.currency}（赠送 ${b.grantedBalance} / 充值 ${b.toppedUpBalance}）")
                            b.updatedAt?.let {
                                Text("更新于 ${java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                TextButton(onClick = viewModel::refreshBalance) { Text("手动刷新余额") }
                var threshold by remember(modelBehavior.lowBalanceThreshold) { mutableStateOf(modelBehavior.lowBalanceThreshold.toString()) }
                OutlinedTextField(
                    value = threshold,
                    onValueChange = {
                        threshold = it
                        it.toDoubleOrNull()?.let { v -> viewModel.setModelBehavior(modelBehavior.copy(lowBalanceThreshold = v)) }
                    },
                    label = { Text("低余额提醒阈值（默认 5）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ================= 3. 提示词 =================
            SettingsGroup(title = "提示词", glassEnabled = glass.enabled) {
                if (promptSafety.defaultPresetId > 0) {
                    Text(
                        "全局默认预设：${presets.firstOrNull { it.id == promptSafety.defaultPresetId }?.name ?: "未知"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                presets.forEach { preset ->
                    val isDefault = promptSafety.defaultPresetId == preset.id
                    ListItem(
                        headlineContent = { Text(preset.name + if (isDefault) "（默认）" else "") },
                        supportingContent = { Text(preset.description, maxLines = 1) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { showPresetEditor = preset }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "编辑", modifier = Modifier.width(20.dp))
                                }
                                IconButton(onClick = { viewModel.deletePreset(preset.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "删除", modifier = Modifier.width(20.dp))
                                }
                            }
                        },
                        modifier = Modifier.clickable { viewModel.setDefaultPreset(preset.id) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showPresetEditor = PromptPresetEntity(id = 0L, name = "", systemPrompt = "") }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.width(18.dp)); Text("新建")
                    }
                    TextButton(onClick = { exportPresetLauncher.launch("mchat-presets.json") }) { Text("导出") }
                    TextButton(onClick = { importPresetLauncher.launch(arrayOf("application/json")) }) { Text("导入") }
                }
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
            SettingsGroup(title = "会话与标题", glassEnabled = glass.enabled) {
                SwitchRow("AI 生成标题", title.titleGenEnabled) {
                    viewModel.setTitle(title.copy(titleGenEnabled = it))
                }
                Text(
                    if (title.titleGenModelId.isBlank()) "标题模型：自动（选最便宜的非推理模型）" else "标题模型：${title.titleGenProviderId} / ${title.titleGenModelId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipRow("标题语言", listOf("自动", "中文", "英文"), title.titleGenLanguage.ordinal) { index ->
                    viewModel.setTitle(title.copy(titleGenLanguage = TitleLanguage.entries[index]))
                }
                TextButton(onClick = viewModel::regenerateAllTitles) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.width(18.dp)); Text("重新生成所有自动标题")
                }
            }

            // ================= 5. 附件 =================
            SettingsGroup(title = "附件", glassEnabled = glass.enabled) {
                ChipRow("图片最大长边", listOf("1024", "1568", "2048"), listOf(1024, 1568, 2048).indexOf(attachment.imageMaxLongEdge)) { index ->
                    viewModel.setAttachment(attachment.copy(imageMaxLongEdge = listOf(1024, 1568, 2048)[index]))
                }
                Text("JPEG 质量：${attachment.jpegQuality}", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = attachment.jpegQuality.toFloat(),
                    onValueChange = { viewModel.setAttachment(attachment.copy(jpegQuality = it.toInt())) },
                    valueRange = 40f..100f,
                )
                ChipRow("单图大小上限", listOf("2MB", "4MB", "8MB"), listOf(2L, 4L, 8L).indexOf(attachment.imageMaxBytes / (1024 * 1024))) { index ->
                    viewModel.setAttachment(attachment.copy(imageMaxBytes = listOf(2L, 4L, 8L)[index] * 1024 * 1024))
                }
                var pdfMax by remember(attachment.pdfMaxChars) { mutableStateOf(attachment.pdfMaxChars.toString()) }
                OutlinedTextField(
                    value = pdfMax,
                    onValueChange = {
                        pdfMax = it
                        it.toIntOrNull()?.let { v -> viewModel.setAttachment(attachment.copy(pdfMaxChars = v.coerceIn(1000, 500000))) }
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
            SettingsGroup(title = "用量统计", glassEnabled = glass.enabled) {
                if (usage.isEmpty()) {
                    Text("近 30 天暂无记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    usage.take(12).forEach { row ->
                        ListItem(
                            headlineContent = { Text("${row.day} · ${row.model}") },
                            supportingContent = { Text("输入 ${row.promptTokens} · 输出 ${row.completionTokens} · ${row.requestCount} 次") },
                        )
                    }
                }
                val auxTotal = auxUsage.sumOf { it.totalTokens }
                Text(
                    "辅助消耗（标题/测试连接）：$auxTotal tokens · ${auxUsage.size} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = viewModel::clearUsageStats) { Text("清空统计") }
            }

            // ================= 7. 安全与隐私 =================
            SettingsGroup(title = "安全与隐私", glassEnabled = glass.enabled) {
                Text(
                    "API Key 使用 Android Keystore（AES/GCM）加密存储，数据库仅保存密文引用，绝不明文落盘。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow("注入扫描（只提示不删除）", promptSafety.injectionScanEnabled) {
                    viewModel.setPromptSafety(promptSafety.copy(injectionScanEnabled = it))
                }
                Text(
                    "网络日志默认关闭（openai-kotlin LoggingConfig = NONE）；LLM 输出永远不会直接执行系统命令。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { showClearDataConfirm = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.width(18.dp)); Text("一键清除本地会话数据")
                }
            }

            // ================= 8. 数据管理 =================
            SettingsGroup(title = "数据管理", glassEnabled = glass.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { exportConvLauncher.launch("mchat-conversations.json") }) { Text("导出会话") }
                    TextButton(onClick = { importConvLauncher.launch(arrayOf("application/json")) }) { Text("导入会话") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { exportPresetLauncher.launch("mchat-presets.json") }) { Text("导出预设") }
                    TextButton(onClick = viewModel::clearAttachmentCache) { Text("清除附件缓存") }
                }
            }

            // ================= 9. 关于 =================
            SettingsGroup(title = "关于", glassEnabled = glass.enabled) {
                ListItem(headlineContent = { Text("版本") }, supportingContent = { Text("0.1.0（versionCode 1，全程锁定）") })
                ListItem(headlineContent = { Text("包名") }, supportingContent = { Text("com.ming.mchat") })
                ListItem(
                    headlineContent = { Text("GitHub 仓库") },
                    supportingContent = { Text("Ming-QWQ520/MChat") },
                    trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/Ming-QWQ520/MChat".toUri()))
                        }
                    },
                )
                Text(
                    "隐私说明：所有数据仅保存在本机；API Key 加密存储；不经用户允许不发送任何遥测。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Text("开源许可：Apache-2.0（openai-kotlin / Ktor / PdfBox-Android / JTokkit / Coil）与 MIT（Compose 生态相关项目）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// ===================== 组件 =====================

@Composable
private fun SettingsGroup(title: String, glassEnabled: Boolean, content: @Composable () -> Unit) {
    if (glassEnabled) {
        LiquidGlassSurface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    } else {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun ThemeModeRow(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val dynamicAvailable = android.os.Build.VERSION.SDK_INT >= 31
    Column {
        Text("主题模式（颜色跟随系统，Android 12+ 动态取色）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ThemeMode.SYSTEM to "跟随系统",
                ThemeMode.LIGHT to "浅色",
                ThemeMode.DARK to "深色",
                ThemeMode.DYNAMIC to "动态取色",
            ).forEach { (mode, label) ->
                val enabled = mode != ThemeMode.DYNAMIC || dynamicAvailable
                FilterChip(
                    selected = current == mode,
                    onClick = { if (enabled) onSelect(mode) },
                    label = { Text(label + if (mode == ThemeMode.DYNAMIC && !dynamicAvailable) "（需 12+）" else "") },
                    enabled = enabled,
                )
            }
        }
    }
}

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

@Composable
private fun ChipRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, option ->
                FilterChip(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                    label = { Text(option) },
                )
            }
        }
    }
}

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
                            trailingContent = {
                                if (selected) {
                                    Icon(Icons.Filled.Balance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
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
            TemplateEngine.builtinContext("deepseek-chat"),
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
