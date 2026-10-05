#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, re
from datetime import date, timezone, datetime
from pathlib import Path

APPS = {
  # 'source' is the build's own manifest: the release publishes its notes, not
  # the previous release's (Latestrelease/ lags until this runs).
  'iconpack': {'gradle':'app/build.gradle.kts','metadata':'Latestrelease/version.json','source':'app/src/main/assets/version.json','apk':'iconpack-release.apk','tag':'iconpack','minSdk':21},
  'coreline': {'gradle':'ticker/android/app/build.gradle.kts','metadata':'Latestrelease/coreline-version.json','apk':'coreline-release.apk','tag':'coreline','minSdk':24},
  'coreshift': {'gradle':'shift/app/build.gradle.kts','metadata':'Latestrelease/shift-version.json','apk':'coreshift-release.apk','tag':'shift','minSdk':26},
  # Core EQ keeps its own changelog, so its release highlights are read from
  # the section this tag is publishing - never from the previous entry, which
  # is what the icon pack gets from app/src/main/assets/version.json.
  'coreeq': {'gradle':'coreeq/app/build.gradle.kts','metadata':'Latestrelease/coreeq-version.json','apk':'coreeq-release.apk','tag':'coreeq','notesTag':'coreeq-v','minSdk':30,'changelog':'coreeq/CHANGELOG.md'},
}

MAX_HIGHLIGHTS = 8
BULLET_LEAD = re.compile(r'^- \*\*(.+?)\*\*', re.M)
RELEASED_HEADING = re.compile(r'^## \[(?P<label>[^\]]+)\] — (?P<date>\d{4}-\d{2}-\d{2})\s*$', re.M)

def changelog_highlights(path, version):
    """The leads of the newest released section, in the order it was written.

    Core EQ's changelog is its own file, so the icon pack's stamper
    (tools/prepare_release.py) cannot be pointed at it - and its [Unreleased]
    block is empty by the time a tag build runs. This reads the section the tag
    names instead: `## [1.1.1] — 2026-10-05`. A missing file, a missing section
    or a section with no bold leads yields no highlights, which the updater
    treats as "no bullets", never as a failure.
    """
    try:
        text = Path(path).read_text(encoding='utf-8')
    except OSError:
        return []
    heading = None
    for match in RELEASED_HEADING.finditer(text):
        if match.group('label') == version:
            heading = match.end()
            break
    if heading is None:
        return []
    rest = text[heading + 1:]
    next_heading = re.search(r'^## ', rest, re.M)
    section = rest[:next_heading.start()] if next_heading else rest
    return [lead.strip() for lead in BULLET_LEAD.findall(section)][:MAX_HIGHLIGHTS]

def gradle_value(text, name):
    m=re.search(rf'{name}\s*=\s*(?:"([^"]+)"|(\d+))', text)
    if not m: raise SystemExit(f'missing {name}')
    return m.group(1) or m.group(2)

def sha256(path):
    h=hashlib.sha256()
    with open(path,'rb') as f:
        for chunk in iter(lambda:f.read(1024*1024), b''):
            h.update(chunk)
    return h.hexdigest()

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('app', choices=APPS)
    ap.add_argument('--apk', required=True)
    ap.add_argument('--out')
    args=ap.parse_args()
    cfg=APPS[args.app]
    gradle=Path(cfg['gradle']).read_text()
    current={}
    meta_path=Path(cfg.get('source') or cfg['metadata'])
    if meta_path.exists(): current=json.loads(meta_path.read_text())
    version_name = gradle_value(gradle,'versionName')
    data={
      **current,
      'versionCode': int(gradle_value(gradle,'versionCode')),
      'versionName': version_name,
      'releaseDate': datetime.now(timezone.utc).date().isoformat(),
      'apkUrl': f"https://github.com/brevityA/CoreBuildsApps/releases/download/{cfg['tag']}/{cfg['apk']}",
      'apkSha256': sha256(args.apk),
      # Per-app: the icon pack's tags are plain `v*`, Core EQ's are
      # `coreeq-v*`, and a wrong tag in this URL is a 404 for the user.
      'releaseNotesUrl': (
        f"https://github.com/brevityA/CoreBuildsApps/releases/tag/{cfg['notesTag']}{version_name}"
        if cfg.get('notesTag')
        else 'https://github.com/brevityA/CoreBuildsApps/releases'
      ),
      'minSdk': cfg['minSdk'],
    }
    if cfg.get('changelog'):
      # Stale highlights would show the previous release's notes, so this key
      # is always rewritten - to the new section's leads, or to nothing.
      data['highlights'] = changelog_highlights(cfg['changelog'], version_name)
    out=Path(args.out or cfg['metadata'])
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, indent=2)+'\n')
    print(f'wrote {out}: {data["versionName"]} code {data["versionCode"]} sha {data["apkSha256"]}')
if __name__ == '__main__': main()
