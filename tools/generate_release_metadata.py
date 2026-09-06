#!/usr/bin/env python3
from __future__ import annotations
import argparse, hashlib, json, re
from datetime import date, timezone, datetime
from pathlib import Path

APPS = {
  'iconpack': {'gradle':'app/build.gradle.kts','metadata':'Latestrelease/version.json','apk':'iconpack-release.apk','tag':'iconpack','minSdk':21},
  'coreline': {'gradle':'ticker/android/app/build.gradle.kts','metadata':'Latestrelease/coreline-version.json','apk':'coreline-release.apk','tag':'coreline','minSdk':24},
  'coreshift': {'gradle':'shift/app/build.gradle.kts','metadata':'Latestrelease/shift-version.json','apk':'coreshift-release.apk','tag':'shift','minSdk':26},
}

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
    meta_path=Path(cfg['metadata'])
    if meta_path.exists(): current=json.loads(meta_path.read_text())
    data={
      **current,
      'versionCode': int(gradle_value(gradle,'versionCode')),
      'versionName': gradle_value(gradle,'versionName'),
      'releaseDate': datetime.now(timezone.utc).date().isoformat(),
      'apkUrl': f"https://github.com/brevityA/CoreBuildsApps/releases/download/{cfg['tag']}/{cfg['apk']}",
      'apkSha256': sha256(args.apk),
      'releaseNotesUrl': 'https://github.com/brevityA/CoreBuildsApps/releases',
      'minSdk': cfg['minSdk'],
    }
    out=Path(args.out or cfg['metadata'])
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, indent=2)+'\n')
    print(f'wrote {out}: {data["versionName"]} code {data["versionCode"]} sha {data["apkSha256"]}')
if __name__ == '__main__': main()
