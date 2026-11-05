#!/usr/bin/env python3
"""Phone pairing storage, held statically.

Why this exists
---------------
Core EQ 1.13.0 phone pairing failed on every phone with "Pairing failed:
Caller-provided IV not permitted". `PhoneCredentialStore` encrypted the pairing
token under an Android Keystore AES key made with randomized encryption
required, then passed its own GCM IV to `Cipher.init`. The Keystore refuses
that; Robolectric has no Keystore, so every JVM test passed.

It was worse than a failed pairing: the token was encrypted *after* the TV had
accepted it, so the TV spent its one-time code and showed "Paired to <phone>"
while the phone had nothing saved.

The fix is held three ways: this file (the source shape), the Robolectric
round trip in `PhoneCredentialStoreTest`, and the on-device test in
`coreeq/app/src/androidTest`, which the emulator jobs run against a real
Keystore. This file also holds that the emulator jobs still run it.

Run: `python3 tests/test_core_eq_phone_pairing.py`
"""
from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/companion/CompanionClient.kt"
PHONE = ROOT / "coreeq/app/src/main/kotlin/tv/corebuilds/eq/companion/PhoneCompanionActivity.kt"
DEVICE_TEST = ROOT / (
    "coreeq/app/src/androidTest/kotlin/tv/corebuilds/eq/companion/"
    "PhoneCredentialStoreKeystoreTest.kt"
)
WORKFLOW = ROOT / ".github/workflows/core-eq-apk.yml"
EMULATOR = ROOT / "tools/install_core_eq_on_emulator.sh"
GRADLE = ROOT / "coreeq/app/build.gradle.kts"


def _between(source: str, start: str, end: str) -> str:
    begin = source.index(start)
    return source[begin:source.index(end, begin + len(start))]


class PhoneCredentialStoreShape(unittest.TestCase):
    def setUp(self) -> None:
        self.client = CLIENT.read_text(encoding="utf-8")
        self.store = _between(self.client, "internal class PhoneCredentialStore(", "\n    companion object {")

    def test_encryption_never_passes_an_iv(self) -> None:
        for call in re.findall(r"cipher\.init\(Cipher\.ENCRYPT_MODE[^\n]*", self.store):
            self.assertNotIn("ParameterSpec", call, f"the Keystore picks the IV; got {call!r}")
        self.assertIn("cipher.init(Cipher.ENCRYPT_MODE, keyProvider())", self.store)

    def test_the_stored_iv_is_the_one_the_cipher_chose(self) -> None:
        seal = _between(self.store, "fun seal(", "fun commit(")
        self.assertIn("val nonce = cipher.iv", seal)
        self.assertIn("nonce.size == NONCE_BYTES", seal)
        self.assertNotIn("SecureRandom", self.store, "a nonce made by the store is a caller-provided IV")

    def test_every_seal_failure_is_named_as_storage(self) -> None:
        seal = _between(self.store, "fun seal(", "fun commit(")
        self.assertIn("throw PhoneKeyStorageException(e)", seal)

    def test_the_keystore_key_still_requires_randomized_encryption(self) -> None:
        self.assertIn(".setRandomizedEncryptionRequired(true)", self.client)
        self.assertIn('KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)', self.client)


class PairingOrder(unittest.TestCase):
    def test_the_token_is_sealed_before_the_tv_is_asked_and_stored_after_it_agrees(self) -> None:
        phone = PHONE.read_text(encoding="utf-8")
        body = _between(phone, 'thread(name = "CoreEqPhonePair")', "} catch (e: Exception) {")
        seal = body.index("credentialStore.seal(")
        pair = body.index(".pair(pairingKey, token, clientName)")
        ok = body.index("requireOk(response)")
        commit = body.index("if (!credentialStore.commit(sealed)) throw PhonePairingNotSavedException()")
        self.assertLess(seal, pair, "a phone that cannot keep the token must stop before the TV spends its code")
        self.assertLess(pair, ok)
        self.assertLess(ok, commit, "nothing is stored until the TV has accepted the token")

    def test_a_storage_failure_says_so(self) -> None:
        phone = PHONE.read_text(encoding="utf-8")
        self.assertIn("is PhoneKeyStorageException -> getString(R.string.phone_pair_key_store_failed", phone)
        self.assertIn("R.string.phone_pair_key_store_failed", phone)
        # After the TV accepts, a failed write is reported rather than shown as "Phone paired".
        self.assertIn("is PhonePairingNotSavedException -> getString(R.string.phone_pair_not_saved)", phone)

    def test_the_token_is_written_synchronously_and_checked(self) -> None:
        client = CLIENT.read_text(encoding="utf-8")
        commit = _between(client, "fun commit(sealed: SealedPhoneCredentials): Boolean", "fun load()")
        self.assertIn(".commit()", commit)
        self.assertNotIn(".apply()", commit, "apply() never reports whether the write happened")

    def test_the_keystore_key_is_created_once(self) -> None:
        client = CLIENT.read_text(encoding="utf-8")
        self.assertIn("@Synchronized\n        private fun androidKeystoreKey()", client)


class OnDeviceTestRuns(unittest.TestCase):
    def test_the_device_test_proves_the_keystore_rule_before_the_round_trip(self) -> None:
        source = DEVICE_TEST.read_text(encoding="utf-8")
        self.assertIn("InvalidAlgorithmParameterException", source)
        self.assertIn(".setRandomizedEncryptionRequired(true)", source)
        self.assertIn("store.seal(", source)
        self.assertIn("store.commit(sealed)", source)
        # A really paired debug install is not overwritten or cleared by the test.
        self.assertIn("assumeTrue(", source)
        self.assertNotIn("PhoneCredentialStore(context).clear()", source)
        self.assertIn("PhoneCredentialStore(context).load()", source)

    def test_gradle_declares_the_runner_and_its_libraries(self) -> None:
        gradle = GRADLE.read_text(encoding="utf-8")
        self.assertIn('testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"', gradle)
        self.assertIn('androidTestImplementation("androidx.test:runner:', gradle)
        self.assertIn('androidTestImplementation("androidx.test.ext:junit:', gradle)

    def test_ci_builds_ships_and_runs_it(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn(":app:assembleDebugAndroidTest", workflow)
        self.assertEqual(workflow.count("name: CoreEQ-androidTest"), 2, "uploaded once, downloaded once")
        # Only the API 30 leg downloads them (the 37.0 image crash-loops, PR #455), and it
        # still has to: a matrix with no device-tests leg would silently run none.
        self.assertIn("matrix.device-tests }}\n        with:\n          name: CoreEQ-androidTest", workflow)
        api30 = workflow[workflow.index("- api-level: '30'"):workflow.index("- api-level: '37.0'")]
        self.assertIn("device-tests: true", api30)
        script = EMULATOR.read_text(encoding="utf-8")
        self.assertIn("TEST_APK=androidtest/coreeq-debug-androidTest.apk", script)
        self.assertIn("tv.corebuilds.eq.debug.test/androidx.test.runner.AndroidJUnitRunner", script)
        self.assertIn('if [[ -f "$TEST_APK" ]]; then', script)
        # am instrument exits 0 on a failed test; the script has to read the summary.
        self.assertIn("OK \\([1-9][0-9]* tests?\\)", script)
        self.assertIn("FAILURES!!!", script)
        # The 37.0 image restarts its framework after boot; user 0 then comes back
        # locked and credential-encrypted SharedPreferences throw (PR #455, first run).
        self.assertIn("am get-started-user-state 0", script)
        self.assertIn("RUNNING_UNLOCKED", script)
        # The post-install check asks a broken package service again, never a missing package.
        self.assertIn("package check met a restart", script)
        # The activity manager dying under `am instrument` is a restart, not a test failure.
        self.assertIn("DeadObjectException", script)
        self.assertIn("System has crashed", script)
        # A stable system server, not just an answering package service, before each run.
        self.assertIn("getprop sys.system_server.start_count", script)
        self.assertIn("pidof system_server", script)
        # A device that cannot report it is warned about, not failed (84e5e1ff0's 37.0 run).
        self.assertIn("running without the stability wait", script)
        run = script[script.index("run_device_tests() {"):]
        self.assertLess(run.index("wait_for_stable_system_server"), run.index("am instrument -w"))
        # Restarts earn at most three runs; an assertion failure gets none.
        self.assertIn("for attempt in 1 2 3; do", script)
        self.assertIn("(( attempt < 3 ))", script)
        self.assertLess(run.index('wait_for_user_unlocked "before the on-device tests'), run.index("am instrument -w"))


if __name__ == "__main__":
    sys.exit(0 if unittest.main(exit=False, verbosity=1).result.wasSuccessful() else 1)
