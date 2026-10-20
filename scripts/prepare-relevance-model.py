#!/usr/bin/env python3
"""Prepare pinned, verified model assets. No pip packages or runtime downloads required."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import tempfile
import time
import urllib.request

ROOT = Path(__file__).resolve().parent.parent


def verified(path, entry):
    if not path.is_file() or path.stat().st_size != entry["size"]:
        return False
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest() == entry["sha256"]


def prepare(output=None, *, cache=None, offline=False):
    lock = json.loads((ROOT / "relevance-model.lock.json").read_text())
    if cache is None:
        cache = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache")) / "netscout-models"
    cache.mkdir(parents=True, exist_ok=True)
    paths = {}
    for name, entry in lock["files"].items():
        path = cache / entry["sha256"]
        if not verified(path, entry):
            if offline:
                raise SystemExit(
                    f"Missing or invalid pinned asset: {name}. "
                    f"Run {Path(__file__).name} --download-only --cache-dir {cache} first."
                )
            url = f'https://huggingface.co/{lock["repository"]}/resolve/{lock["revision"]}/{entry["path"]}'
            for attempt in range(3):
                temporary = None
                try:
                    with tempfile.NamedTemporaryFile(dir=cache, delete=False) as target:
                        temporary = Path(target.name)
                        with urllib.request.urlopen(url, timeout=60) as response:
                            count = 0
                            while chunk := response.read(1024 * 1024):
                                count += len(chunk)
                                if count > entry["size"]:
                                    raise ValueError("Model download exceeded pinned size")
                                target.write(chunk)
                    if not verified(temporary, entry):
                        raise ValueError("Model checksum did not match lock file")
                    temporary.replace(path)
                    break
                except Exception:
                    if attempt == 2:
                        raise
                    time.sleep(1 + attempt)
                finally:
                    if temporary:
                        temporary.unlink(missing_ok=True)
        paths[name] = path
    if output is None:
        print("Pinned model downloads verified; ready for an offline model build.")
        return
    output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(paths["model.onnx"], output / "model.onnx")
    # Compact streaming format avoids a large JSON object graph on Android.
    tokenizer = json.loads(paths["tokenizer.json"].read_text())
    assert tokenizer["model"]["type"] == "Unigram" and tokenizer["model"]["unk_id"] == 3
    with (output / "tokenizer.bin").open("wb") as stream:
        stream.write(b"NSTK1")
        vocab = tokenizer["model"]["vocab"]
        stream.write(struct.pack(">I", len(vocab)))
        for token, score in vocab:
            encoded = token.encode("utf-8")
            stream.write(struct.pack(">I", len(encoded)))
            stream.write(encoded)
            stream.write(struct.pack(">f", score))
        normalizer = base64.b64decode(tokenizer["normalizer"]["precompiled_charsmap"])
        stream.write(struct.pack(">I", len(normalizer)))
        stream.write(normalizer)
    shutil.copyfile(ROOT / "docs/relevance-model.md", output / "MODEL.md")
    shutil.copyfile(ROOT / "third-party/APACHE-2.0.txt", output / "LICENSE.txt")
    shutil.copyfile(ROOT / "third-party/ONNX-RUNTIME-LICENSE.txt", output / "ONNX-RUNTIME-LICENSE.txt")
    shutil.copyfile(ROOT / "third-party/ONNX-RUNTIME-ThirdPartyNotices.txt", output / "ONNX-RUNTIME-ThirdPartyNotices.txt")
    shutil.copyfile(ROOT / "LICENSE", output / "NETSCOUT-LICENSE.txt")
    shutil.copyfile(ROOT / "NOTICE", output / "NETSCOUT-NOTICE.txt")
    print("Verified multilingual relevance model and tokenizer prepared.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--output", type=Path)
    mode.add_argument("--download-only", action="store_true", help="Fetch and verify inputs without generating assets")
    parser.add_argument("--cache-dir", type=Path, help="Directory for SHA-256-addressed input files")
    parser.add_argument("--offline", action="store_true", help="Fail instead of downloading missing or invalid inputs")
    args = parser.parse_args()
    prepare(args.output, cache=args.cache_dir, offline=args.offline)
