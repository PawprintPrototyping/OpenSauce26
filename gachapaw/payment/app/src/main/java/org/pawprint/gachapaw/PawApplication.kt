package org.pawprint.gachapaw

import android.app.Application
import org.pawprint.gachapaw.service.GpioRepository
import org.pawprint.gachapaw.service.LoggingRepository
import org.pawprint.gachapaw.service.SquarePaymentRepository

class PawApplication : Application() {
    // Makes the repository visible as a singleton to Activities/Services
    val gpioRepository: GpioRepository by lazy { GpioRepository() }
    val squarePaymentRepository: SquarePaymentRepository by lazy { SquarePaymentRepository(this) }
    val loggingRepository: LoggingRepository by lazy { LoggingRepository() }
}
