#!/usr/bin/env python3
"""Prove `QrCode.java` builds real QR symbols, using ZXing as the oracle.

The request screen hands a prefilled GitHub issue URL to a phone by drawing it
as a QR code, because a television has no browser to open it in. That makes the
QR the whole handoff, and a QR that is subtly wrong fails in the worst possible
way: the camera simply never locks on, and nothing on screen says why. So the
encoder is not reviewed, it is verified.

ZXing is downloaded here, pinned and hash-checked, and used only to *decode*.
It is never a dependency of the APK — see the note at the top of QrCode.java for
why the app carries its own encoder instead.

Two gates, run by tools/qr/QrRoundTrip.java:

* every payload length the encoder supports, under all eight masks, decoded
  straight from the module matrix — no image, no detector;
* the prefill URLs this repo's own issue forms and appfilter actually produce,
  rendered as images at three module sizes and put through the full reader.

```bash
python tools/check_qr.py            # both gates
python tools/check_qr.py --corpus   # print the URL corpus and stop
```

Needs a JDK (any 17+) and network on first run; the jar is cached under
`.cache/`. `--offline-ok` downgrades an unreachable Maven Central to a skip, for
a local run without network — CI must not pass it, or the gate silently stops
gating.
"""
from __future__ import annotations

import argparse
import hashlib
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.parse import quote_plus

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))

ZXING_VERSION = "3.5.3"
ZXING_URL = ("https://repo1.maven.org/maven2/com/google/zxing/core/"
             f"{ZXING_VERSION}/core-{ZXING_VERSION}.jar")
# Pinned so a swapped artifact cannot quietly become the thing that says our
# encoder is fine.
ZXING_SHA256 = "8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82"
CACHE = ROOT / ".cache"

ENCODER = ROOT / "app/src/main/java/tv/corebuilds/iconpack/QrCode.java"
HARNESS = ROOT / "tools/qr/QrRoundTrip.java"
APPFILTER = ROOT / "app/src/main/res/xml/appfilter.xml"
ISSUE_RES = ROOT / "app/src/main/res/values/issue_forms.xml"

COMPONENT = re.compile(r"ComponentInfo\{([^}]+)\}")


def fail(message: str) -> None:
    print(f"::error::{message}")
    raise SystemExit(1)


def zxing_jar(offline_ok: bool) -> Path | None:
    CACHE.mkdir(exist_ok=True)
    jar = CACHE / f"zxing-core-{ZXING_VERSION}.jar"
    if jar.exists() and hashlib.sha256(jar.read_bytes()).hexdigest() == ZXING_SHA256:
        return jar
    try:
        with urllib.request.urlopen(ZXING_URL, timeout=60) as response:
            blob = response.read()
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        if offline_ok:
            print(f"skipping: cannot reach Maven Central ({exc}); "
                  "the QR encoder was NOT verified")
            return None
        fail(f"cannot download the ZXing oracle from {ZXING_URL}: {exc}")
    digest = hashlib.sha256(blob).hexdigest()
    if digest != ZXING_SHA256:
        fail(f"ZXing jar hash is {digest}, expected {ZXING_SHA256} — refusing to "
             "verify the encoder against an artifact this script did not pin")
    jar.write_bytes(blob)
    return jar


def issue_forms() -> dict[str, str]:
    """The generated resource the app itself reads, so the corpus matches it."""
    if not ISSUE_RES.exists():
        fail(f"missing {ISSUE_RES.relative_to(ROOT)}; run "
             "python tools/build_issue_prefills.py")
    values: dict[str, str] = {}
    for node in ET.parse(ISSUE_RES).getroot().iter("string"):
        text = (node.text or "")
        # The generator quotes a value whose leading or trailing space Android
        # would otherwise strip; the quotes are markup, not content.
        if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
            text = text[1:-1]
        values[node.get("name", "")] = text.replace("\\'", "'").replace('\\"', '"')
    return values


def mapped_components() -> list[str]:
    """Every component the pack already claims, longest first.

    Length is the point: the corpus should be dominated by the worst cases,
    because a long component is what pushes a URL into a bigger, harder to scan
    symbol.
    """
    if not APPFILTER.exists():
        fail(f"missing {APPFILTER.relative_to(ROOT)}")
    found = COMPONENT.findall(APPFILTER.read_text(encoding="utf-8"))
    # Total order, not just by length. Sorting a set on length alone leaves
    # equal-length entries in set iteration order, which moves between
    # processes because string hashing is seeded per run — so `[:150]` below
    # would take a different 150 components each time and the corpus would not
    # be reproducible. It passed every run regardless, which is exactly what
    # makes it worth pinning: a failure that cannot be reproduced is not a
    # finding, and the wobble showed up only as scan totals that disagreed
    # between runs of an unchanged tree.
    return sorted(set(found), key=lambda c: (-len(c), c))


def app_names() -> list[str]:
    """Display names from the appfilter's section comments."""
    names = re.findall(r"<!-- (.+?) -->", APPFILTER.read_text(encoding="utf-8"))
    return [n for n in names if not n.startswith("Generated")]


# Bytes a version 11 symbol holds at ECC M. Past this the symbol grows to a
# size that is materially harder for a phone to lock onto across a room, so the
# request screen sheds optional parameters instead.
BUDGET = 251


def build_url(endpoint: str, template: str, title: str, name: str,
              component: str, field_app: str, field_component: str) -> str:
    """The prefill URL, shortened the way RequestIconActivity shortens it.

    This mirrors `RequestIconActivity.issueUrl`. Both sides read the generated
    resource rather than a literal, and both shed in the same order: the title
    first (the form supplies its own prefix anyway), then the app name (which
    the reporter can retype from the screen), never the component — that is the
    one value the reporter cannot get at without adb, and the entire reason the
    screen exists.

    The form's own `labels:` are applied by GitHub when `template=` is used, so
    no labels parameter is sent; it would only spend budget.
    """
    def url(parts: list[str]) -> str:
        return f"{endpoint}?" + "&".join(parts)

    base = [f"template={quote_plus(template)}"]
    tail = [f"{field_component}={quote_plus(component)}"]
    full = url(base + [f"title={quote_plus(title + name)}",
                       f"{field_app}={quote_plus(name)}"] + tail)
    if len(full.encode("utf-8")) <= BUDGET:
        return full
    without_title = url(base + [f"{field_app}={quote_plus(name)}"] + tail)
    if len(without_title.encode("utf-8")) <= BUDGET:
        return without_title
    return url(base + tail)


def corpus() -> list[str]:
    """The prefill URLs the request screen will build, worst cases first."""
    forms = issue_forms()
    endpoint = forms["issue_endpoint"]
    field_app = forms["issue_field_app"]
    field_component = forms["issue_field_component"]
    components = mapped_components()
    names = app_names() or ["Example TV"]
    longest_name = max(names, key=len)

    urls: list[str] = []
    for case in ("missing", "mapping"):
        template = forms[f"issue_{case}_template"]
        title = forms[f"issue_{case}_title"]
        for index, component in enumerate(components[:150]):
            # Pair each component with a rotating name, and separately with the
            # longest name there is, so the true worst case is always in here
            # rather than only when the rotation happens to land on it.
            for name in (names[index % len(names)], longest_name):
                urls.append(build_url(endpoint, template, title, name, component,
                                      field_app, field_component))
    return sorted(set(urls), key=lambda u: (-len(u), u))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Verify the app's QR encoder against ZXing.")
    parser.add_argument("--corpus", action="store_true",
                        help="print the URL corpus and exit")
    parser.add_argument("--offline-ok", action="store_true",
                        help="skip instead of failing when Maven Central is "
                             "unreachable; never pass this in CI")
    args = parser.parse_args(argv)

    urls = corpus()
    longest = max(urls, key=len)
    if args.corpus:
        for url in urls:
            print(url)
        print(f"\n{len(urls)} URLs, longest {len(longest)} chars", file=sys.stderr)
        return 0

    for tool in ("javac", "java"):
        if shutil.which(tool) is None:
            fail(f"{tool} not found; this gate needs a JDK")

    jar = zxing_jar(args.offline_ok)
    if jar is None:
        return 0

    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        (work / "corpus.txt").write_text("\n".join(urls), encoding="utf-8")
        classes = work / "classes"
        classes.mkdir()
        compile_cmd = ["javac", "-nowarn", "-encoding", "UTF-8",
                       "-d", str(classes), "-cp", str(jar),
                       str(ENCODER), str(HARNESS)]
        result = subprocess.run(compile_cmd, capture_output=True, text=True)
        if result.returncode != 0:
            fail("the QR encoder or its harness does not compile:\n"
                 + result.stdout + result.stderr)

        run = subprocess.run(
            ["java", "-cp", f"{jar}:{classes}", "QrRoundTrip", str(work / "corpus.txt")],
            capture_output=True, text=True)
        # JAVA_TOOL_OPTIONS chatter goes to stderr on some JDKs; keep it out of
        # the receipt but show it when something actually failed.
        print(run.stdout.rstrip())
        if run.returncode != 0:
            print(run.stderr.rstrip(), file=sys.stderr)
            fail("the QR encoder does not round-trip through ZXing")

    print(f"longest prefill URL {len(longest)} chars")
    return 0


if __name__ == "__main__":
    sys.exit(main())
