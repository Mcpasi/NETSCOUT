import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import struct
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("prepare_model", Path(__file__).parents[1] / "prepare-relevance-model.py")
model = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(model)


class OfflineModelTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.cache = self.root / "cache"
        self.cache.mkdir()
        self.output = self.root / "assets"
        vocab = {token: index for index, token in enumerate(
            ["<pad>", "<eos>", "<bos>", "<unk>"] + [f"<0x{byte:02X}>" for byte in range(256)]
            + ["▁", "a", "b", "▁a", "▁ab", "▁b"]
        )}
        self.tokenizer = {
            "model": {"type": "BPE", "byte_fallback": True, "ignore_merges": False, "dropout": None,
                      "unk_token": "<unk>", "continuing_subword_prefix": None, "end_of_word_suffix": None,
                      "fuse_unk": True, "vocab": vocab, "merges": [["▁", "a"], ["▁a", "b"], ["▁", "b"]]},
            "normalizer": {"type": "Replace", "pattern": {"String": " "}, "content": "▁"},
            "pre_tokenizer": {"type": "Metaspace", "replacement": "▁", "prepend_scheme": "always", "split": True},
            "post_processor": {"type": "TemplateProcessing", "pair": [
                {"SpecialToken": {"id": "<bos>", "type_id": 0}}, {"Sequence": {"id": "A", "type_id": 0}},
                {"SpecialToken": {"id": "<eos>", "type_id": 0}}, {"Sequence": {"id": "B", "type_id": 0}},
                {"SpecialToken": {"id": "<eos>", "type_id": 0}},
            ], "special_tokens": {"<bos>": {"ids": [2]}, "<eos>": {"ids": [1]}}},
            "added_tokens": [{"content": "<bos>", "id": 2, "lstrip": False, "rstrip": False,
                              "single_word": False, "normalized": False, "special": True}],
        }
        files = {
            "model.onnx": b"pinned-model-weights",
            "tokenizer.json": json.dumps(self.tokenizer).encode(),
        }
        self.entries = {name: {"size": len(data), "sha256": hashlib.sha256(data).hexdigest(), "path": name} for name, data in files.items()}
        (self.root / "relevance-model.lock.json").write_text(json.dumps({"files": self.entries}))
        for name, data in files.items():
            (self.cache / self.entries[name]["sha256"]).write_bytes(data)
        for name in ("docs/relevance-model.md", "third-party/APACHE-2.0.txt", "third-party/ONNX-RUNTIME-LICENSE.txt", "third-party/ONNX-RUNTIME-ThirdPartyNotices.txt", "LICENSE", "NOTICE"):
            target = self.root / name
            target.parent.mkdir(exist_ok=True)
            target.write_text(name)
        root_patch = patch.object(model, "ROOT", self.root)
        root_patch.start()
        self.addCleanup(root_patch.stop)
        network = patch.object(model.urllib.request, "urlopen", side_effect=AssertionError("offline mode must not access the network"))
        self.network = network.start()
        self.addCleanup(network.stop)

    def test_verified_cache_builds_assets_and_license_notices_without_network(self):
        model.prepare(self.output, cache=self.cache, offline=True)
        self.assertEqual(b"pinned-model-weights", (self.output / "model.onnx").read_bytes())
        self.assertTrue((self.output / "tokenizer.bin").read_bytes().startswith(b"NSBP1"))
        self.assertEqual(json.loads((self.output / "MODEL.lock.json").read_text())["files"], self.entries)
        self.assertEqual("NOTICE", (self.output / "NETSCOUT-NOTICE.txt").read_text())
        self.network.assert_not_called()

    def test_download_only_does_not_generate_binary_assets(self):
        model.prepare(cache=self.cache, offline=True)
        self.assertFalse(self.output.exists())
        self.assertEqual(2, len(list(self.cache.iterdir())))
        self.network.assert_not_called()

    def test_missing_or_corrupt_model_fails_without_network(self):
        for name, entry in self.entries.items():
            path = self.cache / entry["sha256"]
            original = path.read_bytes()
            for content in (None, b"truncated", b"x" * entry["size"]):
                with self.subTest(name=name, content_size=len(content) if content else 0):
                    if content is None:
                        path.unlink(missing_ok=True)
                    else:
                        path.write_bytes(content)
                    with self.assertRaisesRegex(SystemExit, f"Missing or invalid pinned asset: {name}"):
                        model.prepare(self.output, cache=self.cache, offline=True)
                    self.assertFalse(self.output.exists())
                    self.network.assert_not_called()
            path.write_bytes(original)

    def test_compiler_preserves_alphabet_byte_fallback_merge_ranks_and_added_tokens(self):
        output = self.root / "tokenizer.bin"
        model.compile_tokenizer(self.tokenizer, output)
        with output.open("rb") as stream:
            self.assertEqual(b"NSBP1", stream.read(5))
            self.assertEqual((266, 3), struct.unpack(">II", stream.read(8)))
            self.assertEqual([(97, 261), (98, 262), (9601, 260)], [struct.unpack(">II", stream.read(8)) for _ in range(3)])
            self.assertEqual(tuple(range(4, 260)), struct.unpack(">256I", stream.read(1024)))
            self.assertEqual(3, struct.unpack(">I", stream.read(4))[0])
            self.assertEqual([
                ((260 << 32) | 261, 0, 263), ((260 << 32) | 262, 2, 265), ((263 << 32) | 262, 1, 264),
            ], [struct.unpack(">QII", stream.read(16)) for _ in range(3)])
            self.assertEqual(1, struct.unpack(">I", stream.read(4))[0])
            self.assertEqual(5, struct.unpack(">I", stream.read(4))[0])
            self.assertEqual(b"<bos>", stream.read(5))
            self.assertEqual((2, False), struct.unpack(">I?", stream.read(5)))
            self.assertEqual(b"", stream.read())

    def test_incompatible_tokenizer_configuration_fails_explicitly(self):
        for section, key, value in [
            ("model", "type", "Unigram"), ("model", "byte_fallback", False),
            ("normalizer", "content", " "), ("pre_tokenizer", "split", False),
        ]:
            with self.subTest(section=section, key=key):
                tokenizer = json.loads(json.dumps(self.tokenizer))
                tokenizer[section][key] = value
                with self.assertRaisesRegex(ValueError, "Unsupported Horizon BPE"):
                    model.compile_tokenizer(tokenizer, self.root / "bad.bin")
        tokenizer = json.loads(json.dumps(self.tokenizer))
        tokenizer["post_processor"]["pair"].insert(3, {"SpecialToken": {"id": "<eos>", "type_id": 0}})
        with self.assertRaisesRegex(ValueError, "pair template"):
            model.compile_tokenizer(tokenizer, self.root / "bad.bin")

    def test_compiled_tokenizer_drift_fails_without_network(self):
        lock = {"files": self.entries, "compiled_tokenizer": {"size": 1, "sha256": "0" * 64}}
        (self.root / "relevance-model.lock.json").write_text(json.dumps(lock))
        with self.assertRaisesRegex(SystemExit, "Compiled tokenizer differs"):
            model.prepare(self.output, cache=self.cache, offline=True)
        self.network.assert_not_called()


if __name__ == "__main__":
    unittest.main()
