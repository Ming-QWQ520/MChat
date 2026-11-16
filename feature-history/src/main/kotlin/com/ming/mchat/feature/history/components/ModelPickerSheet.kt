package com.ming.mchat.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity

/**
 * 模型选择底部弹层：按供应商分组选择模型（DeepSeek 带品牌 logo）。
 * 切换只覆盖当前会话，影响后续消息。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    providers: List<ProviderConfigEntity>,
    modelsByProvider: Map<String, List<ModelConfigEntity>>,
    currentProviderId: String,
    currentModelId: String,
    onSelect: (providerId: String, modelId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "选择模型",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            providers.filter { it.enabled }.forEach { provider ->
                val models = modelsByProvider[provider.id].orEmpty()
                if (models.isEmpty()) return@forEach
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
                ) {
                    if (provider.id == "deepseek") {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(20.dp),
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(
                                    com.ming.mchat.core.theme.R.drawable.deepseek_logo,
                                ),
                                contentDescription = "DeepSeek",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        provider.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
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
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "当前模型",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(provider.id, model.modelId)
                                onDismiss()
                            },
                    )
                }
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
