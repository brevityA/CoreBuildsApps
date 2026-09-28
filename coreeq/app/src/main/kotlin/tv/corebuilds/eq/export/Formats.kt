package tv.corebuilds.eq.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
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
            return "# Core EQ: no filters\n"
        }
        val pDb = if (profile.preampDb != 0.0) profile.preampDb else Peaking.preampDb(profile.filters)
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "Preamp: %.2f dB\n", pDb))
        sb.append(String.format(Locale.US, "# Core EQ: correction band %.0f-%.0f Hz\n", DspConstants.F_MIN, DspConstants.F_MAX))
        sb.append("# Core EQ: set Bands Overlap to Cascade in Poweramp Equalizer,\n")
        sb.append("# Core EQ: or the filters will not sum the way this file assumes.\n")

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
        root.put("sample_rate", DspConstants.FS)
        root.put("target", profile.target)

        val rangeArr = JSONArray()
        rangeArr.put(DspConstants.F_MIN)
        rangeArr.put(DspConstants.F_MAX)
        root.put("correction_range_hz", rangeArr)

        root.put("transition_hz", profile.transitionHz)
        root.put("schroeder_hz", profile.schroederHz)
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

    fun exportToDevice(
        context: Context,
        content: String,
        filename: String,
        label: String
    ): Boolean {
        try {
            // 1. Copy to clipboard
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, content)
            clipboard?.setPrimaryClip(clip)

            // 2. Save to external Downloads/CoreEQ directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val coreEqDir = File(downloadsDir, "CoreEQ")
            if (!coreEqDir.exists()) {
                coreEqDir.mkdirs()
            }
            val outFile = File(coreEqDir, filename)
            outFile.writeText(content, Charsets.UTF_8)

            Toast.makeText(context, "Exported to Downloads/CoreEQ/$filename & copied to clipboard", Toast.LENGTH_LONG).show()
            return true
        } catch (e: Exception) {
            Toast.makeText(context, "Copied to clipboard ($label)", Toast.LENGTH_SHORT).show()
            return false
        }
    }
}
