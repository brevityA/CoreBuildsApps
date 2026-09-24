#!/usr/bin/env python3
"""Build envelope gate.

Dependabot sees version numbers and nothing else. It cannot see that five of the
six modules in this suite compile against API 34, that the icon pack still
ships `minSdk 21` for Android 5.x Fire TV hardware, or that `doctor/` wires the
Compose compiler through a DSL that Kotlin 2.0 deleted. So it proposes AndroidX
releases whose AAR metadata demands a newer platform, and every such PR dies in
CI with a `CheckAarMetadata` failure that has to be read by a human to explain.

This gate makes the envelope a checked fact instead of a memory:

  1. no Gradle root declares a dependency above its committed ceiling
  2. every Gradle root in the suite is actually watched by Dependabot
  3. `.github/dependabot.yml` ignores minor+major for every ceiling coordinate,
     in every directory that declares it
  4. the Compose compiler wiring matches the Kotlin major in use
  5. every workflow installs the Android platform its module compiles against

Rule 3 is the one that matters most. The ignore rules for `core-ktx`,
`recyclerview` and `appcompat` were added in 6434eabd for exactly this reason,
were silently dropped in 9f5d266d when `dependabot.yml` was restructured into
per-directory groups, and the four PRs that followed (#122 #125 #126 #132) all
failed for the same three libraries. Nothing in CI noticed the rules had gone.
Now something does.

Run: `python tools/check_gradle_envelope.py`
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ENVELOPE = ROOT / "tools" / "gradle_envelope.json"
DEPENDABOT = ROOT / ".github" / "dependabot.yml"
WORKFLOWS = ROOT / ".github" / "workflows"
SUITE_CI = WORKFLOWS / "suite-ci.yml"

problems: list[str] = []
receipt: list[str] = []

def fail(message: str) -> None:
    problems.append(message)

def read(path: Path) -> str:
    if not path.is_file():
        fail(f"missing {path.relative_to(ROOT)}")
        return ""
    return path.read_text(encoding="utf-8")

# --------------------------------------------------------------------------
# versions
# --------------------------------------------------------------------------

_PRERELEASE = re.compile(r"[-.](alpha|beta|rc|dev|snapshot)", re.IGNORECASE)

def parse_version(text: str) -> tuple:
    """Order versions numerically, treating a prerelease as below its release.

    Handles the three shapes this suite uses: `1.13.1`, `8.5.2`, and the
    Compose BOM's date scheme `2024.06.00`. The trailing element ranks a
    prerelease under the release it leads up to, so `1.4.0-alpha02 < 1.4.0`
    rather than comparing equal — a cap that let an alpha through would be worse
    than no cap, since alphas are exactly what Dependabot offers first.
    """
    value = text.strip()
    is_release = 0 if _PRERELEASE.search(value) else 1
    cleaned = _PRERELEASE.split(value)[0]
    parts = []
    for chunk in cleaned.split("."):
        if chunk.isdigit():
            parts.append(int(chunk))
        else:
            break
    if not parts:
        fail(f"unparseable version {text!r}")
        return (0, 0, 0, 0, is_release)
    # Pad before ranking, or "9" becomes (9, 1) and compares as "9.1" — which
    # would let AGP 9.0.0 slip under a `>= 9` cap. The rank is the last element
    # and only ever breaks a tie between otherwise identical numbers.
    while len(parts) < 4:
        parts.append(0)
    return (*parts, is_release)

def within(value: str, ceiling: str) -> bool:
    return parse_version(value) <= parse_version(ceiling)

# --------------------------------------------------------------------------
# Gradle roots
# --------------------------------------------------------------------------

class Module:
    def __init__(self, root: "GradleRoot", name: str, path: Path):
        self.root = root
        self.name = name
        self.path = path
        self.build_file = path / "build.gradle.kts"
        source = read(self.build_file)
        self.source = source
        self.compile_sdk = _int_setting(source, "compileSdk")
        self.min_sdk = _int_setting(source, "minSdk")
        self.target_sdk = _int_setting(source, "targetSdk")
        self.deps = _dependencies(source)
        self.compose_enabled = bool(re.search(r"compose\s*=\s*true", source))
        self.compose_extension = _quoted_setting(source, "kotlinCompilerExtensionVersion")

class GradleRoot:
    def __init__(self, rel: str):
        self.rel = rel                      # "" for the repo root
        self.dir = ROOT / rel if rel else ROOT
        self.label = rel or "."
        self.settings = read(self.dir / "settings.gradle.kts")
        self.build_file = self.dir / "build.gradle.kts"
        self.plugins = read(self.build_file)
        self.agp = _plugin_version(self.plugins, "com.android.application")
        self.kotlin = _plugin_version(self.plugins, "org.jetbrains.kotlin.android")
        self.kotlin_plugins = {
            pid: ver
            for pid, ver in _all_plugins(self.plugins).items()
            if pid.startswith("org.jetbrains.kotlin")
        }
        self.wrapper = _wrapper_version(self.dir)
        # Plugin coordinates are versioned in `plugins {}`, not `dependencies {}`,
        # but they are still coordinates Dependabot proposes bumps for, so the
        # ceiling table has to see them.
        self.plugin_deps = _all_plugins(self.plugins)
        self.modules = [Module(self, name, self.dir / name) for name in _module_dirs(self.settings)]

    @property
    def min_compile_sdk(self) -> int | None:
        values = [m.compile_sdk for m in self.modules if m.compile_sdk is not None]
        return min(values) if values else None

def _module_dirs(settings: str) -> list[str]:
    """`include(":app")` / `include(":glyphs")` -> ["app", "glyphs"], in file order."""
    return re.findall(r'include\(\s*":([A-Za-z0-9_.\-]+)"\s*\)', settings)

def _int_setting(source: str, name: str) -> int | None:
    match = re.search(rf"\b{name}\s*=\s*(\d+)", source)
    return int(match.group(1)) if match else None

def _quoted_setting(source: str, name: str) -> str | None:
    match = re.search(rf'\b{name}\s*=\s*"([^"]+)"', source)
    return match.group(1) if match else None

def _all_plugins(source: str) -> dict[str, str]:
    return dict(re.findall(r'id\(\s*"([^"]+)"\s*\)\s*version\s*"([^"]+)"', source))

def _applied_plugin_ids(source: str) -> set[str]:
    """Every `id("...")` in the file, versioned or not.

    A module normally *applies* a plugin without repeating its version — the
    version lives once in the root's `plugins {}` with `apply false`. Matching
    only versioned declarations would miss the module half of that pairing,
    which is exactly where the Compose plugin has to appear.
    """
    return set(re.findall(r'id\(\s*"([^"]+)"\s*\)', source))

def _plugin_version(source: str, plugin_id: str) -> str | None:
    return _all_plugins(source).get(plugin_id)

def _wrapper_version(root_dir: Path) -> str | None:
    props = root_dir / "gradle" / "wrapper" / "gradle-wrapper.properties"
    source = read(props)
    match = re.search(r"distributionUrl=.*gradle-([0-9][0-9A-Za-z.\-]*?)-(?:bin|all)\.zip", source)
    return match.group(1) if match else None

_COORD = re.compile(r'"([a-zA-Z0-9_.\-]+:[a-zA-Z0-9_.\-]+):([0-9][0-9A-Za-z.\-]*)"\s*\)')

def _dependencies(source: str) -> dict[str, str]:
    """Every `configuration("group:artifact:version")` in a dependencies block.

    Deliberately includes `platform(...)` BOM imports and test/debug
    configurations: a ceiling that only watched `implementation` would miss the
    Compose BOM, which is exactly the coordinate that breaks doctor/.
    """
    block = re.search(r"^dependencies\s*\{(.*?)^\}", source, re.MULTILINE | re.DOTALL)
    scope = block.group(1) if block else source
    found: dict[str, str] = {}
    for coord, version in _COORD.findall(scope):
        # A coordinate pinned twice in one file is its own kind of bug; keep the
        # highest so the gate reports the worst case rather than hiding it.
        if coord not in found or parse_version(version) > parse_version(found[coord]):
            found[coord] = version
    return found

def discover_roots() -> list[GradleRoot]:
    rels: list[str] = []
    for settings in sorted(ROOT.rglob("settings.gradle.kts")):
        rel = settings.parent.relative_to(ROOT).as_posix()
        if any(part in {"node_modules", "build", ".git"} for part in Path(rel).parts):
            continue
        rels.append("" if rel == "." else rel)
    return [GradleRoot(rel) for rel in rels]

# --------------------------------------------------------------------------
# dependabot.yml — parsed without PyYAML on purpose
# --------------------------------------------------------------------------

class DependabotEntry:
    def __init__(self, ecosystem: str, directory: str, lines: list[str]):
        self.ecosystem = ecosystem
        self.directory = directory
        self.lines = lines

    @property
    def ignores(self) -> dict[str, list[str]]:
        """coordinate -> blocked update-types, from this entry's `ignore:` block."""
        out: dict[str, list[str]] = {}
        in_ignore = False
        current: str | None = None
        for line in self.lines:
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):
                continue
            indent = len(line) - len(line.lstrip())
            if stripped == "ignore:":
                in_ignore = True
                continue
            if in_ignore:
                # A sibling key at or above `ignore:`'s own indent ends the block.
                if indent <= 4 and stripped.endswith(":") and not stripped.startswith("-"):
                    in_ignore = False
                    current = None
                    continue
                name = re.match(r'-\s*dependency-name:\s*"([^"]+)"', stripped)
                if name:
                    current = name.group(1)
                    out.setdefault(current, [])
                    continue
                if current is not None:
                    types = re.match(r"update-types:\s*\[(.*)\]", stripped)
                    if types:
                        out[current] = [t.strip().strip('"\'') for t in types.group(1).split(",")]
                        continue
                    versions = re.match(r"versions:\s*\[(.*)\]", stripped)
                    if versions:
                        out[current] = [v.strip().strip('"\'') for v in versions.group(1).split(",")]
        return out

def parse_dependabot() -> list[DependabotEntry]:
    lines = read(DEPENDABOT).splitlines()
    entries: list[DependabotEntry] = []
    current: list[str] = []
    for line in lines:
        if re.match(r"\s*-\s*package-ecosystem:", line):
            if current:
                entries.append(_entry_from(current))
            current = [line]
        elif current:
            current.append(line)
    if current:
        entries.append(_entry_from(current))
    return [e for e in entries if e is not None]

def _entry_from(lines: list[str]) -> DependabotEntry | None:
    text = "\n".join(lines)
    eco = re.search(r'package-ecosystem:\s*"([^"]+)"', text)
    directory = re.search(r'directory:\s*"([^"]+)"', text)
    if not eco or not directory:
        fail(f"dependabot.yml entry is missing package-ecosystem or directory:\n{text[:200]}")
        return None
    return DependabotEntry(eco.group(1), directory.group(1), lines)

def gradle_entry_for(directory: str, entries: list[DependabotEntry]) -> DependabotEntry | None:
    """Dependabot spells the repo root `/`, the way `directory:` does."""
    for entry in entries:
        if entry.ecosystem != "gradle":
            continue
        if entry.directory == directory:
            return entry
    return None

# --------------------------------------------------------------------------
# checks
# --------------------------------------------------------------------------

def check_roots(roots: list[GradleRoot], table: dict) -> None:
    envelope = table["envelope"]
    agp_cap = envelope["agpMaxExclusive"]
    gradle_floor = envelope["minGradleForAgp8"]
    for root in roots:
        if root.agp is None:
            fail(f"{root.label}: no com.android.application version in {root.build_file.relative_to(ROOT)}")
        elif not parse_version(root.agp) < parse_version(agp_cap):
            fail(
                f"{root.label}: AGP {root.agp} is at or past {agp_cap}, the breaking major with "
                f"built-in Kotlin. That is a migration, not a bump — see tools/gradle_envelope.json."
            )
        if root.kotlin is None:
            fail(f"{root.label}: no org.jetbrains.kotlin.android version")
        # Every Kotlin sub-plugin must move in lockstep with the Kotlin plugin.
        for pid, version in sorted(root.kotlin_plugins.items()):
            if version != root.kotlin:
                fail(f"{root.label}: {pid} {version} must match Kotlin {root.kotlin}")
        if root.wrapper is None:
            fail(f"{root.label}: no Gradle wrapper distributionUrl found")
        elif parse_version(root.wrapper) < parse_version(gradle_floor):
            fail(
                f"{root.label}: Gradle wrapper {root.wrapper} is below {gradle_floor}, the minimum "
                f"AGP 8.x runs on"
            )
        receipt.append(f"{root.label}: AGP {root.agp} · Kotlin {root.kotlin} · Gradle {root.wrapper}")

def check_ceilings(roots: list[GradleRoot], table: dict) -> dict[str, set[str]]:
    """Assert no declared dependency is above its ceiling.

    Returns coordinate -> the dependabot directories that declare it, which the
    ignore-rule check consumes so the two can never disagree.
    """
    ceilings = {c["coordinate"]: c for c in table["ceilings"]}
    declaring: dict[str, set[str]] = {}
    for root in roots:
        directory = "/" + root.rel if root.rel else "/"
        # Plugin coordinates: capped and declared in the same breath, because a
        # Kotlin or AGP bump is proposed by Dependabot exactly like a library one.
        for coord, version in sorted(root.plugin_deps.items()):
            ceiling = ceilings.get(coord)
            if ceiling is None:
                continue
            declaring.setdefault(coord, set()).add(directory)
            if not within(version, ceiling["max"]):
                fail(
                    f"{root.build_file.relative_to(ROOT)} applies {coord} {version}, "
                    f"above its ceiling {ceiling['max']}.\n"
                    f"    why it is capped: {ceiling['reason']}"
                )
        for module in root.modules:
            for coord, version in sorted(module.deps.items()):
                ceiling = ceilings.get(coord)
                if ceiling is None:
                    continue
                declaring.setdefault(coord, set()).add(directory)
                if not within(version, ceiling["max"]):
                    fail(
                        f"{module.path.relative_to(ROOT)}/build.gradle.kts declares {coord}:{version}, "
                        f"above its ceiling {ceiling['max']}.\n"
                        f"    why it is capped: {ceiling['reason']}"
                    )
    # A ceiling nobody declares is dead weight, and worse, it silently stops
    # Dependabot proposing updates for a library the suite does not use.
    for coord in ceilings:
        if coord not in declaring:
            fail(f"tools/gradle_envelope.json caps {coord} but no Gradle root declares it — remove the ceiling")
    return declaring

def check_dependabot(
    roots: list[GradleRoot],
    table: dict,
    declaring: dict[str, set[str]],
) -> None:
    entries = parse_dependabot()
    if not entries:
        fail("dependabot.yml parsed to zero entries — the parser and the file have drifted apart")
        return
    gradle_entries = [e for e in entries if e.ecosystem == "gradle"]
    receipt.append(f"dependabot.yml: {len(gradle_entries)} gradle directories, {len(entries) - len(gradle_entries)} other")

    watched = {e.directory for e in gradle_entries}
    for root in roots:
        directory = "/" + root.rel if root.rel else "/"
        if directory not in watched:
            fail(
                f"{root.label} is a Gradle root with no dependabot.yml entry — its dependencies are "
                f"never checked for updates, and never checked for CVEs either. "
                f"Add `directory: \"{directory}\"`."
            )

    ceilings = {c["coordinate"]: c for c in table["ceilings"]}
    for coord, directories in sorted(declaring.items()):
        ceiling = ceilings[coord]
        wanted = set(ceiling["blockedUpdateTypes"])
        for directory in sorted(directories):
            entry = gradle_entry_for(directory, entries)
            if entry is None:
                continue  # already reported as an unwatched root
            blocked = entry.ignores.get(coord)
            if blocked is None:
                fail(
                    f'dependabot.yml directory "{directory}" does not ignore {coord}, which is capped at '
                    f'{ceiling["max"]} in tools/gradle_envelope.json.\n'
                    f"    Dependabot will keep proposing versions that cannot build: {ceiling['reason']}\n"
                    f'    add under `ignore:`:\n'
                    f'      - dependency-name: "{coord}"\n'
                    f'        update-types: {json.dumps(sorted(wanted))}'
                )
            elif set(blocked) != wanted:
                fail(
                    f'dependabot.yml directory "{directory}" ignores {coord} with {sorted(blocked)}, '
                    f"but the ceiling needs {sorted(wanted)}"
                )

# Compose compiler 1.5.x is tied to one exact Kotlin release each; a mismatch is
# a hard error at compile time, not a warning. Only the pairs this suite can
# plausibly land are listed — an unknown pair is left alone rather than guessed.
COMPOSE_COMPILER_TO_KOTLIN = {
    "1.5.14": "1.9.24",
    "1.5.15": "1.9.25",
}


def check_compose(roots: list[GradleRoot], table: dict) -> None:
    """The Compose compiler must be wired the way the Kotlin major expects.

    Two mutually exclusive wirings, and the wrong one for your Kotlin version is
    a hard build failure rather than a warning:

      Kotlin < 2.0  composeOptions.kotlinCompilerExtensionVersion, pinned to one
                    exact Kotlin release
      Kotlin >= 2.0 org.jetbrains.kotlin.plugin.compose applied, and the
                    composeOptions DSL deleted

    `doctor/` is the only module with Compose on, at 1.5.14 / Kotlin 1.9.24.
    That pairing is the reason no Kotlin 2.x bump can be taken as a version
    change alone, so the gate asserts it rather than leaving it as folklore.
    """
    compose = table["compose"]
    floor = parse_version(compose["kotlinMajorThatMovesTheCompiler"])
    plugin_id = compose["pluginId"]
    obsolete = compose["obsoleteDsl"]
    for root in roots:
        if root.kotlin is None:
            continue
        modern = parse_version(root.kotlin) >= floor
        root_applies_plugin = plugin_id in root.kotlin_plugins
        for module in root.modules:
            if not module.compose_enabled:
                continue
            label = module.path.relative_to(ROOT)
            module_source = read(module.build_file)
            # Declaring the plugin in the root with `apply false` only puts it on
            # the classpath; the module has to apply it. So the module is what
            # gets checked, and the root declaration is what supplies the version.
            applies_plugin = plugin_id in _applied_plugin_ids(module_source)
            if modern:
                if not applies_plugin:
                    fail(
                        f"{label} enables Compose on Kotlin {root.kotlin}, which needs "
                        f"`{plugin_id}` applied in that module. From Kotlin 2.0 the Compose "
                        f"compiler is no longer part of the Kotlin plugin."
                        + ("" if root_applies_plugin else
                           f" The root also has to declare it with a version and `apply false`.")
                    )
                if module.compose_extension:
                    fail(
                        f"{label} still sets {obsolete} = \"{module.compose_extension}\" on Kotlin "
                        f"{root.kotlin}. That DSL belongs to the pre-2.0 Compose compiler and must "
                        f"be deleted once `{plugin_id}` is applied."
                    )
                continue
            if not module.compose_extension:
                fail(
                    f"{label} enables Compose on Kotlin {root.kotlin} but sets no {obsolete}. "
                    f"Before Kotlin 2.0 the Compose compiler version has to be pinned, and it has "
                    f"to match the Kotlin version exactly."
                )
                continue
            expected = COMPOSE_COMPILER_TO_KOTLIN.get(module.compose_extension)
            if expected is not None and expected != root.kotlin:
                fail(
                    f"{label} pins Compose compiler {module.compose_extension}, which is built for "
                    f"Kotlin {expected}, but the root uses Kotlin {root.kotlin}"
                )
            receipt.append(f"{label}: Compose compiler {module.compose_extension} tied to Kotlin {root.kotlin}")



def parse_matrix_entries(text: str) -> list[dict[str, str]]:
    """Parse the `include:` list of a `strategy.matrix` block.

    Scoped to that block on purpose. A whole-file search for `- name:` also
    matches every workflow *step* name, and because `gradle:` only appears
    further down in the matrix, a lazy match happily pairs "Check suite truth"
    with the Icon Pack's build file. Read the block, then the entries in it.
    """
    lines = text.splitlines()
    start = None
    indent = 0
    for i, line in enumerate(lines):
        if re.match(r"^\s*include:\s*$", line):
            start = i + 1
            indent = len(line) - len(line.lstrip())
            break
    if start is None:
        return []
    block: list[str] = []
    for line in lines[start:]:
        if line.strip() and (len(line) - len(line.lstrip())) <= indent:
            break
        block.append(line)

    entries: list[dict[str, str]] = []
    current: dict[str, str] | None = None
    for line in block:
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        item = re.match(r"-\s*([A-Za-z0-9_]+):\s*(.*)$", stripped)
        if item:
            if current:
                entries.append(current)
            current = {}
            stripped = item.group(1) + ": " + item.group(2)
        pair = re.match(r"([A-Za-z0-9_]+):\s*(.*)$", stripped)
        if pair and current is not None:
            current[pair.group(1)] = pair.group(2).strip().strip("'\"")
    if current:
        entries.append(current)
    return entries

def check_ci_platforms(roots: list[GradleRoot]) -> None:
    """Every workflow must install the platform its module compiles against.

    `suite-ci.yml` installed only android-34 while its matrix built Core Doctor
    at compileSdk 35, and nothing noticed because two things papered over it:
    ubuntu-latest preinstalls both platforms, so the step no-ops, and where an
    image does not carry one, AGP silently auto-downloads it once the runner has
    accepted the SDK licences. Neither is a property of this repository — both
    belong to the runner, and a runner-image change would have turned a green
    job red with nothing in the workflow to explain why. Declaring the platform
    per matrix entry makes each job state its own requirement, and this check
    holds that statement to the build file it points at.
    """
    by_gradle: dict[str, Module] = {}
    for root in roots:
        for module in root.modules:
            by_gradle[module.build_file.relative_to(ROOT).as_posix()] = module

    suite_ci = read(SUITE_CI)
    if not suite_ci:
        return
    entries = parse_matrix_entries(suite_ci)
    entries = [e for e in entries if "gradle" in e and "workflow" in e]
    if not entries:
        fail("could not parse the suite-ci.yml matrix — has its shape changed?")
        return
    # This job installs the platform itself, so this file is what has to be
    # right. The `workflow:` each entry points at is that product's *release*
    # workflow; check_per_root_workflows audits those separately.
    parameterised = "platforms;android-${{" in suite_ci
    installed_here = {int(v) for v in re.findall(r"platforms;android-(\d+)", suite_ci)}
    if not parameterised:
        fail(
            f"suite-ci.yml installs a hard-coded Android platform "
            f"({sorted(installed_here) or 'none'}) for the whole matrix. Give each matrix entry a "
            f"`compileSdk:` and install `platforms;android-${{{{ matrix.compileSdk }}}}` so a module "
            f"cannot outgrow the platform its own job installs."
        )
    for entry in entries:
        name = entry.get("name", "?")
        gradle = entry["gradle"]
        workflow = entry["workflow"]
        module = by_gradle.get(gradle)
        if module is None:
            fail(f"suite-ci.yml matrix entry {name!r} points at {gradle}, which is not a module build file")
            continue
        declared = entry.get("compileSdk")
        if declared is None:
            fail(f"suite-ci.yml matrix entry {name!r} declares no compileSdk")
        elif not declared.isdigit():
            fail(f"suite-ci.yml matrix entry {name!r} declares compileSdk {declared!r}, which is not a number")
        elif int(declared) != module.compile_sdk:
            fail(
                f"suite-ci.yml matrix entry {name!r} declares compileSdk {declared} but "
                f"{gradle} compiles against {module.compile_sdk}"
            )
        if not (WORKFLOWS / Path(workflow).name).is_file():
            fail(f"suite-ci.yml matrix entry {name!r} references missing workflow {workflow}")
        if not parameterised and module.compile_sdk not in installed_here:
            fail(
                f"suite-ci.yml builds {gradle} at compileSdk {module.compile_sdk} but installs only "
                f"android-{sorted(installed_here)}"
            )
        receipt.append(f"{name}: compileSdk {module.compile_sdk} · {workflow}")

def check_per_root_workflows(roots: list[GradleRoot]) -> None:
    """Audit the per-product release workflows, one per standalone Gradle root.

    Only a workflow that *pins* a platform is held to pinning the right one.
    `build.yml`, `device-check.yml` and `iconpack-test-apk.yml`
    install nothing and have always been green on ubuntu-latest's preinstalled
    SDK; demanding an explicit install there would rewrite working release
    paths for no gain. `suite-ci.yml` is handled by check_ci_platforms, which
    reads its matrix.
    """
    by_rel = {r.rel: r for r in roots}
    for wf in sorted(WORKFLOWS.glob("*.yml")):
        if wf.name == SUITE_CI.name:
            continue
        text = wf.read_text(encoding="utf-8")
        installed = {int(v) for v in re.findall(r"platforms;android-(\d+)", text)}
        if not installed:
            continue
        # A workflow builds a root if it cd's into it or names its build file.
        built = set(re.findall(r"working-directory:\s*([A-Za-z0-9_./\-]+)", text))
        built |= {
            m.removesuffix("/app/build.gradle.kts").removesuffix("/build.gradle.kts")
            for m in re.findall(r"([A-Za-z0-9_./\-]*build\.gradle\.kts)", text)
        }
        for candidate in sorted(built):
            rel = candidate.strip().removeprefix("./")
            rel = "" if rel == "." else rel
            root = by_rel.get(rel)
            if root is None or not root.modules:
                continue
            needed = {m.compile_sdk for m in root.modules if m.compile_sdk is not None}
            if needed - installed:
                fail(
                    f"{wf.name} builds {root.label} at compileSdk {sorted(needed)} but installs "
                    f"only android-{sorted(installed)}"
                )

def main() -> int:
    if not ENVELOPE.is_file():
        print(f"::error::missing {ENVELOPE.relative_to(ROOT)}")
        return 1
    table = json.loads(ENVELOPE.read_text(encoding="utf-8"))
    roots = discover_roots()
    if not roots:
        print("::error::found no Gradle roots (no settings.gradle.kts anywhere)")
        return 1

    check_roots(roots, table)
    declaring = check_ceilings(roots, table)
    check_dependabot(roots, table, declaring)
    check_compose(roots, table)
    check_ci_platforms(roots)
    check_per_root_workflows(roots)

    coords = len(table["ceilings"])
    deps = sum(len(m.deps) for r in roots for m in r.modules)
    print(f"Checked {len(roots)} Gradle roots · {sum(len(r.modules) for r in roots)} modules · "
          f"{deps} declared coordinates against {coords} ceilings")
    for line in receipt:
        print(f"  {line}")
    if problems:
        print()
        for problem in problems:
            print(f"::error::{problem}")
        print(f"\n{len(problems)} build envelope problem(s). See tools/gradle_envelope.json "
              f"for the envelope and its migration steps.")
        return 1
    print("Build envelope OK — every root is inside its compileSdk/minSdk/AGP/Kotlin limits, "
          "and dependabot.yml ignores every capped coordinate")
    return 0

if __name__ == "__main__":
    sys.exit(main())
