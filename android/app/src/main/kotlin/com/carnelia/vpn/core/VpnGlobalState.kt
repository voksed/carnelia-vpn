package com.carnelia.vpn.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VpnGlobalState {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _stats = MutableStateFlow(VpnStats())
    val stats: StateFlow<VpnStats> = _stats.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // New Features Flags (Default to TRUE for the update)
    var isNetShieldEnabled: Boolean = true
    var isStealthModeEnabled: Boolean = true
    var isSecureKeyCheckEnabled: Boolean = true

    fun updateState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun updateStats(stats: VpnStats) {
        _stats.value = stats
    }

    fun setError(message: String?) {
        _lastError.value = message
    }
}
