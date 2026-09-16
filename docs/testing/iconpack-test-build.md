# Core Builds Icon Pack test APKs

The `test` Android build type is a side-by-side test build for maintainer-controlled
Android TV testing. It is not a release channel.

| Field | Test value |
|---|---|
| Variant | `:app:assembleCandidate` (the build type is named `candidate` because Android rejects build type names beginning with `test`) |
| Application ID | `tv.corebuilds.iconpack.test` |
| App label | `Core Builds Icon Pack – Test` |
| Version name | production version plus `-test.<short commit>` |
| Provider authority | `tv.corebuilds.iconpack.test.update` |
| Signing | Gradle debug signing, inherited through `initWith(debug)` |

The production package ID, release signing configuration, update authority, and
release metadata are not changed by this variant. `TEST_SOURCE_COMMIT` and the
version suffix identify the source in the APK's generated metadata; the app's
existing version display also shows the test version.

## Controlled build and distribution

`.github/workflows/iconpack-test-apk.yml` is manually triggered. It validates the
source and builds only `lintTest`, `testTestUnitTest`, and `assembleTest`. The
candidate-code build job has `contents: read` and no signing secrets. A separate
publisher job, which does not check out or execute candidate code, attaches the
APK, checksum, and build information to an unpublished draft GitHub release.
The short-lived Actions artifact is only a hand-off between those jobs; it is
not the tester distribution channel. The draft must remain unpublished and
obsolete drafts/assets should be deleted after testing. This workflow never
publishes a release or modifies the production update manifest.

For a local test build, pass the source hash explicitly:

```bash
./gradlew :app:lintCandidate :app:testCandidateUnitTest :app:assembleCandidate \
  -PtestSourceCommit="$(git rev-parse --short=7 HEAD)" --no-daemon
```

Do not commit APKs, checksums, keystores, or other signing material. Before
installing on a device, verify the APK package name and debug certificate with
`aapt dump badging` and `apksigner verify --print-certs`. Confirm that removing
`tv.corebuilds.iconpack.test` leaves the production `tv.corebuilds.iconpack`
installation untouched.
