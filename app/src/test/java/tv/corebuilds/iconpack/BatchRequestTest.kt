package tv.corebuilds.iconpack

import org.junit.Assert.assertEquals
import org.junit.Test

/** Request all's route and selection: plain strings in, plain lists out. */
class BatchRequestTest {

    @Test fun theBatchRouteSitsBesideEitherSpellingOfTheEndpoint() {
        for (endpoint in listOf(
            "https://broker.example.dev", "https://broker.example.dev/",
            "https://broker.example.dev/v1/request", "https://broker.example.dev/v1/request/",
            " https://broker.example.dev/ ",
        )) {
            assertEquals(endpoint, "https://broker.example.dev/v1/batch", BatchRequest.url(endpoint))
        }
    }

    @Test fun aPressSendsOnlyAppsNotYetRequestedInListOrder() {
        val items = listOf("a/.A", "b/.B", "c/.C")
        assertEquals(listOf("a/.A", "c/.C"), BatchRequest.pending(items, { it }, setOf("b/.B")))
        assertEquals(emptyList<String>(), BatchRequest.pending(items, { it }, items.toSet()))
    }

    @Test fun aPressSendsAtMostTheBrokersCap() {
        val items = (1..25).map { "p$it/.Main" }
        val pending = BatchRequest.pending(items, { it }, emptySet())
        assertEquals(BatchRequest.MAX, pending.size)
        assertEquals(items.take(BatchRequest.MAX), pending)
    }
}
