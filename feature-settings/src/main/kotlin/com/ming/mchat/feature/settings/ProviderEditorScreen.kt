package com.ming.mchat.feature.settings

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ming.mchat.core.theme.LocalLiquidGlass
import com.ming.mchat.core.theme.LiquidGlassSurface

/** Provider 详情编辑页：Base URL / API Key / 模型列表 / 测试连接 / 设为默认 / 删除自定义。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderEditorScreen(
    onBack: () -> Unit,
    viewModel: ProviderEditorViewModel = hiltViewModel(),
) {
    val provider by viewModel.provider.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val behavior by viewModel.behavior.collectAsStateWithLifecycle()
    val keyConfigured by viewModel.keyConfigured.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val glass = LocalLiquidGlass.current

    LaunchedEffect(deleted) { if (deleted) onBack() }
    LaunchedEffect(provider?.id) { viewModel.refreshKeyStatus() }

    var baseUrl by remember(provider?.baseUrl) { mutableStateOf(provider?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(provider?.displayName ?: "Provider") },
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
            LiquidGlassSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, content = {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("启用", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = provider?.enabled == true, onCheckedChange = viewModel::setEnabled)
                    }
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Base URL（如 https://api.deepseek.com/v1）") },
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
                    testResult?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            })

            LiquidGlassSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, content = {
                Column(Modifier.padding(16.dp)) {
                    Text("模型列表", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    models.forEach { model ->
                        val isDefault = behavior.defaultProviderId == viewModel.providerId &&
                            behavior.defaultModelId == model.modelId
                        ListItem(
                            headlineContent = { Text(model.displayName + if (isDefault) "（默认）" else "") },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        "上下文 ${model.contextTokens / 1024}K",
                                        if (model.supportsVision) "视觉" else null,
                                        if (model.supportsReasoning) "推理" else null,
                                        if (model.useMaxCompletionTokens) "max_completion_tokens" else null,
                                        if (!model.supportsStreamUsage) "流式 usage 不稳定（本地估算）" else null,
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = model.enabled,
                                    onCheckedChange = { viewModel.setModelEnabled(model.id, it) },
                                )
                            },
                        )
                        TextButton(onClick = { viewModel.setDefault(model.modelId) }) {
                            Text(if (isDefault) "已是默认" else "设为默认")
                        }
                        HorizontalDivider()
                    }
                }
            })

            if (provider?.isBuiltin == false) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("删除自定义 Provider")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除该 Provider？") },
            text = { Text("仅自定义 Provider 可删除，内置 Provider 只能停用。") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.deleteProvider() }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
        )
    }
}
