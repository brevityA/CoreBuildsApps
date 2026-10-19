# Poweramp JSON fixtures

These small fixtures exercise export shapes seen at different publication dates. They are **not** a vendor schema or a definitive numeric filter-type oracle; none exposes a reliable Poweramp app build number.

- `parametric_forum_2023.json` is a trimmed copy of the preset shown by a Poweramp forum user on 2023-06-27. The post identifies the first two rows as tone-control entries and shows additional channel-specific rows using the same numeric codes. The app build is not stated: <https://forum.powerampapp.com/topic/26543-trouble-importing-json-generated-by-script/>
- `parametric_gist_2023.json` is a trimmed Poweramp-style preset from a public GitHub Gist created 2023-10-15. Its active bands use code 2; the app build and filter-type labels are not stated: <https://gist.github.com/V1rgul/adb2c3f59fe3692a92bc6d51af28a068>
- `parametric_github_2026.json` is a reduced preset from a public Poweramp API repository issue opened 2026-07-17. It exercises codes 0–5, but the submitted preset does not label each code's response shape or give the app build: <https://github.com/maxmpz/powerampapi/issues/52>
- `graphic_pa-ceq-3.0.json` is a trimmed graphic preset from a third-party importer test fixture. It exercises the `parametric: false`, `q: 0` slider layout; its export build/date are not stated: <https://github.com/servitola/eq/blob/main/Tests/eqTests/Fixtures/Poweramp%20PA-CEQ%203.0.json>

The fixtures guard JSON parsing, partial/failed imports, and visible warnings. They do not establish acoustic equivalence or settle Poweramp's undocumented/versionless type mapping. The parametric importer deliberately supports only its narrow best-effort type subset, warns before application, skips codes it cannot map safely, and rejects files with no supported bands.
