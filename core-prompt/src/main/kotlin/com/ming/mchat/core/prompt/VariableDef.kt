package com.ming.mchat.core.prompt

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 提示词变量定义（variablesJson 的模型）。 */
@Serializable
data class VariableDef(
    val key: String,
    val label: String = "",
    val type: VariableType = VariableType.TEXT,
    val defaultValue: String = "",
    val options: List<String> = emptyList(),
    val required: Boolean = false,
) {
    enum class VariableType { TEXT, SELECT, MULTILINE, NUMBER }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parseList(raw: String?): List<VariableDef> =
            if (raw.isNullOrBlank()) emptyList()
            else runCatching { json.decodeFromString<List<VariableDef>>(raw) }.getOrDefault(emptyList())

        fun encodeList(list: List<VariableDef>): String = json.encodeToString(list)
    }
}
