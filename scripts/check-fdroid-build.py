#!/usr/bin/env python3
"""Run the F-Droid recipe against this checkout's committed HEAD in isolation."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

from ruamel.yaml import YAML
from fdroidserver import common

ROOT = Path(__file__).resolve().parent.parent
APP_ID = "de.netscout.app"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--work-dir", type=Path, required=True, help="New, empty F-Droid working directory")
    args = parser.parse_args()
    yaml = YAML()  # F-Droid metadata uses YAML 1.2: "yes" is a flavor string.
    work = args.work_dir.resolve()
    if work.exists() and any(work.iterdir()):
        parser.error("--work-dir must be empty; existing files will not be replaced")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    gradle = shutil.which("gradle")
    if not sdk or not gradle:
        parser.error("Set ANDROID_HOME and put Gradle 8.11.1 on PATH (F-Droid uses its own Gradle launcher)")
    work.mkdir(parents=True, exist_ok=True)
    (work / "metadata").mkdir()
    recipe = work / "metadata" / f"{APP_ID}.yml"
    shutil.copyfile(ROOT / "fdroid/metadata" / recipe.name, recipe)
    shutil.copytree(ROOT / "fdroid/config", work / "config")
    with (work / "config.yml").open("w") as stream:
        yaml.dump({
            "sdk_path": sdk,
            "gradle": gradle,
            "java_paths": {"17": os.environ["JAVA_HOME"]},
        }, stream)
    # Validate the submission metadata before selecting the local CI revision.
    subprocess.run(["fdroid", "lint", APP_ID], cwd=work, check=True)
    metadata = yaml.load(recipe)
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    metadata["Repo"] = str(ROOT)
    metadata["Builds"][-1]["commit"] = revision
    # sudo is performed by the official buildserver. CI installs the same JDK
    # and Python in its setup steps; everything else uses the unchanged recipe.
    metadata["Builds"][-1].pop("sudo", None)
    with recipe.open("w") as stream:
        yaml.dump(metadata, stream)
    # Populate the checkout through F-Droid's VCS layer before fdroidserver
    # 2.4.5 derives SOURCE_DATE_EPOCH. The build still performs its full scan.
    checkout = work / "build" / APP_ID
    checkout.parent.mkdir()
    common.getvcs("git", str(ROOT), str(checkout)).gotorevision(revision)
    subprocess.run([
        "fdroid", "build", "--test", "--stop", "--no-tarball", "--scan-binary", "--refresh-scanner", APP_ID,
    ], cwd=work, check=True)
    unsigned = work / "unsigned"
    unsigned.mkdir(exist_ok=True)
    code = metadata["Builds"][-1]["versionCode"]
    shutil.copyfile(work / "tmp" / f"{APP_ID}_{code}.apk", unsigned / "netscout-release-unsigned.apk")
    print(f"F-Droid source scan, release build and APK scan passed for {revision}.")


if __name__ == "__main__":
    main()
