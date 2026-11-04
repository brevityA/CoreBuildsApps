package tv.corebuilds.iconpack

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Date
import java.util.concurrent.Executors

/**
 * My requests, a Core Builds Plus extra (Google Play build): every icon this
 * TV requested through the broker, and how far each one has got.
 *
 * The rows come from [RequestLog] at once; each row's state starts as the
 * TV's own answer (already in the installed pack) or "checking", and fills in
 * as [RequestStatus.fetch] answers. Answers are kept for [FRESH_MS], and reads
 * come out of one budget of [HOURLY_READS] per rolling hour for the whole
 * process, not per visit: three visits in an hour with 30 open rows each would
 * otherwise send 90 reads past GitHub's 60 unauthenticated reads an hour, and
 * every read past that is a 403 shown as "GitHub did not answer". Rows past
 * the budget say "later". The budget is per process, so a restart within the
 * hour can spend it again; two still stay at GitHub's 60.
 *
 * Opened from Settings, and only through [Plus.unlocked]; it checks again
 * itself so a stale intent cannot open it without Plus (bought or on trial).
 */
class RequestsActivity : TvActivity() {

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyChrome(this)
        super.onCreate(savedInstanceState)
        if (!Plus.active(this)) {
            finish()
            return
        }
        setContentView(R.layout.activity_requests)

        val back = findViewById<View>(R.id.requests_back)
        back.setOnClickListener { finish() }

        val entries = RequestLog.all(this)
        val list = findViewById<LinearLayout>(R.id.requests_list)
        if (entries.isEmpty()) {
            findViewById<View>(R.id.requests_empty).visibility = View.VISIBLE
            findViewById<TextView>(R.id.requests_summary).visibility = View.GONE
            back.requestFocus()
            return
        }

        val states = HashMap<Int, RequestStatus.State>()
        val rows = entries.map { entry ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_request, list, false)
            row.findViewById<TextView>(R.id.request_app).text = entry.appName
            row.findViewById<TextView>(R.id.request_component).text = entry.component
            row.findViewById<TextView>(R.id.request_issue).text = getString(
                R.string.requests_issue_fmt, entry.issue,
                DateFormat.getMediumDateFormat(this).format(Date(entry.at)))
            list.addView(row)
            val local = if (RequestStatus.inPack(this, entry.component)) RequestStatus.State.IN_PACK
                        else cached(entry.issue)
            if (local != null) states[entry.issue] = local
            showState(row, local)
            entry to row
        }
        summarize(entries, states)
        rows.first().second.requestFocus()

        val unchecked = rows.filter { (entry, _) -> entry.issue !in states }
        val granted = reserveReads(unchecked.size)
        val toCheck = unchecked.take(granted)
        // Past the hourly budget a row says so, rather than "checking" forever.
        for ((_, row) in unchecked.drop(granted)) {
            row.findViewById<TextView>(R.id.request_state).setText(R.string.requests_state_later)
        }
        if (toCheck.isEmpty()) return
        IO.execute {
            for ((entry, row) in toCheck) {
                if (closed) return@execute
                val state = RequestStatus.state(RequestStatus.fetch(entry.issue), inPack = false)
                if (state != RequestStatus.State.UNKNOWN) remember(entry.issue, state)
                main.post {
                    if (closed) return@post
                    states[entry.issue] = state
                    showState(row, state)
                    summarize(entries, states)
                }
            }
        }
    }

    override fun onDestroy() {
        closed = true
        super.onDestroy()
    }

    /** One row's state chip; null is "checking". */
    private fun showState(row: View, state: RequestStatus.State?) {
        val chip = row.findViewById<TextView>(R.id.request_state)
        val (text, color) = when (state) {
            null -> R.string.requests_state_checking to R.color.cb_slate
            RequestStatus.State.IN_PACK -> R.string.requests_state_in_pack to R.color.cb_success
            RequestStatus.State.DONE -> R.string.requests_state_done to R.color.cb_signal_cyan
            RequestStatus.State.PRIORITY -> R.string.requests_state_priority to R.color.cb_warning
            RequestStatus.State.OPEN -> R.string.requests_state_open to R.color.cb_ink
            RequestStatus.State.CLOSED -> R.string.requests_state_closed to R.color.cb_slate
            RequestStatus.State.UNKNOWN -> R.string.requests_state_unknown to R.color.cb_slate
        }
        chip.setText(text)
        chip.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun summarize(entries: List<RequestLog.Entry>, states: Map<Int, RequestStatus.State>) {
        val inPack = entries.count { states[it.issue] == RequestStatus.State.IN_PACK }
        val done = entries.count { states[it.issue] == RequestStatus.State.DONE }
        findViewById<TextView>(R.id.requests_summary).text =
            getString(R.string.requests_summary_fmt, entries.size, inPack, done)
    }

    private companion object {
        /** Half GitHub's 60 unauthenticated reads an hour, per address. */
        const val HOURLY_READS = 30
        const val HOUR_MS = 60 * 60_000L
        const val FRESH_MS = 10 * 60_000L
        val IO = Executors.newSingleThreadExecutor()

        /** When each read granted in the last hour was granted, oldest first. */
        val granted = ArrayDeque<Long>()

        /** Grants up to [wanted] reads from the rolling hourly budget. */
        @Synchronized fun reserveReads(wanted: Int, now: Long = System.currentTimeMillis()): Int {
            while (granted.isNotEmpty() && now - granted.first() >= HOUR_MS) granted.removeFirst()
            val n = minOf(wanted, HOURLY_READS - granted.size).coerceAtLeast(0)
            repeat(n) { granted.addLast(now) }
            return n
        }

        /** Answers from GitHub this process already has: issue -> (state, when). */
        val answers = HashMap<Int, Pair<RequestStatus.State, Long>>()

        @Synchronized fun remember(issue: Int, state: RequestStatus.State) {
            answers[issue] = state to System.currentTimeMillis()
        }

        @Synchronized fun cached(issue: Int): RequestStatus.State? =
            answers[issue]?.takeIf { System.currentTimeMillis() - it.second < FRESH_MS }?.first
    }
}
