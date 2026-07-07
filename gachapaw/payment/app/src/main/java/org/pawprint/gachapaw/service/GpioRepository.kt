package org.pawprint.gachapaw.service

import android.util.Log
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
        Log.d("GpioRepository", "onServiceStarted: service=$service")
        _serviceState.value = GpioServiceState.Connected(service)
    }
    
    fun onServiceStopped() {
        Log.d("GpioRepository", "onServiceStopped")
        _serviceState.value = GpioServiceState.Disconnected
    }
}
