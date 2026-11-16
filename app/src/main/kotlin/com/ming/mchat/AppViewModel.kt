package com.ming.mchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ming.mchat.core.data.datastore.AppearanceSettings
import com.ming.mchat.core.data.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** 应用级 ViewModel：为全局主题提供外观设置。 */
@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val appearance: StateFlow<AppearanceSettings> =
        settingsRepository.appearance.stateIn(viewModelScope, SharingStarted.Eagerly, AppearanceSettings())
}
