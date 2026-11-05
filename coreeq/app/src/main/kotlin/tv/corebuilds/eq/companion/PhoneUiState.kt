package tv.corebuilds.eq.companion

/** One interaction policy for every phone control, including listening-mode buttons. */
internal data class PhoneUiState(
    val connected: Boolean,
    val operationInProgress: Boolean,
    val measurementInProgress: Boolean,
    val firstCapturePending: Boolean
) {
    private val idle: Boolean get() = !operationInProgress && !measurementInProgress
    val canChangeSetup: Boolean get() = idle && !firstCapturePending
    val canControl: Boolean get() = connected && canChangeSetup
    // A failed repeat can leave a first sweep on the TV. Saving it is an explicit
    // retry, so it remains available even when a subsequent status read failed.
    val canStartOrSave: Boolean get() = idle && (connected || firstCapturePending)
}
