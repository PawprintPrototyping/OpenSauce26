package org.pawprint.gachapaw.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class GpioServiceState {
    object Disconnected : GpioServiceState()
    data class Connected(val service: GpioService) : GpioServiceState()
}

class GpioRepository {
    private val _serviceState = MutableStateFlow<GpioServiceState>(GpioServiceState.Disconnected)
    val service = _serviceState.asStateFlow()
    fun onServiceStarted(service: GpioService) {
        _serviceState.value = GpioServiceState.Connected(service)
    }
    fun onServiceStopped() {
        _serviceState.value = GpioServiceState.Disconnected
    }
}