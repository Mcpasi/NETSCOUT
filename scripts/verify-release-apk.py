#!/usr/bin/env python3
"""Check the distributable release, including assets, stable version and no signer."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
apk = Path(sys.argv[1])
sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
tools = sdk / "build-tools/35.0.0"
gradle = (ROOT / "app/build.gradle.kts").read_text()
code = re.search(r"versionCode = (\d+)", gradle)[1]
name = re.search(r'versionName = "([^"]+)"', gradle)[1]
badging = subprocess.check_output([str(tools / "aapt"), "dump", "badging", str(apk)], text=True)
if not re.search(rf"package: name='de.netscout.app' versionCode='{code}' versionName='{re.escape(name)}'", badging):
    sys.exit("Release package or version does not match the committed metadata")
if "application-debuggable" in badging:
    sys.exit("Release APK is debuggable")
signing = subprocess.run([str(tools / "apksigner"), "verify", str(apk)], capture_output=True, text=True)
if signing.returncode == 0 or "Missing META-INF/MANIFEST.MF" not in signing.stderr:
    sys.exit("Expected a valid unsigned APK, ready for F-Droid signing: " + signing.stderr)
lock = json.loads((ROOT / "relevance-model.lock.json").read_text())
with zipfile.ZipFile(apk) as archive:
    with archive.open("assets/netscout-relevance/model.onnx") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != lock["files"]["model.onnx"]["sha256"]:
            sys.exit("Packaged model differs from the pinned model")
    if json.loads(archive.read("assets/netscout-relevance/MODEL.lock.json")) != lock:
        sys.exit("Packaged model identity differs from the pinned lock file")
    tokenizer = archive.read("assets/netscout-relevance/tokenizer.bin")
    expected_tokenizer = lock["compiled_tokenizer"]
    if (not tokenizer.startswith(expected_tokenizer["format"].encode("ascii"))
            or len(tokenizer) != expected_tokenizer["size"]
            or hashlib.sha256(tokenizer).hexdigest() != expected_tokenizer["sha256"]):
        sys.exit("Packaged tokenizer differs from the pinned Horizon BPE asset")
    for asset, source in (("MODEL.md", "docs/relevance-model.md"), ("NETSCOUT-NOTICE.txt", "NOTICE")):
        if archive.read("assets/netscout-relevance/" + asset) != (ROOT / source).read_bytes():
            sys.exit("Packaged model documentation or notices differ from source: " + asset)
    for asset in ("MODEL.md", "MODEL.lock.json", "LICENSE.txt", "ONNX-RUNTIME-LICENSE.txt", "ONNX-RUNTIME-ThirdPartyNotices.txt", "NETSCOUT-LICENSE.txt", "NETSCOUT-NOTICE.txt", "tokenizer.bin"):
        if archive.getinfo("assets/netscout-relevance/" + asset).file_size == 0:
            sys.exit("Empty release asset: " + asset)
print(f"Verified unsigned NetScout {name} ({code}) release with pinned model and license notices.")
