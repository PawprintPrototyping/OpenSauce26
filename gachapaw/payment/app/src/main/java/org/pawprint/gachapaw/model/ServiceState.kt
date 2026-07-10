package org.pawprint.gachapaw.model

enum class InputGpioState {
    NOT_CHECKING,
    WAITING,
    LATCH_HIT
}

enum class TransactionState {
    DISCONNECTED,
    INITIALIZING,
    WAITING_FOR_BUTTON_PRESS,
    WAITING_FOR_TRANSACTION_RESULT,
    TRANSACTION_SUCCESS,
    TRANSACTION_FAIL,
    MAINTENANCE
}

data class ServiceState(
    val transactionState: TransactionState = TransactionState.INITIALIZING,
    // Maintenance mode states below.
    val isPrizeDispenserActive: Boolean = false,
    val isSquareReaderActive: Boolean = false,
    val inputGpioState: InputGpioState = InputGpioState.NOT_CHECKING
)
