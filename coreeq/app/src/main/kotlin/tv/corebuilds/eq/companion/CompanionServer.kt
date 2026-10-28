package tv.corebuilds.eq.companion

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import tv.corebuilds.eq.BuildConfig
import tv.corebuilds.eq.apply.EqService
import tv.corebuilds.eq.apply.OutputRoute
import tv.corebuilds.eq.dsp.Correction
import tv.corebuilds.eq.dsp.MeasurementQuality
import tv.corebuilds.eq.dsp.MeasurementException
import tv.corebuilds.eq.dsp.RepeatabilityAnalysis
import tv.corebuilds.eq.dsp.RepeatabilityComparison
import tv.corebuilds.eq.dsp.Sweep
import tv.corebuilds.eq.dsp.SweepAnalysis
import tv.corebuilds.eq.dsp.SweepResult
import tv.corebuilds.eq.export.CaptureInfo
import tv.corebuilds.eq.export.CurvePoint
import tv.corebuilds.eq.export.MicrophoneResponsePoint
import tv.corebuilds.eq.export.PlatformBand
import tv.corebuilds.eq.export.Profile
import tv.corebuilds.eq.export.ProfileStore
import tv.corebuilds.eq.export.RepeatabilityCheck
import tv.corebuilds.eq.measure.StimulusPlayer
import tv.corebuilds.eq.mode.ContentMode
import tv.corebuilds.eq.mode.ContentModeStore
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.log10

/**
 * On-demand Core EQ LAN endpoint. It exists only while the TV user has the
 * Companion screen open. RPC bodies are AES-GCM encrypted with a one-time
 * 128-bit pairing key or a 256-bit per-phone token; the HTTP path is bound as
 * GCM additional data. Pairing expires and is rate-limited per LAN peer,
 * requests are replay-checked, the HTTP framing and worker pool are bounded,
 * and TV-side revoke deletes the stored token.
 *
 * This is not an internet service, an ADB service, or an always-on listener;
 * the listener is stopped as soon as its TV screen is no longer visible.
 */
internal class CompanionServer(
    context: Context,
    private val onStatus: (String) -> Unit = {}
) : Closeable {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val profileStore = ProfileStore(appContext)
    private val modeStore = ContentModeStore(appContext)
    private val stimulusPlayer = StimulusPlayer()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workers = ThreadPoolExecutor(
        WORKER_COUNT,
        WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(MAX_QUEUED_CONNECTIONS),
    )
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val listenerLock = Any()
    private val pairingLock = Any()
    private val pairingAttemptLimiter = PairingAttemptLimiter()
    private val replayedNonces = object : LinkedHashSet<String>() {}
    private val nonceLock = Any()
    /** Replaced by [ListenerPort.open] in [start]; an unbound placeholder until then. */
    @Volatile private var server = ServerSocket()
    @Volatile private var running = false
    @Volatile var port: Int = 0
        private set
    @Volatile private var pairingKey: ByteArray? = null
    @Volatile private var pairingExpiresElapsed: Long = 0L
    @Volatile private var measurement: MeasurementSession? = null
    @Volatile private var measurementTimeout: Runnable? = null
    @Volatile private var lastError: String? = null
    /**
     * Called (on the server thread) when a phone asks the TV to show its pairing screen. The
     * request carries nothing and grants nothing: it only opens the screen, which still needs
     * the one-time code.
     */
    @Volatile var onWake: (() -> Unit)? = null
    private val wakeLock = Any()
    private var lastWakeMs: Long? = null

    private enum class MeasurementPhase { FIRST_SWEEP, WAITING_FOR_REPEAT, REPEAT_SWEEP }

    private data class MeasurementSession(
        val id: String,
        val target: String,
        val volumeM3: Double?,
        @Volatile var startedElapsedMs: Long,
        val repeatRequested: Boolean,
        /** Output reported for the first sweep; this remains the profile's route. */
        @Volatile var output: OutputRoute.Output?,
        @Volatile var activeOutput: OutputRoute.Output? = output,
        @Volatile var repeatOutput: OutputRoute.Output? = null,
        @Volatile var phase: MeasurementPhase = MeasurementPhase.FIRST_SWEEP,
        @Volatile var firstResult: SweepResult? = null,
        @Volatile var firstCaptureInfo: CaptureInfo? = null,
        @Volatile var firstCaptureSeconds: Double? = null
    )

    fun start(): Int {
        if (running) return port
        runCatching { server.close() }
        server = ListenerPort.open(BACKLOG)
        port = server.localPort
        running = true
        Thread({ acceptLoop() }, "CoreEqCompanionServer").apply { isDaemon = true; start() }
        updateStatus("Phone link ready on port $port. Pairing is off until you start it here.")
        return port
    }

    /** Return a fresh, high-entropy code for the TV screen; a previous code is immediately invalid. */
    fun startPairing(): String = synchronized(pairingLock) {
        check(running) { "Companion server is not running" }
        val key = CompanionCrypto.randomKey(CompanionCrypto.PAIRING_KEY_BYTES)
        pairingKey = key
        pairingExpiresElapsed = SystemClock.elapsedRealtime() + PAIRING_WINDOW_MS
        clearReplayCache()
        updateStatus("Pairing code is active for two minutes. It is shown only on this TV.")
        CompanionCrypto.encodeKey(key)
    }

    fun stopPairing() = synchronized(pairingLock) {
        pairingKey = null
        pairingExpiresElapsed = 0L
        clearReplayCache()
        updateStatus(if (isPaired()) "Paired phone retained; new pairings are off." else "Pairing stopped.")
    }

    fun isPairing(): Boolean = pairingKey != null &&
        SystemClock.elapsedRealtime() < pairingExpiresElapsed

    fun isPaired(): Boolean = storedToken() != null

    fun pairedClientName(): String? = preferences.getString(KEY_CLIENT_NAME, null)

    @Synchronized
    fun revokePairing() {
        check(running) { "Local phone link is stopped." }
        preferences.edit().remove(KEY_TOKEN).remove(KEY_CLIENT_NAME).remove(KEY_PAIRED_AT).apply()
        clearReplayCache()
        if (measurement != null) {
            measurement = null
            lastError = null
            clearMeasurementTimeout()
            stimulusPlayer.stop()
            EqService.send(appContext, EqService.ACTION_RESUME)
        }
        updateStatus("Phone pairing revoked.")
    }

    private fun acceptLoop() {
        while (running) {
            var accepted: Socket? = null
            try {
                val client = server.accept()
                val acceptedAtElapsedMs = SystemClock.elapsedRealtime()
                accepted = client
                val registered = synchronized(listenerLock) {
                    if (!running || !isPrivateLanAddress(client.inetAddress) ||
                        activeSockets.size >= MAX_ACTIVE_CONNECTIONS) {
                        false
                    } else {
                        client.soTimeout = SOCKET_IDLE_TIMEOUT_MS
                        activeSockets.add(client)
                    }
                }
                if (!registered) {
                    client.close()
                    accepted = null
                    if (!running) break
                    continue
                }
                try {
                    workers.execute {
                        try {
                            handleSocket(client, acceptedAtElapsedMs)
                        } finally {
                            activeSockets.remove(client)
                        }
                    }
                    accepted = null
                } catch (_: RejectedExecutionException) {
                    activeSockets.remove(client)
                    client.close()
                    accepted = null
                }
            } catch (e: Exception) {
                accepted?.let {
                    activeSockets.remove(it)
                    try { it.close() } catch (_: Exception) { }
                }
                if (running) {
                    Log.w(TAG, "Local companion connection failed", e)
                    updateStatus("A phone connection failed: ${e.javaClass.simpleName}.")
                }
            }
        }
    }

    private fun isPrivateLanAddress(address: InetAddress): Boolean {
        val bytes = (address as? java.net.Inet4Address)?.address ?: return false
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }

    private data class HttpRequest(val method: String, val path: String, val body: ByteArray)
    private data class HttpResponse(val status: Int, val body: ByteArray)

    private fun handleSocket(socket: Socket, acceptedAtElapsedMs: Long) {
        socket.use { client ->
            try {
                val request = readRequest(client, acceptedAtElapsedMs)
                if (requestDeadlineReached(acceptedAtElapsedMs)) {
                    safelyWriteError(client, 408, "Request timed out")
                    return@use
                }
                val response = dispatchHttp(request, client.inetAddress)
                if (requestDeadlineReached(acceptedAtElapsedMs)) {
                    safelyWriteError(client, 408, "Request timed out")
                    return@use
                }
                val reason = when (response.status) {
                    200 -> "OK"
                    400 -> "Bad Request"
                    403 -> "Forbidden"
                    404 -> "Not Found"
                    405 -> "Method Not Allowed"
                    408 -> "Request Timeout"
                    409 -> "Conflict"
                    413 -> "Payload Too Large"
                    429 -> "Too Many Requests"
                    503 -> "Service Unavailable"
                    else -> "Internal Server Error"
                }
                val header = "HTTP/1.1 ${response.status} $reason\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${response.body.size}\r\n" +
                    "Cache-Control: no-store\r\n" +
                    "Connection: close\r\n\r\n"
                client.getOutputStream().apply {
                    write(header.toByteArray(StandardCharsets.US_ASCII))
                    write(response.body)
                    flush()
                }
            } catch (e: RequestHeaderTooLargeException) {
                safelyWriteError(client, 431, "Request headers too large")
            } catch (e: RequestTooLargeException) {
                safelyWriteError(client, 413, "Request too large")
            } catch (e: RequestTimeoutException) {
                safelyWriteError(client, 408, "Request timed out")
            } catch (e: SocketTimeoutException) {
                safelyWriteError(client, 408, "Request timed out")
            } catch (e: Exception) {
                if (running) Log.i(TAG, "Companion request rejected: ${e.javaClass.simpleName}")
                safelyWriteError(client, 400, "Invalid request")
            }
        }
    }

    private fun safelyWriteError(socket: Socket, status: Int, message: String) {
        try {
            writeError(socket, status, message)
        } catch (_: Exception) {
            // The peer may have closed the connection already.
        }
    }

    private fun writeError(socket: Socket, status: Int, message: String) {
        val body = JSONObject().put("error", message).toString().toByteArray(StandardCharsets.UTF_8)
        val reason = when (status) {
            408 -> "Request Timeout"
            413 -> "Payload Too Large"
            431 -> "Request Header Fields Too Large"
            else -> "Bad Request"
        }
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(header.toByteArray(StandardCharsets.US_ASCII))
            write(body)
            flush()
        }
    }

    private fun requestDeadlineReached(acceptedAtElapsedMs: Long): Boolean =
        SystemClock.elapsedRealtime() - acceptedAtElapsedMs >= MAX_REQUEST_DURATION_MS

    private fun readRequest(socket: Socket, acceptedAtElapsedMs: Long): HttpRequest {
        fun checkDeadline() {
            if (requestDeadlineReached(acceptedAtElapsedMs)) throw RequestTimeoutException()
        }

        val input = socket.getInputStream()
        val headers = ByteArrayOutputStream()
        var state = 0
        while (headers.size() < MAX_HEADER_BYTES && state < 4) {
            checkDeadline()
            val value = input.read()
            checkDeadline()
            if (value < 0) throw IllegalArgumentException("Connection closed before headers")
            if (value > 0x7f) throw IllegalArgumentException("Non-ASCII HTTP headers")
            headers.write(value)
            state = when {
                state == 0 && value == '\r'.code -> 1
                state == 1 && value == '\n'.code -> 2
                state == 2 && value == '\r'.code -> 3
                state == 3 && value == '\n'.code -> 4
                value == '\r'.code -> 1
                else -> 0
            }
        }
        if (state != 4) {
            if (headers.size() >= MAX_HEADER_BYTES) throw RequestHeaderTooLargeException()
            throw IllegalArgumentException("Incomplete HTTP headers")
        }

        val lines = String(headers.toByteArray(), StandardCharsets.US_ASCII).dropLast(4).split("\r\n")
        val requestParts = lines.firstOrNull()?.split(' ')
            ?: throw IllegalArgumentException("Missing request line")
        if (requestParts.size != 3 || requestParts.any { it.isEmpty() } ||
            requestParts[2] != "HTTP/1.1" || requestParts[0].length > 16 ||
            requestParts[0].any { !it.isLetter() || !it.isUpperCase() } ||
            !requestParts[1].startsWith('/') || requestParts[1].startsWith("//") || requestParts[1].contains('#')) {
            throw IllegalArgumentException("Invalid HTTP request line")
        }

        val requestHeaders = HashMap<String, String>()
        for (line in lines.drop(1)) {
            if (line.isEmpty() || line.first().isWhitespace()) throw IllegalArgumentException("Invalid HTTP header line")
            val separator = line.indexOf(':')
            if (separator <= 0) throw IllegalArgumentException("Invalid HTTP header")
            val name = line.substring(0, separator)
            if (name.any { !it.isLetterOrDigit() && it !in "!#$%&'*+-.^_`|~" }) {
                throw IllegalArgumentException("Invalid HTTP header name")
            }
            val value = line.substring(separator + 1).trim()
            if (value.any { (it.code < 0x20 && it != '\t') || it.code == 0x7f }) {
                throw IllegalArgumentException("Invalid HTTP header value")
            }
            val key = name.lowercase()
            if (requestHeaders.put(key, value) != null) {
                throw IllegalArgumentException("Duplicate HTTP header")
            }
        }
        if (requestHeaders["host"].isNullOrBlank()) throw IllegalArgumentException("Missing HTTP Host header")
        if ("transfer-encoding" in requestHeaders || "expect" in requestHeaders) {
            throw IllegalArgumentException("Unsupported HTTP framing")
        }
        val rawLength = requestHeaders["content-length"]
            ?: throw IllegalArgumentException("Missing Content-Length")
        if (rawLength.isEmpty() || rawLength.any { !it.isDigit() }) {
            throw IllegalArgumentException("Invalid Content-Length")
        }
        val contentLength = rawLength.toIntOrNull() ?: throw RequestTooLargeException()
        if (contentLength > MAX_BODY_BYTES) throw RequestTooLargeException()
        val contentType = requestHeaders["content-type"]?.substringBefore(';')?.trim()
        if (!contentType.equals("application/json", ignoreCase = true)) {
            throw IllegalArgumentException("Unsupported content type")
        }
        val body = ByteArray(contentLength)
        var filled = 0
        while (filled < contentLength) {
            checkDeadline()
            val count = input.read(body, filled, contentLength - filled)
            checkDeadline()
            if (count < 0) throw IllegalArgumentException("Truncated request body")
            if (count > 0) filled += count
        }
        checkDeadline()
        val target = requestParts[1]
        return HttpRequest(requestParts[0], target.substringBefore('?'), body)
    }

    private fun dispatchHttp(request: HttpRequest, peerAddress: InetAddress): HttpResponse {
        if (!running) return plain(503, "Phone link is stopped")
        if (request.method != "POST") return plain(405, "POST required")
        return when (request.path) {
            PAIR_PATH -> pair(request.body, peerAddress)
            RPC_PATH -> rpc(request.body)
            WAKE_PATH -> wake()
            else -> plain(404, "Unknown endpoint")
        }
    }

    private fun pair(envelope: ByteArray, peerAddress: InetAddress): HttpResponse = synchronized(pairingLock) {
        val secret = pairingKey
        if (!running || secret == null || SystemClock.elapsedRealtime() >= pairingExpiresElapsed) {
            pairingKey = null
            pairingExpiresElapsed = 0L
            return@synchronized plain(403, "Pairing is not open")
        }
        val peer = peerAddress.hostAddress ?: return@synchronized plain(403, "Pairing code not accepted")
        if (!pairingAttemptLimiter.allow(peer, SystemClock.elapsedRealtime())) {
            return@synchronized plain(429, "Too many pairing attempts; try again shortly")
        }
        try {
            if (!rememberNonce(envelope)) return@synchronized plain(409, "Request already used")
            val request = CompanionCrypto.decryptJson(secret, PAIR_PATH, envelope)
            if (request.optString("op") != "pair") return@synchronized plain(400, "Invalid pairing request")
            val token = Base64.getDecoder().decode(request.getString("token"))
            if (token.size != CompanionCrypto.TOKEN_BYTES) return@synchronized plain(400, "Invalid pairing token")
            val client = request.optString("client_name", "Android phone").trim().take(60).ifBlank { "Android phone" }
            preferences.edit()
                .putString(KEY_TOKEN, Base64.getEncoder().encodeToString(token))
                .putString(KEY_CLIENT_NAME, client)
                .putLong(KEY_PAIRED_AT, System.currentTimeMillis())
                .apply()
            pairingKey = null // one pairing code can authorize one phone once
            pairingExpiresElapsed = 0L
            pairingAttemptLimiter.recordSuccess(peer)
            clearReplayCache()
            val response = CompanionCrypto.encryptJson(secret, PAIR_PATH, JSONObject().put("ok", true))
            updateStatus("Paired to $client. Close this screen or revoke to end access.")
            HttpResponse(200, response)
        } catch (_: Exception) {
            Log.i(TAG, "Pairing request rejected")
            plain(403, "Pairing code not accepted")
        }
    }

    private fun rpc(envelope: ByteArray): HttpResponse {
        val token = storedToken() ?: return plain(403, "Pair this phone on the TV first")
        return try {
            if (!rememberNonce(envelope)) return plain(409, "Request already used")
            val request = CompanionCrypto.decryptJson(token, RPC_PATH, envelope)
            val sentAt = request.optLong("sent_at_ms", 0L)
            if (sentAt == 0L || abs(System.currentTimeMillis() - sentAt) > REQUEST_MAX_AGE_MS) {
                return plain(409, "Request expired; reconnect and try again")
            }
            val response = handleRpc(request)
            val encrypted = CompanionCrypto.encryptJson(token, RPC_PATH, response)
            HttpResponse(200, encrypted)
        } catch (e: Exception) {
            Log.i(TAG, "Authenticated RPC rejected: ${e.javaClass.simpleName}")
            plain(403, "Request not accepted")
        }
    }

    private fun handleRpc(request: JSONObject): JSONObject = try {
        when (request.optString("op")) {
            "status" -> statusResponse()
            "profiles" -> profilesResponse()
            "activate_profile" -> activateProfile(request.getString("id"))
            "set_enabled" -> setEnabled(request.getBoolean("enabled"))
            "set_mode" -> setMode(request.getString("mode"))
            "start_measurement" -> startMeasurement(request)
            "repeat_measurement" -> startRepeatMeasurement(request)
            "upload_measurement" -> uploadMeasurement(request)
            "save_first_capture" -> saveFirstCapture(request)
            "cancel_measurement" -> cancelMeasurement(request)
            "revoke" -> {
                val response = JSONObject().put("ok", true)
                revokePairing()
                response
            }
            else -> JSONObject().put("ok", false).put("error", "Unknown Core EQ command")
        }
    } catch (e: MeasurementException) {
        val message = e.message ?: "The measurement could not be trusted."
        val measurementOperation = isMeasurementOperation(request.optString("op"))
        val preserved = measurementOperation && preserveFirstForRepeatRetry(message)
        if (measurementOperation && !preserved) failMeasurement(message)
        JSONObject().put("ok", false).put("error", message.take(180))
    } catch (e: Exception) {
        val message = (e.message ?: "Command failed").take(180)
        val measurementOperation = isMeasurementOperation(request.optString("op"))
        if (!measurementOperation || !preserveFirstForRepeatRetry(message)) {
            Log.w(TAG, "Core EQ companion command failed", e)
        }
        JSONObject().put("ok", false).put("error", message)
    }

    private fun isMeasurementOperation(operation: String): Boolean = operation in setOf(
        "start_measurement", "repeat_measurement", "upload_measurement", "save_first_capture", "cancel_measurement"
    )

    @Synchronized
    private fun preserveFirstForRepeatRetry(reason: String): Boolean {
        val session = measurement ?: return false
        if (session.phase !in setOf(MeasurementPhase.WAITING_FOR_REPEAT, MeasurementPhase.REPEAT_SWEEP) ||
            session.firstResult == null
        ) return false
        stimulusPlayer.stop()
        clearMeasurementTimeout()
        session.phase = MeasurementPhase.WAITING_FOR_REPEAT
        session.startedElapsedMs = SystemClock.elapsedRealtime()
        lastError = null
        scheduleMeasurementTimeout(session)
        updateStatus("The optional repeat operation was not completed ($reason). The first capture remains available to save or compare again.")
        return true
    }

    private fun statusResponse(): JSONObject {
        val output = OutputRoute.current(appContext)
        val profile = profileStore.getActiveProfile()
        val status = profileStore.status()
        return JSONObject()
            .put("ok", true)
            .put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("output_kind", output?.kind ?: JSONObject.NULL)
            .put("output_name", output?.name ?: JSONObject.NULL)
            .put("output_evidence", output?.let { OutputRoute.evidenceDescription(it.evidence) } ?: JSONObject.NULL)
            .put("correction_enabled", profileStore.correctionEnabled)
            .put("profile_id", profile?.id ?: JSONObject.NULL)
            .put("profile_name", profile?.name ?: JSONObject.NULL)
            .put("mode", modeStore.currentDecision().mode.key)
            .put("service_status", status?.message ?: "No status reported")
            .put("service_error", status?.isError ?: false)
            .put("paired_client", pairedClientName() ?: "")
    }

    private fun profilesResponse(): JSONObject {
        val chosen = profileStore.chosenId()
        val profiles = JSONArray()
        profileStore.getAllProfiles().take(MAX_PROFILES).forEach { profile ->
            profiles.put(JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("output_kind", profile.outputKind ?: "unknown")
                .put("output_name", profile.outputName ?: "")
                .put("active", profile.id == chosen)
                .put("manual_only", profile.manualOnly))
        }
        return JSONObject().put("ok", true).put("profiles", profiles)
    }

    @Synchronized
    private fun activateProfile(id: String): JSONObject {
        check(running) { "Local phone link is stopped." }
        check(measurement == null) { "TV controls are paused during a phone measurement." }
        val profile = profileStore.getAllProfiles().firstOrNull { it.id == id }
            ?: return JSONObject().put("ok", false).put("error", "That profile no longer exists on the TV")
        profileStore.setActiveProfile(profile.id)
        EqService.send(appContext, EqService.ACTION_REAPPLY)
        updateStatus("Phone selected ${profile.name}.")
        return JSONObject().put("ok", true).put("profile_id", profile.id).put("profile_name", profile.name)
    }

    @Synchronized
    private fun setEnabled(enabled: Boolean): JSONObject {
        check(running) { "Local phone link is stopped." }
        check(measurement == null) { "TV controls are paused during a phone measurement." }
        if (enabled) {
            val refused = EqService.enable(appContext)
            if (refused != null) return JSONObject().put("ok", false).put("error", refused)
        } else {
            EqService.disable(appContext)
        }
        updateStatus(if (enabled) "Correction enabled from the phone." else "Correction turned off from the phone.")
        return JSONObject().put("ok", true).put("correction_enabled", profileStore.correctionEnabled)
    }

    @Synchronized
    private fun setMode(key: String): JSONObject {
        check(running) { "Local phone link is stopped." }
        check(measurement == null) { "TV controls are paused during a phone measurement." }
        val mode = ContentMode.entries.firstOrNull { it.key == key }
            ?: return JSONObject().put("ok", false).put("error", "Unknown listening mode")
        modeStore.selectMode(mode)
        EqService.send(appContext, EqService.ACTION_REAPPLY)
        updateStatus("Phone selected ${mode.title} mode.")
        return JSONObject().put("ok", true).put("mode", mode.key).put("title", mode.title)
    }

    @Synchronized
    private fun startMeasurement(request: JSONObject): JSONObject {
        check(running) { "Local phone link is stopped." }
        val target = request.optString("target", "dialogue")
        if (target !in SUPPORTED_TARGETS) throw MeasurementException("Choose a supported target first.")
        val room = if (request.has("volume_m3") && !request.isNull("volume_m3")) request.optDouble("volume_m3", Double.NaN) else null
        if (room != null && (!room.isFinite() || room !in MIN_ROOM_VOLUME_M3..MAX_ROOM_VOLUME_M3)) {
            throw MeasurementException("Room size is outside the supported range.")
        }
        val prior = measurement
        if (prior != null && SystemClock.elapsedRealtime() - prior.startedElapsedMs < MEASUREMENT_TIMEOUT_MS) {
            throw MeasurementException("A phone measurement is already waiting for its capture.")
        }
        if (prior != null) {
            if (hasRetainedFirstCapture(prior)) {
                try {
                    stimulusPlayer.stop()
                    val profile = saveRetainedFirstCapture(prior)
                    finishPhoneMeasurement()
                    updateStatus("A previous phone repeat expired; its first sweep was saved as ${profile.name} without comparison or averaging.")
                } catch (_: Exception) {
                    failMeasurement("A previous phone repeat expired and its first sweep could not be saved; correction resumed.")
                }
            } else {
                failMeasurement("A previous phone measurement expired; correction resumed.")
            }
        }
        val initialOutput = OutputRoute.current(appContext)
        val session = MeasurementSession(
            id = UUID.randomUUID().toString(),
            target = target,
            volumeM3 = room,
            startedElapsedMs = SystemClock.elapsedRealtime(),
            repeatRequested = request.optBoolean("repeatability", false),
            output = initialOutput,
            activeOutput = initialOutput
        )
        measurement = session
        lastError = null
        // Do not let the correction being measured colour its own sweep.
        EqService.send(appContext, EqService.ACTION_SUSPEND)
        startSweep(session)
        updateStatus("The phone is recording; the TV has started the calibration sweep.")
        return measurementStartResponse(session)
    }

    @Synchronized
    private fun startRepeatMeasurement(request: JSONObject): JSONObject {
        check(running) { "Local phone link is stopped." }
        val session = measurement ?: throw MeasurementException("No repeat is waiting on the TV.")
        if (request.optString("session_id") != session.id) {
            throw MeasurementException("That repeat session expired; start a new phone measurement.")
        }
        if (!session.repeatRequested || session.phase != MeasurementPhase.WAITING_FOR_REPEAT || session.firstResult == null) {
            throw MeasurementException("The TV is not waiting for a repeat capture.")
        }
        if (SystemClock.elapsedRealtime() - session.startedElapsedMs > MEASUREMENT_TIMEOUT_MS) {
            throw MeasurementException("The repeat window expired. The first capture remains available to save on its own.")
        }
        session.phase = MeasurementPhase.REPEAT_SWEEP
        session.startedElapsedMs = SystemClock.elapsedRealtime()
        session.activeOutput = OutputRoute.current(appContext)
        session.repeatOutput = session.activeOutput
        lastError = null
        startSweep(session)
        updateStatus("The phone is recording the same-position repeat sweep.")
        return measurementStartResponse(session).put("pass", 2)
    }

    private fun measurementStartResponse(session: MeasurementSession): JSONObject = JSONObject()
        .put("ok", true)
        .put("session_id", session.id)
        .put("capture_seconds", CAPTURE_LEAD_SECONDS + Sweep.STIMULUS_SECONDS + SweepAnalysis.TAIL_SECONDS)
        .put("stimulus_seconds", Sweep.STIMULUS_SECONDS)

    /** The phone is recording before this is called, so its clock markers bracket the TV sweep. */
    private fun startSweep(session: MeasurementSession) {
        val id = session.id
        val sweepPhase = session.phase
        if (sweepPhase == MeasurementPhase.FIRST_SWEEP) {
            session.activeOutput = session.output
        }
        try {
            // Keep the same acoustic lead as the TV's own capture screen so
            // AudioTrack setup does not eat the sweep tail.
            Thread.sleep(CAPTURE_LEAD_MS)
            check(running && measurement?.id == id) { "Measurement stopped before playback started" }
            stimulusPlayer.playSweep(
                onError = { message ->
                    if (measurement?.id == id && session.phase == sweepPhase) {
                        lastError = message
                        updateStatus(message)
                    }
                },
                onRouted = { devices ->
                    if (measurement?.id == id && session.phase == sweepPhase) {
                        val tagged = OutputRoute.tag(session.activeOutput, devices)
                        session.activeOutput = tagged
                        when (sweepPhase) {
                            MeasurementPhase.FIRST_SWEEP -> session.output = tagged
                            MeasurementPhase.REPEAT_SWEEP -> session.repeatOutput = tagged
                            MeasurementPhase.WAITING_FOR_REPEAT -> Unit
                        }
                        updateStatus("Playing the calibration sweep to ${tagged?.name ?: "the TV audio output"}.")
                    }
                }
            )
            scheduleMeasurementTimeout(session)
        } catch (e: Exception) {
            if (measurement?.id == id && session.phase == MeasurementPhase.FIRST_SWEEP) {
                measurement = null
                clearMeasurementTimeout()
                stimulusPlayer.stop()
                EqService.send(appContext, EqService.ACTION_RESUME)
            }
            throw e
        }
    }

    @Synchronized
    private fun uploadMeasurement(request: JSONObject): JSONObject {
        check(running) { "Local phone link is stopped." }
        val session = measurement ?: throw MeasurementException("No phone measurement is waiting on the TV.")
        val sessionId = request.optString("session_id", "")
        if (sessionId != session.id) throw MeasurementException("That measurement session expired; start a new one.")
        if (SystemClock.elapsedRealtime() - session.startedElapsedMs > MEASUREMENT_TIMEOUT_MS) {
            throw MeasurementException("The phone capture arrived too late. Start the measurement again.")
        }
        if (session.phase == MeasurementPhase.WAITING_FOR_REPEAT) {
            throw MeasurementException("The TV is waiting for the repeat sweep to start.")
        }
        if (session.phase == MeasurementPhase.REPEAT_SWEEP && session.firstResult == null) {
            throw MeasurementException("The first sweep is no longer available for comparison.")
        }
        lastError?.let { throw MeasurementException(it) }
        val rate = request.optInt("sample_rate_hz", 0)
        if (rate !in SUPPORTED_SAMPLE_RATES) throw MeasurementException("The phone reported an unsupported microphone sample rate.")
        val pcmText = request.optString("pcm16_le_base64", "")
        if (pcmText.length > MAX_PCM_BASE64_CHARS) throw MeasurementException("The phone recording is too large to accept.")
        val pcm = try {
            Base64.getDecoder().decode(pcmText)
        } catch (_: IllegalArgumentException) {
            throw MeasurementException("The phone recording was not valid PCM audio.")
        }
        val minimumBytes = (rate * MIN_CAPTURE_SECONDS * 2.0).toInt()
        val maximumBytes = (rate * MAX_CAPTURE_SECONDS * 2.0).toInt()
        if (pcm.size < minimumBytes || pcm.size > maximumBytes || pcm.size % 2 != 0) {
            throw MeasurementException("The phone recording length does not include a complete sweep and decay tail.")
        }
        val samples = DoubleArray(pcm.size / 2) { i ->
            val value = (pcm[i * 2].toInt() and 0xff) or (pcm[i * 2 + 1].toInt() shl 8)
            value.toShort().toDouble() / Short.MAX_VALUE
        }
        var peak = 0.0
        for (sample in samples) peak = maxOf(peak, abs(sample))
        val peakDbfs = 20.0 * log10(maxOf(peak, 1e-9))
        if (peakDbfs > CLIP_DBFS) {
            throw MeasurementException("The phone microphone clipped (${String.format(java.util.Locale.US, "%.1f", peakDbfs)} dBFS). Lower the TV volume and measure again.")
        }
        val captureInfo = parseCaptureInfo(request.optJSONObject("capture"), rate)
        val result = try {
            SweepAnalysis.analyze(samples, rate, session.target, session.volumeM3)
        } catch (e: MeasurementException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Phone capture analysis failed", e)
            throw MeasurementException("Analysis failed: ${e.javaClass.simpleName}.")
        }
        val captureSeconds = samples.size.toDouble() / rate
        if (session.phase == MeasurementPhase.FIRST_SWEEP && session.repeatRequested) {
            stimulusPlayer.stop()
            session.firstResult = result
            session.firstCaptureInfo = captureInfo
            session.firstCaptureSeconds = captureSeconds
            session.output = session.activeOutput
            session.phase = MeasurementPhase.WAITING_FOR_REPEAT
            session.startedElapsedMs = SystemClock.elapsedRealtime()
            lastError = null
            scheduleMeasurementTimeout(session)
            updateStatus("First phone sweep analysed; waiting for the same-position repeat.")
            return JSONObject()
                .put("ok", true)
                .put("awaiting_repeat", true)
                .put("session_id", session.id)
                .put("snr_db", result.snrDb)
                .put("quality_score", MeasurementQuality.score(result))
        }

        val isRepeat = session.phase == MeasurementPhase.REPEAT_SWEEP
        val primaryResult = if (isRepeat) session.firstResult
            ?: throw MeasurementException("The first sweep was lost; start a new phone measurement.") else result
        val primaryCapture = if (isRepeat) session.firstCaptureInfo
            ?: throw MeasurementException("The first microphone metadata was lost; start a new phone measurement.") else captureInfo
        val primarySeconds = if (isRepeat) session.firstCaptureSeconds
            ?: throw MeasurementException("The first capture duration was lost; start a new phone measurement.") else captureSeconds
        val primaryOutput = if (isRepeat) session.output else session.activeOutput
        val repeatCheck = if (isRepeat) buildRepeatabilityCheck(
            session = session,
            repeatedResult = result,
            repeatedCaptureInfo = captureInfo
        ) else null
        val now = System.currentTimeMillis()
        val profile = profileFromMeasurement(
            primaryResult,
            session,
            primaryOutput,
            primaryCapture,
            primarySeconds,
            now,
            repeatabilityCheck = repeatCheck
        )
        profileStore.saveProfile(profile, setAsActive = true)
        updateStatus("Phone measurement saved as ${profile.name}.")
        val response = JSONObject()
            .put("ok", true)
            .put("profile_id", profile.id)
            .put("profile_name", profile.name)
            .put("snr_db", primaryResult.snrDb)
            .put("quality_score", MeasurementQuality.score(primaryResult))
            .put("output_kind", profile.outputKind)
            .put("output_name", profile.outputName ?: "")
        if (repeatCheck != null) response.put("repeatability", repeatabilityJson(repeatCheck))
        finishPhoneMeasurement()
        return response
    }

    @Synchronized
    private fun saveFirstCapture(request: JSONObject): JSONObject {
        check(running) { "Local phone link is stopped." }
        val session = measurement ?: throw MeasurementException("No first phone capture is waiting to save.")
        if (request.optString("session_id") != session.id) {
            throw MeasurementException("That repeat session expired; start a new phone measurement.")
        }
        if (!session.repeatRequested || session.firstResult == null ||
            session.phase !in setOf(MeasurementPhase.WAITING_FOR_REPEAT, MeasurementPhase.REPEAT_SWEEP)
        ) {
            throw MeasurementException("There is no complete first phone capture to save.")
        }
        val result = session.firstResult!!
        stimulusPlayer.stop()
        val profile = saveRetainedFirstCapture(session)
        updateStatus("First phone sweep saved as ${profile.name}; the repeat was not compared.")
        val response = JSONObject()
            .put("ok", true)
            .put("profile_id", profile.id)
            .put("profile_name", profile.name)
            .put("snr_db", result.snrDb)
            .put("quality_score", MeasurementQuality.score(result))
            .put("output_kind", profile.outputKind)
            .put("output_name", profile.outputName ?: "")
            .put("repeatability_note", "Only the first capture was saved; no repeat comparison or averaging was applied.")
        finishPhoneMeasurement()
        return response
    }

    private fun hasRetainedFirstCapture(session: MeasurementSession): Boolean =
        session.repeatRequested && session.firstResult != null && session.firstCaptureInfo != null &&
            session.firstCaptureSeconds != null &&
            session.phase in setOf(MeasurementPhase.WAITING_FOR_REPEAT, MeasurementPhase.REPEAT_SWEEP)

    /** Persist the retained first sweep unchanged when an optional repeat cannot finish. */
    private fun saveRetainedFirstCapture(session: MeasurementSession): Profile {
        val result = session.firstResult ?: throw MeasurementException("The first capture is no longer available.")
        val captureInfo = session.firstCaptureInfo
            ?: throw MeasurementException("The first microphone metadata was lost; start a new phone measurement.")
        val captureSeconds = session.firstCaptureSeconds
            ?: throw MeasurementException("The first capture duration was lost; start a new phone measurement.")
        val profile = profileFromMeasurement(
            result,
            session,
            session.output,
            captureInfo,
            captureSeconds,
            System.currentTimeMillis(),
            repeatabilityCheck = null
        )
        profileStore.saveProfile(profile, setAsActive = true)
        return profile
    }

    private fun buildRepeatabilityCheck(
        session: MeasurementSession,
        repeatedResult: SweepResult,
        repeatedCaptureInfo: CaptureInfo
    ): RepeatabilityCheck {
        val firstResult = session.firstResult ?: throw MeasurementException("The first capture was lost.")
        val firstCaptureInfo = session.firstCaptureInfo
        val repeatOutput = session.repeatOutput ?: session.activeOutput
        val routeMismatch = RepeatabilityAnalysis.reportedOutputMismatch(
            OutputRoute.keyFor(session.output), session.output?.name,
            OutputRoute.keyFor(repeatOutput), repeatOutput?.name
        )
        val inputMismatch = RepeatabilityAnalysis.reportedInputMismatch(firstCaptureInfo, repeatedCaptureInfo)
        val mismatch = routeMismatch ?: inputMismatch
        val comparison = RepeatabilityAnalysis.compare(firstResult, repeatedResult)
        val metrics = if (mismatch == null) comparison.metrics else null
        val inputUnknown = listOf(firstCaptureInfo, repeatedCaptureInfo).any { info ->
            info == null || (info.inputDeviceType.isNullOrBlank() && info.inputDeviceName.isNullOrBlank())
        }
        val routeUnknown = session.output == null || repeatOutput == null ||
            OutputRoute.keyFor(session.output) == OutputRoute.UNKNOWN || OutputRoute.keyFor(repeatOutput) == OutputRoute.UNKNOWN
        val note = when {
            mismatch != null -> "$mismatch No same-path repeatability summary was calculated."
            metrics == null -> comparison.reason ?: "There were not enough comparable bands for a repeatability summary."
            inputUnknown || routeUnknown -> "Android did not report enough input/output route detail for both sweeps; treat the comparison as provisional. No pass/fail threshold has been physically qualified."
            else -> "Descriptive same-position comparison only; no physically qualified pass/fail threshold. Captures are not averaged."
        }
        return RepeatabilityCheck(
            metrics = metrics,
            comparisonCurve = repeatedResult.centresHz.indices.map { index ->
                CurvePoint(repeatedResult.centresHz[index], repeatedResult.measuredDb[index], repeatedResult.correctionDb[index])
            },
            captureInfo = repeatedCaptureInfo,
            outputKind = OutputRoute.keyFor(repeatOutput),
            outputName = repeatOutput?.name,
            snrDb = repeatedResult.snrDb,
            driftPpm = repeatedResult.driftPpm,
            note = note
        )
    }

    private fun repeatabilityJson(check: RepeatabilityCheck): JSONObject {
        val output = JSONObject()
        check.metrics?.let { metrics ->
            output.put("metrics", JSONObject()
                .put("compared_bands", metrics.comparedBands)
                .put("comparison_range_hz", JSONArray().put(metrics.lowFrequencyHz).put(metrics.highFrequencyHz))
                .put("level_offset_db", metrics.levelOffsetDb)
                .put("median_abs_difference_db", metrics.medianAbsDifferenceDb)
                .put("rms_difference_db", metrics.rmsDifferenceDb)
                .put("p95_abs_difference_db", metrics.p95AbsDifferenceDb))
        }
        check.note?.let { output.put("note", it.take(400)) }
        return output
    }

    @Synchronized
    private fun finishPhoneMeasurement() {
        measurement = null
        lastError = null
        clearMeasurementTimeout()
        // Resume applies the saved profile (or the previous one if saving failed).
        EqService.send(appContext, EqService.ACTION_RESUME)
    }

    private fun profileFromMeasurement(
        result: SweepResult,
        session: MeasurementSession,
        output: OutputRoute.Output?,
        captureInfo: CaptureInfo,
        captureSeconds: Double,
        now: Long,
        repeatabilityCheck: RepeatabilityCheck? = null
    ): Profile {
        val displayBandsHz = doubleArrayOf(60.0, 230.0, 910.0, 3_600.0, 14_000.0)
        val platformBands = Correction.collapseToBands(
            displayBandsHz,
            { hz -> interpolate(hz, result.centresHz, result.correctionDb) },
            -1500,
            1500
        ).map { PlatformBand(it.first, it.second) }
        val baseName = "Phone measurement · ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(now))}"
        val outputName = output?.name
        return Profile(
            id = "profile-$now",
            name = if (outputName != null) "$baseName · $outputName" else baseName,
            timestampMs = now,
            target = session.target,
            micType = captureInfo.captureDeviceModel?.let { "$it microphone" } ?: "phone microphone",
            captureInfo = captureInfo,
            repeatabilityCheck = repeatabilityCheck,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            stimulus = "sweep_${Sweep.SECONDS.toInt()}s_phone_capture",
            captureSeconds = captureSeconds,
            volumeM3 = session.volumeM3,
            rt60Seconds = result.rt60Seconds,
            schroederHz = result.schroederHz,
            transitionHz = result.transitionHz,
            rolloffHz = result.floorHz,
            snrDb = result.snrDb,
            qualityScore = MeasurementQuality.score(result),
            nullsUntouchedHz = result.centresHz.indices.filter { result.nullMask[it] }.map { result.centresHz[it] },
            preampDb = result.preampDb,
            filters = result.filters,
            platformBands = platformBands,
            curve = result.centresHz.indices.map { index ->
                CurvePoint(result.centresHz[index], result.measuredDb[index], result.correctionDb[index])
            },
            outputKind = OutputRoute.keyFor(output),
            outputName = outputName,
            measurementNotes = buildList {
                add("Phone microphone capture: device response and Android audio processing may vary by model; Android microphone metadata is stored, not applied as calibration.")
                if (repeatabilityCheck != null) {
                    add("Optional same-position repeat retained as a separate trace; captures are never averaged, no physically qualified pass/fail threshold is applied, and results do not generalize to other seats or rooms.")
                    repeatabilityCheck.note?.let { note -> add(note) }
                } else if (session.repeatRequested) {
                    add("Optional same-position repeat did not complete; only the first sweep was saved, with no comparison or averaging.")
                }
            }
        )
    }

    private fun parseCaptureInfo(value: JSONObject?, sampleRate: Int): CaptureInfo {
        val capture = value ?: JSONObject()
        val descriptions = readStrings(capture.optJSONArray("microphone_descriptions"))
        val processing = readStrings(capture.optJSONArray("processing"))
        val points = mutableListOf<MicrophoneResponsePoint>()
        capture.optJSONArray("microphone_frequency_response")?.let { array ->
            for (index in 0 until minOf(array.length(), MAX_RESPONSE_POINTS)) {
                val point = array.optJSONObject(index) ?: continue
                val hz = point.optDouble("frequency_hz", Double.NaN)
                val db = point.optDouble("response_db", Double.NaN)
                val label = point.optString("microphone", "phone microphone").take(80)
                if (hz > 0.0 && hz.isFinite() && db.isFinite()) points += MicrophoneResponsePoint(label, hz, db)
            }
        }
        return CaptureInfo(
            requestedSampleRateHz = capture.optInt("requested_sample_rate_hz", sampleRate).takeIf { it > 0 },
            inputSampleRateHz = sampleRate,
            analysisSampleRateHz = sampleRate,
            audioSource = capture.optString("audio_source", "VOICE_RECOGNITION").take(40),
            inputDeviceName = capture.optString("input_device_name", "").takeIf { it.isNotBlank() && it != "null" },
            inputDeviceType = capture.optString("input_device_type", "").takeIf { it.isNotBlank() && it != "null" },
            activeMicrophoneCount = capture.optInt("active_microphone_count", -1).takeIf { it >= 0 },
            captureDeviceModel = capture.optString("capture_device_model", "Android phone microphone").take(80),
            captureOsVersion = capture.optString("capture_os_version", "").takeIf { it.isNotBlank() }?.take(40),
            microphoneDescriptions = descriptions,
            processing = processing,
            unprocessedAudioSourceSupported = if (capture.has("unprocessed_audio_source_supported")) {
                when (capture.opt("unprocessed_audio_source_supported")) {
                    true -> true
                    false -> false
                    else -> null
                }
            } else null,
            microphoneResponse = points
        )
    }

    private fun readStrings(array: JSONArray?): List<String> = buildList {
        if (array == null) return@buildList
        for (index in 0 until minOf(array.length(), MAX_METADATA_STRINGS)) {
            val text = array.optString(index, "").trim().take(120)
            if (text.isNotEmpty()) add(text)
        }
    }

    private fun interpolate(hz: Double, frequencies: DoubleArray, values: DoubleArray): Double {
        if (frequencies.isEmpty() || hz < frequencies.first() || hz > frequencies.last()) return 0.0
        for (index in 0 until frequencies.lastIndex) {
            val a = frequencies[index]
            val b = frequencies[index + 1]
            if (hz in a..b) {
                val t = kotlin.math.ln(hz / a) / kotlin.math.ln(b / a)
                return values[index] + t * (values[index + 1] - values[index])
            }
        }
        return values.last()
    }

    @Synchronized
    private fun cancelMeasurement(request: JSONObject): JSONObject {
        check(running) { "Local phone link is stopped." }
        val session = measurement ?: return JSONObject().put("ok", true).put("cancelled", false)
        if (request.optString("session_id") != session.id) return JSONObject().put("ok", false).put("error", "Measurement session does not match")
        stimulusPlayer.stop()
        measurement = null
        lastError = null
        clearMeasurementTimeout()
        EqService.send(appContext, EqService.ACTION_RESUME)
        updateStatus("Phone measurement cancelled; nothing was saved.")
        return JSONObject().put("ok", true).put("cancelled", true)
    }

    private fun scheduleMeasurementTimeout(session: MeasurementSession) {
        clearMeasurementTimeout()
        val task = object : Runnable {
            override fun run() {
                synchronized(this@CompanionServer) {
                    val current = measurement
                    if (current?.id != session.id) return
                    val remaining = MEASUREMENT_TIMEOUT_MS - (SystemClock.elapsedRealtime() - current.startedElapsedMs)
                    if (remaining <= 0L) {
                        if (hasRetainedFirstCapture(current)) {
                            stimulusPlayer.stop()
                            try {
                                val profile = saveRetainedFirstCapture(current)
                                finishPhoneMeasurement()
                                updateStatus("Phone repeat timed out; the first sweep was saved as ${profile.name} without comparison or averaging.")
                            } catch (e: Exception) {
                                failMeasurement("Phone measurement timed out and the first sweep could not be saved (${e.message ?: e.javaClass.simpleName}); correction resumed.")
                            }
                        } else {
                            failMeasurement("Phone measurement timed out; the sweep stopped and correction resumed.")
                        }
                    } else {
                        mainHandler.postDelayed(this, remaining)
                    }
                }
            }
        }
        measurementTimeout = task
        mainHandler.postDelayed(task, MEASUREMENT_TIMEOUT_MS)
    }

    private fun clearMeasurementTimeout() {
        measurementTimeout?.let(mainHandler::removeCallbacks)
        measurementTimeout = null
    }

    @Synchronized
    private fun failMeasurement(message: String) {
        stimulusPlayer.stop()
        measurement = null
        lastError = null
        clearMeasurementTimeout()
        EqService.send(appContext, EqService.ACTION_RESUME)
        updateStatus(message)
    }

    private fun statusString(): String = when {
        !running -> "Local phone link is stopped."
        isPaired() -> "Paired to ${pairedClientName() ?: "Android phone"}; connection is local-only while this screen remains open."
        else -> "Open Core EQ Companion on the phone and enter this TV's IP, port and temporary pairing code."
    }

    private fun updateStatus(message: String) {
        mainHandler.post { onStatus(message) }
    }

    private fun storedToken(): ByteArray? = try {
        preferences.getString(KEY_TOKEN, null)?.let { Base64.getDecoder().decode(it) }
            ?.takeIf { it.size == CompanionCrypto.TOKEN_BYTES }
    } catch (_: Exception) {
        null
    }

    private fun rememberNonce(envelope: ByteArray): Boolean {
        return try {
            val nonce = JSONObject(String(envelope, StandardCharsets.UTF_8)).getString("nonce")
            synchronized(nonceLock) {
                if (nonce in replayedNonces) false else {
                    replayedNonces += nonce
                    while (replayedNonces.size > MAX_REPLAYED_NONCES) {
                        replayedNonces.iterator().let { iterator -> if (iterator.hasNext()) { iterator.next(); iterator.remove() } }
                    }
                    true
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun clearReplayCache() {
        synchronized(nonceLock) { replayedNonces.clear() }
    }

    /** Unauthenticated: opens the pairing screen at most once per [WakeLimiter.MIN_INTERVAL_MS]. */
    private fun wake(): HttpResponse {
        val now = SystemClock.elapsedRealtime()
        val accepted = synchronized(wakeLock) {
            if (WakeLimiter.shouldWake(now, lastWakeMs)) {
                lastWakeMs = now
                true
            } else false
        }
        if (accepted) onWake?.invoke()
        return HttpResponse(200, JSONObject().put("ok", true).toString().toByteArray(StandardCharsets.UTF_8))
    }

    private fun plain(status: Int, message: String): HttpResponse =
        HttpResponse(status, JSONObject().put("error", message).toString().toByteArray(StandardCharsets.UTF_8))

    /** Stop accepting or serving sockets synchronously when the companion screen is hidden. */
    fun stopListening() {
        synchronized(pairingLock) {
            running = false
            pairingKey = null
            pairingExpiresElapsed = 0L
        }
        synchronized(listenerLock) {
            try {
                server.close()
            } catch (_: Exception) {
                // Already closed.
            }
            activeSockets.toList().forEach { socket ->
                try { socket.close() } catch (_: Exception) { }
            }
            activeSockets.clear()
            workers.shutdownNow()
        }
    }

    override fun close() {
        stopListening()
        synchronized(this) {
            pairingKey = null
            pairingExpiresElapsed = 0L
            val currentMeasurement = measurement
            val retainedSession = currentMeasurement?.takeIf { session -> hasRetainedFirstCapture(session) }
            if (retainedSession != null) {
                try {
                    stimulusPlayer.stop()
                    val profile = saveRetainedFirstCapture(retainedSession)
                    updateStatus("Phone link closed during the optional repeat; the first sweep was saved as ${profile.name} without comparison or averaging.")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not save the retained phone sweep while closing", e)
                }
            }
            val hadMeasurement = measurement != null
            measurement = null
            lastError = null
            clearMeasurementTimeout()
            try {
                stimulusPlayer.stop()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not stop the phone-measurement stimulus", e)
            }
            if (hadMeasurement) EqService.send(appContext, EqService.ACTION_RESUME)
        }
        activeSockets.clear()
        clearReplayCache()
    }

    private class RequestTooLargeException : RuntimeException()
    private class RequestHeaderTooLargeException : RuntimeException()
    private class RequestTimeoutException : RuntimeException()

    companion object {
        const val PAIR_PATH = "/v1/pair"
        const val RPC_PATH = "/v1/rpc"
        const val WAKE_PATH = "/v1/wake"
        private const val PREFS = "core_eq_companion"
        private const val KEY_TOKEN = "paired_phone_token"
        private const val KEY_CLIENT_NAME = "paired_phone_name"
        private const val KEY_PAIRED_AT = "paired_phone_at"
        private const val BACKLOG = 8
        private const val WORKER_COUNT = 4
        private const val MAX_QUEUED_CONNECTIONS = 4
        private const val MAX_ACTIVE_CONNECTIONS = WORKER_COUNT + MAX_QUEUED_CONNECTIONS
        private const val SOCKET_IDLE_TIMEOUT_MS = 5_000
        private const val MAX_REQUEST_DURATION_MS = 30_000L
        private const val MAX_HEADER_BYTES = 8192
        private const val MAX_BODY_BYTES = 3 * 1024 * 1024
        private const val MAX_PCM_BASE64_CHARS = 2_400_000
        private const val MAX_PROFILES = 100
        private const val MAX_REPLAYED_NONCES = 2048
        private const val PAIRING_WINDOW_MS = 120_000L
        private const val REQUEST_MAX_AGE_MS = 120_000L
        private const val MEASUREMENT_TIMEOUT_MS = 90_000L
        private const val CAPTURE_LEAD_SECONDS = 0.5
        private const val CAPTURE_LEAD_MS = 500L
        private const val MIN_CAPTURE_SECONDS = 8.0
        private const val MAX_CAPTURE_SECONDS = 30.0
        private const val CLIP_DBFS = -0.5
        private const val MIN_ROOM_VOLUME_M3 = 8.0
        private const val MAX_ROOM_VOLUME_M3 = 500.0
        private const val MAX_METADATA_STRINGS = 16
        private const val MAX_RESPONSE_POINTS = 256
        private val SUPPORTED_TARGETS = setOf("dialogue", "room", "bk", "flat", "olive", "house")
        private val SUPPORTED_SAMPLE_RATES = setOf(16_000, 44_100, 48_000)
        private const val TAG = "CoreEqCompanion"
    }
}
