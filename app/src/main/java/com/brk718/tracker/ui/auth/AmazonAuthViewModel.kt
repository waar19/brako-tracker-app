package com.brk718.tracker.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brk718.tracker.data.local.AmazonSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AmazonAuthViewModel @Inject constructor(
    private val sessionManager: AmazonSessionManager
) : ViewModel() {

    fun saveCookies(url: String) {
        viewModelScope.launch {
            sessionManager.saveCookies(url)
        }
    }
}
