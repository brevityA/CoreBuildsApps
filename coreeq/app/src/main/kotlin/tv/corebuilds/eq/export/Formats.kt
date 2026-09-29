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
import tv.corebuilds.eq.dsp.Peaking
import java.io.File
import java.util.Locale

/**
 * Exporters for Poweramp Equalizer, Equalizer APO, and Core EQ profile JSON.
 */
object Formats {

    fun exportParametricTxt(profile: Profile): String {
        if (profile.filters.isEmpty()) {
            return buildString {
                append("# Core EQ: no filters\n")
                for (note in profile.measurementNotes) append("# Core EQ measurement note: $note\n")
            }
        }
        val pDb = if (profile.preampDb != 0.0) profile.preampDb else Peaking.preampDb(profile.filters)
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "Preamp: %.2f dB\n", pDb))
        sb.append(String.format(Locale.US, "# Core EQ: correction band %.0f-%.0f Hz\n", maxOf(DspConstants.F_MIN, profile.rolloffHz), DspConstants.F_MAX))
        sb.append("# Core EQ: set Bands Overlap to Cascade in Poweramp Equalizer,\n")
        sb.append("# Core EQ: or the filters will not sum the way this file assumes.\n")
        for (note in profile.measurementNotes) {
            sb.append("# Core EQ measurement note: $note\n")
        }

        for (i in profile.filters.indices) {
            val f = profile.filters[i]
            val sign = if (f.gain >= 0) "+" else ""
            sb.append(String.format(Locale.US, "Filter %d: ON PK Fc %.0f Hz Gain %s%.2f dB Q %.2f\n",
                i + 1, f.fc, sign, f.gain, f.q))
        }
        return sb.toString()
    }

    fun exportGraphicEq(profile: Profile): String {
        val parts = mutableListOf<String>()
        for (cp in profile.curve) {
            val sign = if (cp.correctionDb >= 0) "+" else ""
            parts.add(String.format(Locale.US, "%.0f %s%.2f", cp.hz, sign, cp.correctionDb))
        }
        return "GraphicEQ: " + parts.joinToString("; ") + "\n"
    }

    fun exportProfileJson(profile: Profile): String {
        val root = JSONObject()
        root.put("format", "corebuilds.core-eq/1")
        root.put("id", profile.id)
        root.put("name", profile.name)
        root.put("timestamp_ms", profile.timestampMs)
        root.put("device", profile.deviceName)
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

        val pDb = if (profile.preampDb != 0.0) profile.preampDb else Peaking.preampDb(profile.filters)
        root.put("preamp_db", pDb)

        val filtersArr = JSONArray()
        for (f in profile.filters) {
            val fObj = JSONObject()
            fObj.put("fc", f.fc)
            fObj.put("q", f.q)
            fObj.put("gain", f.gain)
            filtersArr.put(fObj)
        }
        root.put("filters", filtersArr)

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
