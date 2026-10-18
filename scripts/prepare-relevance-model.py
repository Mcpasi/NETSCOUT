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


def prepare(output):
    lock = json.loads((ROOT / "relevance-model.lock.json").read_text())
    cache = Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache")) / "netscout-models"
    cache.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=True)
    paths = {}
    for name, entry in lock["files"].items():
        path = cache / entry["sha256"]
        if not verified(path, entry):
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
    print("Verified multilingual relevance model and tokenizer prepared.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    prepare(parser.parse_args().output)
