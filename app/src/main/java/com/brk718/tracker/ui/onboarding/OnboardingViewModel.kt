package com.brk718.tracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.BuildConfig
import com.brk718.tracker.data.local.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    fun finishOnboarding() {
        viewModelScope.launch {
            prefsRepository.setOnboardingDone(true)
            // Marcar la versión actual como "vista" para que los usuarios nuevos
            // no vean el What's New dialog al instalar la app por primera vez
            prefsRepository.setLastSeenVersionCode(BuildConfig.VERSION_CODE)
        }
    }
}
