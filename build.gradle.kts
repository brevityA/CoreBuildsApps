plugins {
    // AGP 9.4.0, the same toolchain coreeq/ has run since its API 37
    // migration: it is the only AGP line that both supports compileSdk 36+
    // (the Play target-API floor) and talks to Gradle 9.6+ at all. AGP 8.6-8.13
    // bind org.gradle.api.problems.internal.InternalProblems, removed in
    // Gradle 9.6, which is why the wrapper on 9.7.0 could only ever hold 8.5.2.
    //
    // AGP 9 ships built-in Kotlin, so there is no external KGP version here
    // any more; see tools/gradle_envelope.json.
    id("com.android.application") version "9.4.0" apply false
}
