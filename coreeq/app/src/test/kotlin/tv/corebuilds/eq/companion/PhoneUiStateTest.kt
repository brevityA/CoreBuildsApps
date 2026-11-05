package tv.corebuilds.eq.companion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUiStateTest {
    private val connected = PhoneUiState(true, false, false, false)

    @Test
    fun `connected and idle enables all controls`() {
        assertTrue(connected.canControl)
        assertTrue(connected.canChangeSetup)
        assertTrue(connected.canStartOrSave)
    }

    @Test
    fun `stale dashboards cannot control a disconnected TV`() {
        val state = connected.copy(connected = false)
        assertFalse(state.canControl)
        assertFalse(state.canStartOrSave)
        assertTrue(state.canChangeSetup)
    }

    @Test
    fun `a command or capture disables every conflicting action`() {
        for (state in listOf(connected.copy(operationInProgress = true), connected.copy(measurementInProgress = true))) {
            assertFalse(state.canControl)
            assertFalse(state.canChangeSetup)
            assertFalse(state.canStartOrSave)
        }
    }

    @Test
    fun `a retained first capture permits only an explicit save retry even when offline`() {
        for (online in listOf(false, true)) {
            val state = connected.copy(connected = online, firstCapturePending = true)
            assertFalse(state.canControl)
            assertFalse(state.canChangeSetup)
            assertTrue(state.canStartOrSave)
            assertFalse(state.copy(operationInProgress = true).canStartOrSave)
        }
    }
}
