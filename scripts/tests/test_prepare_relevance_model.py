import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
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
        files = {
            "model.onnx": b"pinned-model-weights",
            "tokenizer.json": json.dumps({
                "model": {"type": "Unigram", "unk_id": 3, "vocab": [["test", -1.0]]},
                "normalizer": {"precompiled_charsmap": ""},
            }).encode(),
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
        self.assertTrue((self.output / "tokenizer.bin").read_bytes().startswith(b"NSTK1"))
        self.assertEqual("NOTICE", (self.output / "NETSCOUT-NOTICE.txt").read_text())
        self.network.assert_not_called()

    def test_download_only_does_not_generate_binary_assets(self):
        model.prepare(cache=self.cache, offline=True)
        self.assertFalse(self.output.exists())
        self.assertEqual(2, len(list(self.cache.iterdir())))
        self.network.assert_not_called()

    def test_missing_or_corrupt_model_fails_without_network(self):
        path = self.cache / self.entries["model.onnx"]["sha256"]
        for content in (None, b"truncated", b"modified-model-bytes"):
            with self.subTest(content=content):
                if content is None:
                    path.unlink(missing_ok=True)
                else:
                    path.write_bytes(content)
                with self.assertRaisesRegex(SystemExit, "Missing or invalid pinned asset: model.onnx"):
                    model.prepare(self.output, cache=self.cache, offline=True)
                self.assertFalse(self.output.exists())
                self.network.assert_not_called()


if __name__ == "__main__":
    unittest.main()
