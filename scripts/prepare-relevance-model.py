#!/usr/bin/env python3
"""Prepare pinned, verified model assets. No pip packages or runtime downloads required."""
import argparse
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


def compile_tokenizer(tokenizer, output):
    """Compile Horizon's BPE tables without a Python/native tokenizer dependency.

    Store only the alphabet, byte fallback, added tokens and ranked merges. The
    runtime never needs a large JSON object graph or boxed merge-pair objects.
    Integers are big-endian; merge pairs are sorted for primitive-array lookup.
    """
    model = tokenizer["model"]
    if (model["type"] != "BPE" or not model["byte_fallback"] or model["ignore_merges"]
            or model["dropout"] is not None or model["unk_token"] != "<unk>"
            or model["continuing_subword_prefix"] is not None or model["end_of_word_suffix"] is not None
            or not model["fuse_unk"]
            or tokenizer["normalizer"] != {"type": "Replace", "pattern": {"String": " "}, "content": "▁"}
            or tokenizer["pre_tokenizer"] != {"type": "Metaspace", "replacement": "▁", "prepend_scheme": "always", "split": True}):
        raise ValueError("Unsupported Horizon BPE tokenizer configuration")
    vocab = model["vocab"]
    if (set(vocab.values()) != set(range(len(vocab)))
            or [vocab.get(t) for t in ("<pad>", "<eos>", "<bos>", "<unk>")] != [0, 1, 2, 3]):
        raise ValueError("Unexpected Horizon vocabulary or special token IDs")
    processor = tokenizer["post_processor"]
    expected_pair = [
        {"SpecialToken": {"id": "<bos>", "type_id": 0}}, {"Sequence": {"id": "A", "type_id": 0}},
        {"SpecialToken": {"id": "<eos>", "type_id": 0}}, {"Sequence": {"id": "B", "type_id": 0}},
        {"SpecialToken": {"id": "<eos>", "type_id": 0}},
    ]
    if (processor["type"] != "TemplateProcessing" or processor["pair"] != expected_pair
            or processor["special_tokens"]["<bos>"]["ids"] != [2]
            or processor["special_tokens"]["<eos>"]["ids"] != [1]):
        raise ValueError("Unexpected Horizon pair template")
    alphabet = sorted((ord(token), token_id) for token, token_id in vocab.items() if len(token) == 1)
    merges = sorted(
        (vocab[left] << 32 | vocab[right], rank, vocab[left + right])
        for rank, (left, right) in enumerate(model["merges"])
    )
    if len({pair for pair, _, _ in merges}) != len(merges):
        raise ValueError("Duplicate BPE merge pair")
    added = tokenizer["added_tokens"]
    if any(t["single_word"] or t["normalized"] or t["rstrip"] or vocab.get(t["content"]) != t["id"] for t in added):
        raise ValueError("Unsupported added-token behavior")
    with output.open("wb") as stream:
        stream.write(b"NSBP1")
        stream.write(struct.pack(">II", len(vocab), len(alphabet)))
        for codepoint, token_id in alphabet:
            stream.write(struct.pack(">II", codepoint, token_id))
        # Gemma/mmBERT has literal tab/newline/CR pieces instead of their <0x..>
        # spellings. HF falls back to <unk> if a byte token is unavailable.
        stream.write(struct.pack(">256I", *(vocab.get(f"<0x{byte:02X}>", vocab["<unk>"]) for byte in range(256))))
        stream.write(struct.pack(">I", len(merges)))
        for pair, rank, token_id in merges:
            stream.write(struct.pack(">QII", pair, rank, token_id))
        stream.write(struct.pack(">I", len(added)))
        for token in added:
            encoded = token["content"].encode("utf-8")
            stream.write(struct.pack(">I", len(encoded)))
            stream.write(encoded)
            stream.write(struct.pack(">I?", token["id"], token["lstrip"]))


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
    tokenizer = json.loads(paths["tokenizer.json"].read_text())
    compile_tokenizer(tokenizer, output / "tokenizer.bin")
    if "compiled_tokenizer" in lock and not verified(output / "tokenizer.bin", lock["compiled_tokenizer"]):
        raise SystemExit("Compiled tokenizer differs from the pinned Horizon BPE asset")
    shutil.copyfile(ROOT / "relevance-model.lock.json", output / "MODEL.lock.json")
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
