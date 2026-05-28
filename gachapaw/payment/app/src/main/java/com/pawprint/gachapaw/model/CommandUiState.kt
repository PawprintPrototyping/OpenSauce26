package com.pawprint.gachapaw.model

enum class InputGpioState {
    NOT_CHECKING,
    WAITING,
    LATCH_HIT
}

data class CommandUiState(
    val isPrizeDispenserActive: Boolean = false,
    val isSquareReaderActive: Boolean = false,
    val inputGpioState: InputGpioState = InputGpioState.NOT_CHECKING
)
