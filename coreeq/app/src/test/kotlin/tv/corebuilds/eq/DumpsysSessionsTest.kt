package tv.corebuilds.eq

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.corebuilds.eq.apply.DiscoveredSession
import tv.corebuilds.eq.apply.DumpsysSessions

/**
 * The DUMP parser is pinned here, shape by shape. The fixtures are
 * representative shapes compiled from public AOSP dump documentation (the
 * AudioFlinger Tracks table and the key-value Clients / PlayerBase rows);
 * real captures from Google TV, Fire OS and AOSP replace or shadow them as
 * device testing lands (plan M6a/M8). Until then, any parser change that
 * moves a column or a key fails here first.
 */
class DumpsysSessionsTest {

    // Shape 2: the fixed-width Tracks table. Row 1 has no Type value (the
    // common case) and row 2 has one — the column shift is per row, and both
    // alignments must land on the same fields.
    private val googleTvTable = """
        Output thread 0xb4000076 type 0 (MIXER):
          I/O handle: 21
          Standby: no
          Tracks:
            Type     Id Active Client(pid/uid) Session Port Id S  Flags   Format Chn mask  SRate ST Usg CT  G db  L dB  R dB  VS dB  PortVol dB  PortMuted   Server FrmCnt  FrmRdy F Underruns  Flushed BitPerfect InternalMute   Latency
                     63    yes    3128/  10074     137      52 A  0x000 00000001 00000003  44100  3   1  3     0     0     0     0            0      false 000A5CE4  11025    8967 A         0        0      false        false
        T            64    no     3129/  10234     392      53 A  0x000 00000001 00000003  48000  3   5  3     0     0     0     0            0      false 000A5CE5  12000    1200 A         0        0      false        false
    """.trimIndent()

    // Shape 1: key-value rows, three spellings a firmware may print.
    private val fireOsKv = """
        AudioFlinger global configuration:
        Notification clients:
          pid: 551, uid: 1000, session: 0
          pid: 3128, uid: 10074, session: 392
        players:
          AudioPlaybackConfiguration piid:22 session:137 state:started uid:10074 pid:3128 usage:media
          AudioPlaybackConfiguration piid:23 session:455 state:started uid:10234 pid:3200 usage:voice_communication
          AudioPlaybackConfiguration piid:24 sessionId=470 state:started uid:10300 usage:game
    """.trimIndent()

    // Shape 1, classic AOSP Clients rows: one with usage, one global session,
    // one old enough to print no usage at all.
    private val aospClientsRows = """
          Clients:
            pid: 24798, uid: 10234, session: 392, type: 3, state: 2, usage: 1
            pid: 24798, uid: 10234, session: 0, type: 3, state: 2, usage: 1
            pid: 25100, uid: 10456, session: 512, type: 3, state: 2
    """.trimIndent()

    @Test
    fun theUsageConstantsAreThePlatformValues() {
        // AudioAttributes.USAGE_*, pinned: if these drift the filter is
        // silently wrong and a voice call could get room-corrected.
        assertEquals(0, DumpsysSessions.USAGE_UNKNOWN)
        assertEquals(1, DumpsysSessions.USAGE_MEDIA)
        assertEquals(2, DumpsysSessions.USAGE_VOICE_COMMUNICATION)
        assertEquals(4, DumpsysSessions.USAGE_ALARM)
        assertEquals(5, DumpsysSessions.USAGE_NOTIFICATION)
        assertEquals(13, DumpsysSessions.USAGE_SONIFICATION)
        assertEquals(14, DumpsysSessions.USAGE_GAME)
        assertEquals(15, DumpsysSessions.USAGE_VIRTUAL_SOURCE)
        assertEquals(16, DumpsysSessions.USAGE_ASSISTANT)
    }

    @Test
    fun theTracksTableIsParsedByRowAlignedColumns() {
        val sessions = DumpsysSessions.parse(googleTvTable).associateBy { it.sessionId }
        assertEquals(setOf(137, 392), sessions.keys)

        val media = sessions.getValue(137)
        assertEquals(10074, media.uid)
        assertEquals(3128, media.pid)
        assertEquals(DumpsysSessions.USAGE_MEDIA, media.usage)
        assertEquals(true, media.active)

        // Row with a Type value: the shift differs, the fields do not.
        val notification = sessions.getValue(392)
        assertEquals(10234, notification.uid)
        assertEquals(DumpsysSessions.USAGE_NOTIFICATION, notification.usage)
        assertEquals(false, notification.active)
    }

    @Test
    fun keyValueRowsAreParsedAcrossSpellings() {
        val sessions = DumpsysSessions.parse(fireOsKv).associateBy { it.sessionId }
        assertEquals(setOf(0, 137, 392, 455, 470), sessions.keys)

        assertEquals(10074, sessions.getValue(137).uid)
        assertEquals(true, sessions.getValue(137).active)
        assertEquals(DumpsysSessions.USAGE_MEDIA, sessions.getValue(137).usage)

        assertEquals(DumpsysSessions.USAGE_VOICE_COMMUNICATION, sessions.getValue(455).usage)
        // sessionId= spelling and a word usage.
        assertEquals(10300, sessions.getValue(470).uid)
        assertEquals(DumpsysSessions.USAGE_GAME, sessions.getValue(470).usage)
    }

    @Test
    fun rowsWithoutAUsageCarryNone() {
        val sessions = DumpsysSessions.parse(aospClientsRows).associateBy { it.sessionId }
        assertEquals(DumpsysSessions.USAGE_MEDIA, sessions.getValue(392).usage)
        assertNull(sessions.getValue(512).usage)
        assertEquals(10456, sessions.getValue(512).uid)
    }

    @Test
    fun sourcesMergePerSessionWithoutLosingFacts() {
        val merged = DumpsysSessions.merge(
            DumpsysSessions.parse(googleTvTable),
            DumpsysSessions.parse(aospClientsRows)
        ).associateBy { it.sessionId }
        // Session 392 appears in both: both uids agree, the table's active=no wins.
        assertEquals(10234, merged.getValue(392).uid)
        assertEquals(false, merged.getValue(392).active)
        assertEquals(setOf(137, 392, 0, 512), merged.keys)
    }

    @Test
    fun onlyMediaAndGameSessionsOfOtherAppsAreAttachable() {
        val sessions = listOf(
            DiscoveredSession(1, uid = 100, usage = DumpsysSessions.USAGE_MEDIA),
            DiscoveredSession(2, uid = 100, usage = DumpsysSessions.USAGE_GAME),
            DiscoveredSession(3, uid = 100, usage = DumpsysSessions.USAGE_UNKNOWN),
            DiscoveredSession(4, uid = 100, usage = null), // a dump with no usage column
            DiscoveredSession(5, uid = 100, usage = DumpsysSessions.USAGE_VOICE_COMMUNICATION),
            DiscoveredSession(6, uid = 100, usage = DumpsysSessions.USAGE_NOTIFICATION),
            DiscoveredSession(7, uid = 100, usage = DumpsysSessions.USAGE_ALARM),
            DiscoveredSession(8, uid = 100, usage = DumpsysSessions.USAGE_SONIFICATION),
            DiscoveredSession(9, uid = 100, usage = DumpsysSessions.USAGE_ASSISTANT),
            DiscoveredSession(10, uid = 100, usage = DumpsysSessions.USAGE_VIRTUAL_SOURCE),
            DiscoveredSession(16, uid = 100, usage = DumpsysSessions.USAGE_EXCLUDED),
            DiscoveredSession(0, uid = 100, usage = DumpsysSessions.USAGE_MEDIA), // global mix
            DiscoveredSession(-5, uid = 100, usage = DumpsysSessions.USAGE_MEDIA), // not a session
            DiscoveredSession(17, uid = null, usage = DumpsysSessions.USAGE_MEDIA), // unattributable
            DiscoveredSession(18, uid = 200, usage = DumpsysSessions.USAGE_MEDIA) // us
        )
        val attachable = DumpsysSessions.attachable(sessions, ourUid = 200).map { it.sessionId }
        assertEquals(listOf(1, 2, 3, 4), attachable)
    }

    @Test
    fun stoppedSessionsAreNotAttachable() {
        val sessions = listOf(
            DiscoveredSession(1, uid = 100, usage = DumpsysSessions.USAGE_MEDIA, active = true),
            DiscoveredSession(2, uid = 100, usage = DumpsysSessions.USAGE_MEDIA, active = null), // dump is silent
            DiscoveredSession(3, uid = 100, usage = DumpsysSessions.USAGE_MEDIA, active = false) // paused/left behind
        )
        assertEquals(listOf(1, 2), DumpsysSessions.attachable(sessions, ourUid = 200).map { it.sessionId })
        // The merged fixture's session 392 is active=no in the Tracks table.
        val merged = DumpsysSessions.merge(
            DumpsysSessions.parse(googleTvTable),
            DumpsysSessions.parse(aospClientsRows)
        )
        assertFalse(DumpsysSessions.attachable(merged, ourUid = 200).any { it.sessionId == 392 })
    }

    @Test
    fun anUnrecognisedUsageWordIsNamedNotSilent() {
        // "usage: podcast" is a usage the dump named: treat it as named and
        // leave it alone, unlike a dump that prints no usage at all.
        val parsed = DumpsysSessions.parse("pid: 1, uid: 2, session: 3, usage: podcast")
        assertEquals(1, parsed.size)
        assertEquals(DumpsysSessions.USAGE_EXCLUDED, parsed[0].usage)
        assertTrue(DumpsysSessions.attachable(parsed, ourUid = 9).isEmpty())
    }

    @Test
    fun garbageIsNeverGuessed() {
        val sessions = DumpsysSessions.parse(
            """
            complete nonsense
            session: notanumber
            session: 12, uid: abc
            Clients:
            """.trimIndent()
        )
        assertTrue(sessions.isEmpty())
    }

    @Test
    fun theExclusiveRuleForSessionsIsNeverBothPathsAtOnce() {
        // Discovery hands the service only what it may attach to: the service
        // still refuses to run discovery alongside the whole-TV equaliser, and
        // its own uid can never appear here.
        val attachable = DumpsysSessions.attachable(
            listOf(DiscoveredSession(1, uid = 200, usage = DumpsysSessions.USAGE_MEDIA)),
            ourUid = 200
        )
        assertFalse(attachable.any { it.uid == 200 })
    }
}
