package com.ming.mchat.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ming.mchat.core.data.entity.ModelConfigEntity
import com.ming.mchat.core.data.entity.ProviderConfigEntity

/**
 * 模型选择底部弹层：选择 Provider 与模型。
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
        providers.filter { it.enabled }.forEach { provider ->
            val models = modelsByProvider[provider.id].orEmpty()
            if (models.isEmpty()) return@forEach
            Text(
                provider.displayName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
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
                            Text("当前", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(provider.id, model.modelId)
                            onDismiss()
                        },
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
        Row(Modifier.padding(24.dp)) {} // bottom inset
    }
}
