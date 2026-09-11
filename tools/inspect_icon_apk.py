#!/usr/bin/env python3
"""Inspect a locally downloaded APK without installing or executing it.

Optional research dependency: pip install androguard==4.1.4
Usage: python tools/inspect_icon_apk.py app.apk --source-url https://vendor/app.apk \
           --output tools/reference/app

Only launcher manifest evidence and the application's referenced icon/banner
resources are exported. Never commit the APK itself. This tool is not part of
normal/offline icon generation; its small, reviewed receipts are committed inputs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
LAUNCHERS = {"android.intent.category.LAUNCHER", "android.intent.category.LEANBACK_LAUNCHER"}
MAX_RESOURCE_BYTES = 4 * 1024 * 1024


def activity_name(package: str, name: str) -> str:
    if name.startswith("."):
        return package + name
    return name if "." in name else package + "." + name


def launcher_components(root) -> list[dict]:
    """Use the alias name, not its target, and require MAIN + launcher together."""
    package = root.get("package", "")
    app = root.find("application")
    if app is None or app.get(ANDROID + "enabled") == "false":
        return []
    result = []
    for node in app:
        if node.tag not in ("activity", "activity-alias"):
            continue
        if node.get(ANDROID + "enabled") == "false" or node.get(ANDROID + "exported") == "false":
            continue
        categories = set()
        for intent in node.findall("intent-filter"):
            actions = {e.get(ANDROID + "name") for e in intent.findall("action")}
            if "android.intent.action.MAIN" in actions:
                categories |= {e.get(ANDROID + "name") for e in intent.findall("category")} & LAUNCHERS
        if not categories:
            continue
        name = activity_name(package, node.get(ANDROID + "name", ""))
        item = {"component": f"{package}/{name}", "kind": node.tag,
                "categories": sorted(categories)}
        if node.tag == "activity-alias":
            item["target_activity"] = activity_name(package, node.get(ANDROID + "targetActivity", ""))
        result.append(item)
    return sorted(result, key=lambda row: row["component"])


def resource_path(name: str) -> Path:
    """Reject traversal, absolute paths and non-resource ZIP members."""
    posix = PurePosixPath(name)
    if (posix.is_absolute() or ".." in posix.parts or "\\" in name
            or not posix.parts or posix.parts[0] != "res"):
        raise ValueError(f"not a safe APK resource path: {name}")
    return Path(*posix.parts)


def inspect(apk_path: Path, source_url: str, output: Path) -> dict:
    from loguru import logger
    logger.disable("androguard")
    from androguard.core.apk import APK
    from androguard.core.axml import AXMLPrinter

    apk = APK(str(apk_path))
    root = apk.get_android_manifest_xml()
    launchers = launcher_components(root)
    if not launchers:
        raise ValueError("APK has no enabled exported MAIN launcher activity/alias")
    app = root.find("application")
    refs = {key: app.get(ANDROID + key) for key in ("icon", "roundIcon", "banner")
            if app.get(ANDROID + key)}
    resources = apk.get_android_resources()
    output.mkdir(parents=True, exist_ok=True)
    exported, seen, resolved = [], set(), {}

    def export(ref: str):
        if ref in seen:
            return
        seen.add(ref)
        if len(seen) > 128:
            raise ValueError("icon resource graph unexpectedly large")
        if re.fullmatch(r"@[0-9a-fA-F]{8}", ref):
            values = resources.get_resolved_res_configs(int(ref[1:], 16))
            resolved[ref] = sorted({str(value) for _, value in values})
            for _, value in values:
                if isinstance(value, str) and (value.startswith("res/") or value.startswith("@")):
                    export(value)
            return
        if not ref.startswith("res/"):
            return
        path = resource_path(ref)
        raw = apk.get_file(ref)
        if len(raw) > MAX_RESOURCE_BYTES:
            raise ValueError(f"icon resource too large: {ref}")
        data = raw
        if ref.endswith(".xml"):
            xml = AXMLPrinter(raw).get_xml_obj()
            data = ET.tostring(ET.fromstring(ET.tostring(xml)), encoding="utf-8", xml_declaration=True)
            for node in xml.iter():
                for value in node.attrib.values():
                    if value.startswith("@"):
                        export(value)
        dest = output / path
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
        exported.append({"path": ref, "apk_sha256": hashlib.sha256(raw).hexdigest(),
                         "export_sha256": hashlib.sha256(data).hexdigest(),
                         "decoded_binary_xml": ref.endswith(".xml")})

    for ref in refs.values():
        export(ref)
    receipt = {
        "source_url": source_url,
        "retrieved_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "apk_sha256": hashlib.sha256(apk_path.read_bytes()).hexdigest(),
        "apk_bytes": apk_path.stat().st_size,
        "app_name": apk.get_app_name(), "package": apk.package,
        "version_name": apk.get_androidversion_name(),
        "version_code": int(apk.get_androidversion_code()),
        "verification": "Static manifest/resource inspection only; not installed or device-tested.",
        "launchers": launchers, "application_artwork": refs,
        "resolved_artwork_values": resolved,
        "resources": sorted(exported, key=lambda row: row["path"]),
    }
    (output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    return receipt


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("apk", type=Path)
    ap.add_argument("--source-url", required=True)
    ap.add_argument("--output", type=Path, required=True)
    args = ap.parse_args()
    print(json.dumps(inspect(args.apk, args.source_url, args.output), indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
