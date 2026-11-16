package com.ming.mchat.core.common

/**
 * 全局设置枚举：供 core-data（持久化）与 core-theme / 各 feature（UI）共用。
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK, DYNAMIC }
enum class GlassIntensity { LOW, MEDIUM, HIGH }

/**
 * 思考强度：对齐 DeepSeek reasoning_effort 取值（none / low / high / max）。
 * NONE = 关闭思考（非思考模式）；MAX 不传参数时由服务端使用模型默认最高档。
 */
enum class ReasoningEffortLevel { NONE, LOW, HIGH, MAX }
enum class OutputTokenMode { DEFAULT, MANUAL, AUTO }
enum class ChatBackground { SOLID, GRADIENT, IMAGE }
enum class TitleLanguage { AUTO, CHINESE, ENGLISH }
