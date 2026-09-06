package dev.corebuilds.doctor

import dev.corebuilds.doctor.diagnostics.CheckResult
import dev.corebuilds.doctor.diagnostics.DoctorReport
import dev.corebuilds.doctor.diagnostics.Redactor
import dev.corebuilds.doctor.diagnostics.ReportCard
import dev.corebuilds.doctor.diagnostics.Verdict
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRedactionTest {
    @Test
    fun redactorRemovesTokensFromShareableReport() {
        val secret = "Bearer abcdefghijklmnopqrstuvwxyz0123456789"
        val report = ReportCard.render(
            DoctorReport(
                checks = listOf(
                    CheckResult(
                        "Leak probe",
                        Verdict.FAIL,
                        "Failed with $secret",
                        "Open https://example.test/path?token=supersecretvalue"
                    )
                ),
                timestamp = 0L,
            )
        )
        assertFalse(report.contains("abcdefghijklmnopqrstuvwxyz0123456789"))
        assertFalse(report.contains("supersecretvalue"))
        assertTrue(report.contains("[REDACTED]"))
    }

    @Test
    fun redactorTruncatesOverlongErrors() {
        assertTrue(Redactor.clean("x".repeat(800)).length <= 500)
    }
}
