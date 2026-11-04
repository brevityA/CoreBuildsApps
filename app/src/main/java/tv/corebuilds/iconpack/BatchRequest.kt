package tv.corebuilds.iconpack

/**
 * Core Builds Plus "Request all": which missing apps one press sends, and
 * where. The broker's batch route (tools/icon_request_broker, /v1/batch) files
 * each one as a priority request, exactly as a single press would; its limits
 * are mirrored here, and tests/test_batch_requests.py holds the two equal.
 */
object BatchRequest {

    /** Apps per press: the broker's BATCH_MAX. */
    const val MAX = 10

    /** Presses a day per purchase: the broker's BATCH_PER_DAY. */
    const val PER_DAY = 3

    /**
     * The batch route beside the single-request endpoint baked into the APK,
     * which is either the broker's root or its /v1/request path.
     */
    fun url(endpoint: String): String =
        endpoint.trim().removeSuffix("/").removeSuffix("/v1/request").removeSuffix("/") + "/v1/batch"

    /**
     * The next press's apps: those this TV has not requested yet, in list
     * order, at most [MAX]. An app already in My requests is left out, since
     * the broker would only +1 its issue again.
     */
    fun <T> pending(items: List<T>, component: (T) -> String, requested: Set<String>): List<T> =
        items.filter { component(it) !in requested }.take(MAX)
}
