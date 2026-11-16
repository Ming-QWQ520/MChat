package com.ming.mchat.core.common

/**
 * 全局设置枚举：供 core-data（持久化）与 core-theme / 各 feature（UI）共用。
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK, DYNAMIC }
enum class GlassIntensity { LOW, MEDIUM, HIGH }
enum class ReasoningEffortLevel { LOW, HIGH, MAX }
enum class OutputTokenMode { DEFAULT, MANUAL, AUTO }
enum class ChatBackground { SOLID, GRADIENT, IMAGE }
enum class TitleLanguage { AUTO, CHINESE, ENGLISH }
