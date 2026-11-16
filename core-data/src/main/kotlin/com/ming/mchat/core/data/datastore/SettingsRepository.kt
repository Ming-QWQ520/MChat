package com.ming.mchat.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ming.mchat.core.common.ChatBackground
import com.ming.mchat.core.common.GlassIntensity
import com.ming.mchat.core.common.OutputTokenMode
import com.ming.mchat.core.common.ReasoningEffortLevel
import com.ming.mchat.core.common.ThemeMode
import com.ming.mchat.core.common.TitleLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "mchat_settings")

/** 供 Hilt DataModule 使用的访问点。 */
fun settingsDataStore(context: Context): DataStore<Preferences> = context.settingsStore

private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

/** 外观设置。 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val liquidGlassEnabled: Boolean = false,
    val liquidGlassIntensity: GlassIntensity = GlassIntensity.MEDIUM,
    val reduceMotion: Boolean = false,
    val fontScale: Float = 1.0f,
    val chatBackground: ChatBackground = ChatBackground.SOLID,
    /** 自定义聊天背景图：应用私有目录绝对路径；空表示未设置。 */
    val backgroundImageUri: String = "",
)

/** 模型行为设置。 */
data class ModelBehaviorSettings(
    val defaultProviderId: String = "deepseek",
    val defaultModelId: String = "deepseek-flash",
    val globalTemperature: Double = 0.7,
    val reasoningEffort: ReasoningEffortLevel = ReasoningEffortLevel.HIGH,
    val outputTokenMode: OutputTokenMode = OutputTokenMode.DEFAULT,
    val outputTokenManualValue: Int = 2048,
    val lowBalanceThreshold: Double = 5.0,
    /** 思考过程默认折叠显示（DeepSeek 手机端风格）；关闭后始终展开。 */
    val collapseReasoning: Boolean = true,
)

/** 提示词与安全设置。 */
data class PromptSafetySettings(
    val defaultPresetId: Long = -1L,
    val appendGlobalSystemPrompt: Boolean = true,
    val globalSystemPrompt: String = "",
    val attachmentSafetyNoticeEnabled: Boolean = true,
    val injectionScanEnabled: Boolean = true,
)

/** 标题生成设置。 */
data class TitleSettings(
    val titleGenEnabled: Boolean = true,
    val titleGenProviderId: String = "",
    val titleGenModelId: String = "",
    val titleGenLanguage: TitleLanguage = TitleLanguage.AUTO,
)

/** 附件处理设置。 */
data class AttachmentSettings(
    val imageMaxLongEdge: Int = 1568,
    val jpegQuality: Int = 85,
    val imageMaxBytes: Long = 4L * 1024 * 1024,
    val pdfMaxChars: Int = 50000,
    val stripExif: Boolean = true,
)

/** 用户资料（kelivo：抽屉头部展示）。 */
data class UserProfileSettings(
    val userName: String = "",
    val avatarEmoji: String = "🦉",
)

/** 聊天气泡风格（kelivo：磨砂 / 实底）。 */
enum class BubbleStyle { FROSTED, SOLID }

/** 聊天展示设置。 */
data class ChatDisplaySettings(
    val bubbleStyle: BubbleStyle = BubbleStyle.FROSTED,
    val showTimestamp: Boolean = true,
    val showMessageNavButtons: Boolean = true,
)

/**
 * 统一 DataStore 偏好设置仓库：分组 Flow + 整组写入，即时生效。
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val appearance: Flow<AppearanceSettings> = dataStore.data.map { p ->
        AppearanceSettings(
            themeMode = enumOf(p[Keys.themeMode], ThemeMode.SYSTEM),
            liquidGlassEnabled = p[Keys.liquidGlassEnabled] ?: false,
            liquidGlassIntensity = enumOf(p[Keys.liquidGlassIntensity], GlassIntensity.MEDIUM),
            reduceMotion = p[Keys.reduceMotion] ?: false,
            fontScale = (p[Keys.fontScale] ?: 1.0f).coerceIn(0.85f, 1.3f),
            chatBackground = enumOf(p[Keys.chatBackground], ChatBackground.SOLID),
            backgroundImageUri = p[Keys.backgroundImageUri] ?: "",
        )
    }

    val modelBehavior: Flow<ModelBehaviorSettings> = dataStore.data.map { p ->
        ModelBehaviorSettings(
            defaultProviderId = p[Keys.defaultProviderId] ?: "deepseek",
            defaultModelId = p[Keys.defaultModelId] ?: "deepseek-flash",
            globalTemperature = p[Keys.globalTemperature] ?: 0.7,
            reasoningEffort = enumOf(p[Keys.reasoningEffort], ReasoningEffortLevel.HIGH),
            outputTokenMode = enumOf(p[Keys.outputTokenMode], OutputTokenMode.DEFAULT),
            outputTokenManualValue = p[Keys.outputTokenManualValue] ?: 2048,
            lowBalanceThreshold = p[Keys.lowBalanceThreshold] ?: 5.0,
            collapseReasoning = p[Keys.collapseReasoning] ?: true,
        )
    }

    val promptSafety: Flow<PromptSafetySettings> = dataStore.data.map { p ->
        PromptSafetySettings(
            defaultPresetId = p[Keys.defaultPresetId] ?: -1L,
            appendGlobalSystemPrompt = p[Keys.appendGlobalSystemPrompt] ?: true,
            globalSystemPrompt = p[Keys.globalSystemPrompt] ?: "",
            attachmentSafetyNoticeEnabled = p[Keys.attachmentSafetyNoticeEnabled] ?: true,
            injectionScanEnabled = p[Keys.injectionScanEnabled] ?: true,
        )
    }

    val title: Flow<TitleSettings> = dataStore.data.map { p ->
        TitleSettings(
            titleGenEnabled = p[Keys.titleGenEnabled] ?: true,
            titleGenProviderId = p[Keys.titleGenProviderId] ?: "",
            titleGenModelId = p[Keys.titleGenModelId] ?: "",
            titleGenLanguage = enumOf(p[Keys.titleGenLanguage], TitleLanguage.AUTO),
        )
    }

    val attachment: Flow<AttachmentSettings> = dataStore.data.map { p ->
        AttachmentSettings(
            imageMaxLongEdge = p[Keys.imageMaxLongEdge] ?: 1568,
            jpegQuality = (p[Keys.jpegQuality] ?: 85).coerceIn(40, 100),
            imageMaxBytes = p[Keys.imageMaxBytes] ?: (4L * 1024 * 1024),
            pdfMaxChars = p[Keys.pdfMaxChars] ?: 50000,
            stripExif = p[Keys.stripExif] ?: true,
        )
    }

    val userProfile: Flow<UserProfileSettings> = dataStore.data.map { p ->
        UserProfileSettings(
            userName = p[Keys.userName] ?: "",
            avatarEmoji = p[Keys.avatarEmoji] ?: "🦉",
        )
    }

    val chatDisplay: Flow<ChatDisplaySettings> = dataStore.data.map { p ->
        ChatDisplaySettings(
            bubbleStyle = enumOf(p[Keys.bubbleStyle], BubbleStyle.FROSTED),
            showTimestamp = p[Keys.showTimestamp] ?: true,
            showMessageNavButtons = p[Keys.showMessageNavButtons] ?: true,
        )
    }

    /** 快捷短语（自定义部分；内置短语另行提供）。 */
    val quickPhrases: Flow<List<String>> = dataStore.data.map { p ->
        p[Keys.quickPhrases]?.toList() ?: emptyList()
    }

    suspend fun setQuickPhrases(value: List<String>) {
        dataStore.edit { it[Keys.quickPhrases] = value.distinct().toSet() }
    }

    suspend fun setAppearance(value: AppearanceSettings) {
        dataStore.edit { p ->
            p[Keys.themeMode] = value.themeMode.name
            p[Keys.liquidGlassEnabled] = value.liquidGlassEnabled
            p[Keys.liquidGlassIntensity] = value.liquidGlassIntensity.name
            p[Keys.reduceMotion] = value.reduceMotion
            p[Keys.fontScale] = value.fontScale
            p[Keys.chatBackground] = value.chatBackground.name
            p[Keys.backgroundImageUri] = value.backgroundImageUri
        }
    }

    suspend fun setModelBehavior(value: ModelBehaviorSettings) {
        dataStore.edit { p ->
            p[Keys.defaultProviderId] = value.defaultProviderId
            p[Keys.defaultModelId] = value.defaultModelId
            p[Keys.globalTemperature] = value.globalTemperature
            p[Keys.reasoningEffort] = value.reasoningEffort.name
            p[Keys.outputTokenMode] = value.outputTokenMode.name
            p[Keys.outputTokenManualValue] = value.outputTokenManualValue
            p[Keys.lowBalanceThreshold] = value.lowBalanceThreshold
            p[Keys.collapseReasoning] = value.collapseReasoning
        }
    }

    suspend fun setPromptSafety(value: PromptSafetySettings) {
        dataStore.edit { p ->
            p[Keys.defaultPresetId] = value.defaultPresetId
            p[Keys.appendGlobalSystemPrompt] = value.appendGlobalSystemPrompt
            p[Keys.globalSystemPrompt] = value.globalSystemPrompt
            p[Keys.attachmentSafetyNoticeEnabled] = value.attachmentSafetyNoticeEnabled
            p[Keys.injectionScanEnabled] = value.injectionScanEnabled
        }
    }

    suspend fun setTitle(value: TitleSettings) {
        dataStore.edit { p ->
            p[Keys.titleGenEnabled] = value.titleGenEnabled
            p[Keys.titleGenProviderId] = value.titleGenProviderId
            p[Keys.titleGenModelId] = value.titleGenModelId
            p[Keys.titleGenLanguage] = value.titleGenLanguage.name
        }
    }

    suspend fun setAttachment(value: AttachmentSettings) {
        dataStore.edit { p ->
            p[Keys.imageMaxLongEdge] = value.imageMaxLongEdge
            p[Keys.jpegQuality] = value.jpegQuality
            p[Keys.imageMaxBytes] = value.imageMaxBytes
            p[Keys.pdfMaxChars] = value.pdfMaxChars
            p[Keys.stripExif] = value.stripExif
        }
    }

    suspend fun setUserProfile(value: UserProfileSettings) {
        dataStore.edit { p ->
            p[Keys.userName] = value.userName.take(24)
            p[Keys.avatarEmoji] = value.avatarEmoji.take(4)
        }
    }

    suspend fun setChatDisplay(value: ChatDisplaySettings) {
        dataStore.edit { p ->
            p[Keys.bubbleStyle] = value.bubbleStyle.name
            p[Keys.showTimestamp] = value.showTimestamp
            p[Keys.showMessageNavButtons] = value.showMessageNavButtons
        }
    }

    private object Keys {
        val themeMode = stringPreferencesKey("themeMode")
        val liquidGlassEnabled = booleanPreferencesKey("liquidGlassEnabled")
        val liquidGlassIntensity = stringPreferencesKey("liquidGlassIntensity")
        val reduceMotion = booleanPreferencesKey("reduceMotion")
        val fontScale = floatPreferencesKey("fontScale")
        val chatBackground = stringPreferencesKey("chatBackground")
        val backgroundImageUri = stringPreferencesKey("backgroundImageUri")

        val defaultProviderId = stringPreferencesKey("defaultProviderId")
        val defaultModelId = stringPreferencesKey("defaultModelId")
        val globalTemperature = doublePreferencesKey("globalTemperature")
        val reasoningEffort = stringPreferencesKey("reasoningEffort")
        val outputTokenMode = stringPreferencesKey("outputTokenMode")
        val outputTokenManualValue = intPreferencesKey("outputTokenManualValue")
        val lowBalanceThreshold = doublePreferencesKey("lowBalanceThreshold")
        val collapseReasoning = booleanPreferencesKey("collapseReasoning")

        val defaultPresetId = longPreferencesKey("defaultPresetId")
        val appendGlobalSystemPrompt = booleanPreferencesKey("appendGlobalSystemPrompt")
        val globalSystemPrompt = stringPreferencesKey("globalSystemPrompt")
        val attachmentSafetyNoticeEnabled = booleanPreferencesKey("attachmentSafetyNoticeEnabled")
        val injectionScanEnabled = booleanPreferencesKey("injectionScanEnabled")

        val titleGenEnabled = booleanPreferencesKey("titleGenEnabled")
        val titleGenProviderId = stringPreferencesKey("titleGenProviderId")
        val titleGenModelId = stringPreferencesKey("titleGenModelId")
        val titleGenLanguage = stringPreferencesKey("titleGenLanguage")

        val imageMaxLongEdge = intPreferencesKey("imageMaxLongEdge")
        val jpegQuality = intPreferencesKey("jpegQuality")
        val imageMaxBytes = longPreferencesKey("imageMaxBytes")
        val pdfMaxChars = intPreferencesKey("pdfMaxChars")
        val stripExif = booleanPreferencesKey("stripExif")

        val userName = stringPreferencesKey("userName")
        val avatarEmoji = stringPreferencesKey("avatarEmoji")
        val bubbleStyle = stringPreferencesKey("bubbleStyle")
        val showTimestamp = booleanPreferencesKey("showTimestamp")
        val showMessageNavButtons = booleanPreferencesKey("showMessageNavButtons")
        val quickPhrases = stringSetPreferencesKey("quickPhrases")
    }
}
