package tv.corebuilds.eq.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import tv.corebuilds.eq.dsp.DspConstants
import tv.corebuilds.eq.dsp.ManualEq
import tv.corebuilds.eq.dsp.Peaking
import tv.corebuilds.eq.dsp.PeakingFilter
import tv.corebuilds.eq.mode.ContentMode
import java.io.File
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min

/**
 * Exporters for Poweramp Equalizer, Equalizer APO, and Core EQ profile JSON.
 */
object Formats {

    private const val PREAMP_ROUNDING_EPSILON_DB = 1e-9

    fun exportParametricTxt(profile: Profile): String {
        val filters = profile.filters + ManualEq.sanitize(profile.manualFilters)
        if (filters.isEmpty()) {
            return buildString {
                append("# Core EQ: no filters\n")
                for (note in profile.measurementNotes) append("# Core EQ measurement note: $note\n")
            }
        }
        val pDb = exportPreampDb(profile, filters)
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "Preamp: %.2f dB\n", pDb))
        sb.append(String.format(Locale.US, "# Core EQ: correction band %.0f-%.0f Hz\n", maxOf(DspConstants.F_MIN, profile.rolloffHz), DspConstants.F_MAX))
        sb.append("# Core EQ: set Bands Overlap to Cascade in Poweramp Equalizer,\n")
        sb.append("# Core EQ: or the filters will not sum the way this file assumes.\n")
        if (profile.manualFilters.isNotEmpty()) {
            sb.append("# Core EQ: manual EQ filters are included after the measured correction.\n")
        }
        for (note in profile.measurementNotes) {
            sb.append("# Core EQ measurement note: $note\n")
        }

        for (i in filters.indices) {
            val f = filters[i]
            val sign = if (f.gain >= 0) "+" else ""
            sb.append(String.format(Locale.US, "Filter %d: ON PK Fc %.0f Hz Gain %s%.2f dB Q %.2f\n",
                i + 1, f.fc, sign, f.gain, f.q))
        }
        return sb.toString()
    }

    fun exportGraphicEq(profile: Profile): String {
        val frequencies = if (profile.curve.isNotEmpty()) {
            profile.curve.map { it.hz }
        } else {
            DspConstants.ISO_CENTRES_HZ.filter { it in DspConstants.F_MIN..DspConstants.F_MAX }
        }
        val manualFilters = ManualEq.sanitize(profile.manualFilters)
        val responses = frequencies.map { hz ->
            profile.correctionAt(hz) + ManualEq.responseDb(manualFilters, hz)
        }
        val parts = frequencies.indices.map { index ->
            val response = responses[index]
            val sign = if (response >= 0) "+" else ""
            String.format(Locale.US, "%.0f %s%.2f", frequencies[index], sign, response)
        }
        val preamp = graphicEqPreampDb(profile)?.let { db ->
            String.format(Locale.US, "Preamp: %.2f dB\n", db)
        }.orEmpty()
        return preamp + "GraphicEQ: " + parts.joinToString("; ") + "\n"
    }

    /**
     * Headroom for the GraphicEQ points actually serialized, or null when the
     * base-only export intentionally has no preamp line (pre-existing behavior).
     */
    fun graphicEqPreampDb(profile: Profile): Double? {
        val manualFilters = ManualEq.sanitize(profile.manualFilters)
        if (manualFilters.isEmpty()) return null
        val frequencies = if (profile.curve.isNotEmpty()) {
            profile.curve.map { it.hz }
        } else {
            DspConstants.ISO_CENTRES_HZ.filter { it in DspConstants.F_MIN..DspConstants.F_MAX }
        }
        val peak = frequencies.maxOfOrNull { hz ->
            profile.correctionAt(hz) + ManualEq.responseDb(manualFilters, hz)
        } ?: 0.0
        // GraphicEQ serializes the measured curve, not the fitted parametric
        // filters. Preserve any more-conservative room-only reserve.
        return min(roomPreampDb(profile), conservativePreampDb(peak))
    }

    /** Headroom for a PA parametric export, preserving the measured correction's existing reserve. */
    fun recommendedPreampDb(profile: Profile): Double {
        val filters = profile.filters + ManualEq.sanitize(profile.manualFilters)
        val roomPreamp = roomPreampDb(profile)
        if (profile.manualFilters.isEmpty()) return roomPreamp
        return exportPreampDb(profile, filters, roomPreamp)
    }

    /** Room-only reserve persisted separately so changing a manual boost can restore headroom. */
    private fun roomPreampDb(profile: Profile): Double =
        if (profile.preampDb != 0.0) profile.preampDb else Peaking.preampDb(profile.filters)

    private fun exportPreampDb(profile: Profile, filters: List<PeakingFilter>): Double {
        return exportPreampDb(profile, filters, roomPreampDb(profile))
    }

    private fun exportPreampDb(profile: Profile, filters: List<PeakingFilter>, existing: Double): Double {
        if (profile.manualFilters.isEmpty()) return existing

        // Sample densely over Core EQ's trusted span to catch overlapping bands,
        // then never reduce the headroom the measured correction already requested.
        val sampleHz = ((0..240).map { index ->
            DspConstants.F_MIN * Math.pow(DspConstants.F_MAX / DspConstants.F_MIN, index / 240.0)
        } + filters.map { it.fc }).distinct().toDoubleArray()
        val peakDb = Peaking.filterSumDb(sampleHz, filters).maxOrNull() ?: 0.0
        return min(existing, conservativePreampDb(peakDb))
    }

    /** Round a peak reserve toward more-negative hundredths without a 0.01 dB float artifact. */
    private fun conservativePreampDb(peakDb: Double): Double =
        floor((-maxOf(0.0, peakDb) + PREAMP_ROUNDING_EPSILON_DB) * 100.0) / 100.0

    fun exportProfileJson(profile: Profile): String = exportProfileJson(profile, emptyMap(), selectedMode = null)

    /** Include all saved mode overlays when exporting a user-facing profile backup. */
    fun exportProfileJson(
        profile: Profile,
        modeOverlays: Map<ContentMode, List<PeakingFilter>>,
        selectedMode: ContentMode?
    ): String {
        val selectedProfile = selectedMode?.let { mode ->
            profile.copy(manualFilters = ManualEq.sanitize(profile.manualFilters + modeOverlays[mode].orEmpty()))
        } ?: profile
        val root = JSONObject()
        root.put("format", "corebuilds.core-eq/1")
        root.put("id", profile.id)
        root.put("name", profile.name)
        root.put("timestamp_ms", profile.timestampMs)
        root.put("device", profile.deviceName)
        root.put("manual_only", profile.manualOnly)
        profile.outputKind?.let { kind ->
            root.put("output", JSONObject().put("kind", kind).put("name", profile.outputName ?: JSONObject.NULL))
        }
        root.put("microphone", profile.micType)
        root.put("stimulus", profile.stimulus)
        root.put("capture_seconds", profile.captureSeconds)
        val measurementNotes = JSONArray()
        for (note in profile.measurementNotes) measurementNotes.put(note)
        root.put("measurement_notes", measurementNotes)
        root.put("sample_rate", DspConstants.FS)
        root.put("target", profile.target)

        val rangeArr = JSONArray()
        rangeArr.put(DspConstants.F_MIN)
        rangeArr.put(DspConstants.F_MAX)
        root.put("correction_range_hz", rangeArr)

        root.put("transition_hz", profile.transitionHz)
        profile.schroederHz?.let { root.put("schroeder_hz", it) }
        profile.snrDb?.let { root.put("snr_db", it) }
        root.put("rolloff_hz", profile.rolloffHz)

        val gainLimits = JSONObject()
        gainLimits.put("boost", DspConstants.MAX_BOOST_DB)
        gainLimits.put("cut", DspConstants.MAX_CUT_DB)
        gainLimits.put("shaping", DspConstants.MAX_SHAPING_DB)
        root.put("gain_limits_db", gainLimits)

        val nullsArr = JSONArray()
        for (hz in profile.nullsUntouchedHz) {
            nullsArr.put(hz)
        }
        root.put("nulls_untouched_hz", nullsArr)

        val roomObj = JSONObject()
        profile.volumeM3?.let { roomObj.put("volume_m3", it) }
        profile.rt60Seconds?.let { roomObj.put("rt60_s", it) }
        root.put("room", roomObj)

        selectedMode?.let { root.put("selected_mode", it.key) }
        root.put("preamp_db", recommendedPreampDb(selectedProfile))
        root.put("room_preamp_db", roomPreampDb(profile))

        val filtersArr = JSONArray()
        for (f in profile.filters) {
            val fObj = JSONObject()
            fObj.put("fc", f.fc)
            fObj.put("q", f.q)
            fObj.put("gain", f.gain)
            filtersArr.put(fObj)
        }
        root.put("filters", filtersArr)

        val manualFiltersArr = JSONArray()
        for (f in ManualEq.sanitize(profile.manualFilters)) {
            manualFiltersArr.put(JSONObject()
                .put("fc", f.fc)
                .put("q", f.q)
                .put("gain", f.gain))
        }
        root.put("manual_filters", manualFiltersArr)
        if (modeOverlays.isNotEmpty()) {
            val modeOverlaysObj = JSONObject()
            for (mode in ContentMode.entries) {
                val filters = JSONArray()
                for (filter in ManualEq.sanitize(modeOverlays[mode].orEmpty())) {
                    filters.put(JSONObject()
                        .put("fc", filter.fc)
                        .put("q", filter.q)
                        .put("gain", filter.gain))
                }
                modeOverlaysObj.put(mode.key, filters)
            }
            root.put("mode_overlays", modeOverlaysObj)
        }

        val bandsArr = JSONArray()
        for (b in profile.platformBands) {
            val bObj = JSONObject()
            bObj.put("center_hz", b.centerHz)
            bObj.put("millibels", b.millibels)
            bandsArr.put(bObj)
        }
        root.put("platform_bands", bandsArr)

        val capObj = JSONObject()
        for ((k, v) in profile.capabilityVerdict) {
            capObj.put(k, v)
        }
        root.put("capability", capObj)

        val curveArr = JSONArray()
        for (cp in profile.curve) {
            val pt = JSONObject()
            pt.put("hz", cp.hz)
            pt.put("measured_db", cp.measuredDb)
            pt.put("correction_db", cp.correctionDb)
            curveArr.put(pt)
        }
        root.put("curve", curveArr)

        return root.toString(2) + "\n"
    }

    /** Where an export landed, or why it did not, in words for the user. */
    data class ExportResult(val ok: Boolean, val message: String)

    /**
     * Saves [content] as Downloads/CoreEQ/[filename]: MediaStore on Android 10+,
     * which needs no permission, and the public directory below that. A TV
     * clipboard reaches nothing, so there is no clipboard fallback.
     */
    fun exportToDevice(context: Context, content: String, filename: String): ExportResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (filename.endsWith(".json")) "application/json" else "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CoreEQ")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return ExportResult(false, "Could not create Downloads/CoreEQ/$filename: the TV's storage refused the file.")
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    ?: return ExportResult(false, "Could not open Downloads/CoreEQ/$filename for writing.")
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CoreEQ")
                if (!dir.exists() && !dir.mkdirs()) {
                    return ExportResult(false, "Could not create ${dir.path}: storage permission is missing or the disk is full.")
                }
                File(dir, filename).writeText(content, Charsets.UTF_8)
            }
            ExportResult(true, "Saved to Downloads/CoreEQ/$filename")
        } catch (e: Exception) {
            Log.e("CoreEqExport", "Export of $filename failed", e)
            ExportResult(false, "Export failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
