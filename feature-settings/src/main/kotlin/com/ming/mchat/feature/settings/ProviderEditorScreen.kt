package com.ming.mchat.feature.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ming.mchat.core.data.entity.ModelConfigEntity

/**
 * Provider 编辑 / 新建页：
 * - 新建：填供应商名称 / Base URL / API Key，创建后跳转编辑页继续配置模型；
 * - 编辑：Base URL / API Key / 测试连接 / 启用 / 删除自定义供应商；
 * - 模型管理：手动添加、编辑、删除、从 API 拉取（GET /models）、恢复内置（DeepSeek）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditorScreen(
    onBack: () -> Unit,
    onOpenProvider: (String) -> Unit = {},
    viewModel: ProviderEditorViewModel = hiltViewModel(),
) {
    val provider by viewModel.provider.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val behavior by viewModel.behavior.collectAsStateWithLifecycle()
    val keyConfigured by viewModel.keyConfigured.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val fetchingModels by viewModel.fetchingModels.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val createdProviderId by viewModel.createdProviderId.collectAsStateWithLifecycle()

    LaunchedEffect(deleted) { if (deleted) onBack() }
    LaunchedEffect(provider?.id) { viewModel.refreshKeyStatus() }
    LaunchedEffect(createdProviderId) {
        createdProviderId?.let { id ->
            viewModel.consumeCreatedEvent()
            onOpenProvider(id)
        }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editModelTarget by remember { mutableStateOf<ModelConfigEntity?>(null) }
    var showAddModel by remember { mutableStateOf(false) }
    var deleteModelTarget by remember { mutableStateOf<ModelConfigEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (viewModel.isNew) "添加供应商" else (provider?.displayName ?: "供应商"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
            if (viewModel.isNew) {
                NewProviderForm(viewModel)
            } else {
                ProviderConfigCard(
                    viewModel = viewModel,
                    provider = provider,
                    keyConfigured = keyConfigured,
                    testResult = testResult,
                    testing = testing,
                    isDeepSeek = provider?.id == "deepseek",
                )

                // ================= 模型管理 =================
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("模型列表", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { showAddModel = true }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("添加")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = viewModel::fetchModelsFromApi,
                                enabled = !fetchingModels,
                            ) {
                                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (fetchingModels) "拉取中…" else "从 API 拉取")
                            }
                            if (provider?.id == "deepseek") {
                                OutlinedButton(onClick = viewModel::restoreBuiltinModels) {
                                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("恢复内置")
                                }
                            }
                        }
                        testResult?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        models.forEach { model ->
                            val isDefault = behavior.defaultProviderId == viewModel.providerId &&
                                behavior.defaultModelId == model.modelId
                            ListItem(
                                headlineContent = {
                                    Text(model.displayName + if (isDefault) "（默认）" else if (!model.enabled) "（已停用）" else "")
                                },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            model.modelId,
                                            "上下文 ${model.contextTokens / 1024}K",
                                            if (model.supportsVision) "视觉" else null,
                                            if (model.supportsReasoning) "推理" else null,
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { editModelTarget = model }, modifier = Modifier.size(32.dp)) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = "编辑模型",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(onClick = { deleteModelTarget = model }, modifier = Modifier.size(32.dp)) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "删除模型",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                        if (models.isEmpty()) {
                            Text(
                                "暂无模型。可“从 API 拉取”或手动添加。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (provider?.isBuiltin == false) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("删除该供应商", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---- 对话框 ----

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除该供应商？") },
            text = { Text("将删除该供应商及其模型列表；使用它的会话会自动切换到 DeepSeek 默认模型。") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.deleteProvider() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
        )
    }

    deleteModelTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteModelTarget = null },
            title = { Text("删除模型？") },
            text = { Text("将删除「${target.displayName}」；引用它的会话会自动切换到其他可用模型。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteModel(target.providerId, target.modelId)
                    deleteModelTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteModelTarget = null }) { Text("取消") } },
        )
    }

    if (showAddModel) {
        ModelEditDialog(
            providerId = viewModel.providerId,
            existing = null,
            onDismiss = { showAddModel = false },
            onSave = {
                viewModel.saveModel(it)
                showAddModel = false
            },
        )
    }

    editModelTarget?.let { target ->
        ModelEditDialog(
            providerId = viewModel.providerId,
            existing = target,
            onDismiss = { editModelTarget = null },
            onSave = {
                viewModel.saveModel(it)
                editModelTarget = null
            },
        )
    }
}

/** 新建供应商表单：供应商名称 / Base URL / API Key。 */
@Composable
private fun NewProviderForm(viewModel: ProviderEditorViewModel) {
    var displayName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }

    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("接入任意 OpenAI 兼容服务（DeepSeek / GLM / Grok / OpenAI / Claude …）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("供应商名称（如 GLM、OpenAI）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL（如 https://open.bigmodel.cn/api/paas/v4）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key（可稍后填写）") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "创建后可在编辑页“从 API 拉取”模型列表，或手动添加模型。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { viewModel.createProvider(displayName, baseUrl, apiKey) },
                enabled = displayName.isNotBlank() && baseUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("创建供应商")
            }
        }
    }
}

/** 供应商基础配置卡片：启用 / Base URL / API Key / 测试连接 / DeepSeek logo。 */
@Composable
private fun ProviderConfigCard(
    viewModel: ProviderEditorViewModel,
    provider: com.ming.mchat.core.data.entity.ProviderConfigEntity?,
    keyConfigured: Boolean,
    testResult: String?,
    testing: Boolean,
    isDeepSeek: Boolean,
) {
    var baseUrl by remember(provider?.baseUrl) { mutableStateOf(provider?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }

    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDeepSeek) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(34.dp),
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(
                                    com.ming.mchat.core.theme.R.drawable.deepseek_logo,
                                ),
                                contentDescription = "DeepSeek",
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                    }
                    Text("启用", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(checked = provider?.enabled == true, onCheckedChange = viewModel::setEnabled)
            }
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL（如 https://api.deepseek.com）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { viewModel.updateBaseUrl(baseUrl) }) { Text("保存 Base URL") }
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = {
                    Text(if (keyConfigured) "API Key（已配置，输入以更换）" else "API Key（未配置）")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Key 经 Android Keystore 加密后存储，数据库不保存明文。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.saveApiKey(apiKey); apiKey = "" }, enabled = apiKey.isNotBlank()) {
                    Text("保存 Key")
                }
                OutlinedButton(onClick = viewModel::testConnection, enabled = !testing && keyConfigured) {
                    Text(if (testing) "测试中…" else "测试连接")
                }
            }
        }
    }
}

/** 模型添加 / 编辑对话框：ID、名称、上下文窗口、能力开关。 */
@Composable
private fun ModelEditDialog(
    providerId: String,
    existing: ModelConfigEntity?,
    onDismiss: () -> Unit,
    onSave: (ModelConfigEntity) -> Unit,
) {
    var modelId by remember(existing) { mutableStateOf(existing?.modelId.orEmpty()) }
    var displayName by remember(existing) { mutableStateOf(existing?.displayName.orEmpty()) }
    var contextTokens by remember(existing) { mutableStateOf((existing?.contextTokens ?: 32768).toString()) }
    var supportsVision by remember(existing) { mutableStateOf(existing?.supportsVision ?: false) }
    var supportsReasoning by remember(existing) { mutableStateOf(existing?.supportsReasoning ?: false) }
    var supportsTemperature by remember(existing) { mutableStateOf(existing?.supportsTemperature ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "添加模型" else "编辑模型") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("模型 ID（API 调用名）") },
                    singleLine = true,
                    enabled = existing == null,
                )
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("显示名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = contextTokens,
                    onValueChange = { contextTokens = it.filter { c -> c.isDigit() } },
                    label = { Text("上下文窗口（tokens）") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = supportsVision, onCheckedChange = { supportsVision = it })
                    Text("视觉（图片输入）", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = supportsReasoning, onCheckedChange = { supportsReasoning = it })
                    Text("推理（深度思考）", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = supportsTemperature, onCheckedChange = { supportsTemperature = it })
                    Text("支持 temperature", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val id = modelId.trim()
                    if (id.isNotBlank() && displayName.isNotBlank()) {
                        onSave(
                            ModelConfigEntity(
                                id = "$providerId:$id",
                                providerId = providerId,
                                modelId = id,
                                displayName = displayName.trim(),
                                contextTokens = contextTokens.toIntOrNull() ?: 32768,
                                maxOutputTokens = existing?.maxOutputTokens ?: 8192,
                                supportsVision = supportsVision,
                                supportsStreamUsage = existing?.supportsStreamUsage ?: true,
                                supportsReasoning = supportsReasoning,
                                supportsTemperature = supportsTemperature,
                                useMaxCompletionTokens = existing?.useMaxCompletionTokens ?: false,
                                systemRole = existing?.systemRole ?: "system",
                                enabled = existing?.enabled ?: true,
                                isBuiltin = existing?.isBuiltin ?: false,
                            )
                        )
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
