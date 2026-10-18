#!/usr/bin/env python3
"""Fail the build if its signing certificate differs from the committed dev identity."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
apk = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "app/build/outputs/apk/debug/app-debug.apk"
sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
signer = shutil.which("apksigner")
if not signer and sdk:
    candidate = Path(sdk) / "build-tools/35.0.0/apksigner"
    if candidate.exists():
        signer = str(candidate)
if not signer:
    sys.exit("apksigner missing: set ANDROID_HOME and install build-tools;35.0.0")

expected = (root / "debug-signing/certificate.sha256").read_text().strip().lower()
if not re.fullmatch(r"[a-f0-9]{64}", expected):
    sys.exit("Invalid committed debug certificate fingerprint")
result = subprocess.run([signer, "verify", "--verbose", "--print-certs", str(apk)], capture_output=True, text=True, check=True)
digests = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)", result.stdout)
if len(digests) != 1 or digests[0].lower() != expected:
    sys.exit("Debug signing identity changed: refusing to publish this APK")
print(f"Debug APK verified; permanent SHA-256 certificate: {expected}")
