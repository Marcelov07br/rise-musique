"""Generate only public release assets; never include signing keys or source code."""
import hashlib
import json
import re
import shutil
from pathlib import Path

root = Path(__file__).resolve().parents[1]
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
code = int(re.search(r"versionCode = (\d+)", gradle)[1])
name = re.search(r'versionName = "([^"]+)"', gradle)[1]
apk = root / "app/build/outputs/apk/release/app-release.apk"
if not apk.exists():
    raise SystemExit("Signed release APK is missing; build assembleRelease first.")
output = root / "delivery" / f"v{name}"
output.mkdir(parents=True, exist_ok=True)
asset = f"RiseMusique-GPT-{name}.apk"
shutil.copyfile(apk, output / asset)
manifest = {"versionCode": code, "versionName": name, "asset": asset,
            "sha256": hashlib.sha256(apk.read_bytes()).hexdigest()}
(output / "update.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
notes = root / "RELEASE_NOTES.md"
shutil.copyfile(notes, output / "RELEASE_NOTES.md")
print(f"Release assets ready in {output}")
