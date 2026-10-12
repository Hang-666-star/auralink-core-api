"""Synthetic asset-delivery preflight tests. No live services or real data."""

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "scripts/verify-knowledge-assets.py"
SPEC = importlib.util.spec_from_file_location("knowledge_assets", SCRIPT)
assets = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(assets)


class KnowledgeAssetTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "synthetic-assets"
        self.root.mkdir()
        entries = []
        for filename, variable in assets.FILES.items():
            if filename == "china.json":
                continue
            content = json.dumps({"fixture": filename}).encode()
            (self.root / filename).write_bytes(content)
            entries.append({"filename": filename, "bytes": len(content),
                            "sha256": hashlib.sha256(content).hexdigest(),
                            "hashEnvironmentVariable": variable})
        self.manifest = {"schemaVersion": 2, "releaseProfile": "poetry-only",
                         "mapEnabledEnvironmentVariable": assets.MAP_ENABLED_ENV, "manifestId": "synthetic-test-only",
                         "rootEnvironmentVariable": assets.ROOT_ENV, "artifacts": entries}
        self.manifest_path = Path(self.temp.name) / "manifest.json"
        self.manifest_path.write_text(json.dumps(self.manifest))

    def test_exact_bytes_and_explicit_configuration(self):
        environment = {assets.ROOT_ENV: str(self.root), assets.MAP_ENABLED_ENV: "false"}
        environment.update({e["hashEnvironmentVariable"]: e["sha256"] for e in self.manifest["artifacts"]})
        with mock.patch.dict(os.environ, environment, clear=True):
            result = assets.verify(assets.load_manifest(self.manifest_path), self.root, check_environment=True)
        self.assertEqual("verified", result["byteIdentity"])
        self.assertEqual("verified", result["runtimeEnvironmentBindings"])
        self.assertEqual("not_checked", result["serviceReadOnlyPermissions"])
        self.assertEqual("not_established", result["productionAcceptance"])

    def test_missing_or_unexpected_file_rejected(self):
        (self.root / "poetry-stats.json").unlink()
        with self.assertRaisesRegex(ValueError, "exactly the profile"):
            assets.verify(self.manifest, self.root)
        (self.root / "poetry-stats.json").write_text("fixture")
        (self.root / "private.env").write_text("synthetic only")
        with self.assertRaisesRegex(ValueError, "exactly the profile"):
            assets.verify(self.manifest, self.root)

    def test_same_length_tampering_rejected(self):
        path = self.root / "poetry-graph.json"
        path.write_bytes(b"x" * path.stat().st_size)
        with self.assertRaisesRegex(ValueError, "SHA-256"):
            assets.verify(self.manifest, self.root)

    def test_file_and_root_symlinks_rejected(self):
        path = self.root / "poetry-stats.json"
        external = Path(self.temp.name) / "external.json"
        path.rename(external)
        path.symlink_to(external)
        with self.assertRaisesRegex(ValueError, "without symlinks"):
            assets.verify(self.manifest, self.root)
        alias = Path(self.temp.name) / "alias"
        alias.symlink_to(self.root, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "real directory"):
            assets.verify(self.manifest, alias)

    def test_absent_and_mismatched_configuration_rejected(self):
        with mock.patch.dict(os.environ, {assets.MAP_ENABLED_ENV: "false"}, clear=True):
            with self.assertRaisesRegex(ValueError, "root binding"):
                assets.verify(self.manifest, self.root, check_environment=True)
        with mock.patch.dict(os.environ, {assets.ROOT_ENV: str(self.root), assets.MAP_ENABLED_ENV: "false"}, clear=True):
            with self.assertRaisesRegex(ValueError, "hash binding"):
                assets.verify(self.manifest, self.root, check_environment=True)

    def test_release_profile_and_map_configuration_cannot_disagree(self):
        environment = {assets.ROOT_ENV: str(self.root), assets.MAP_ENABLED_ENV: "false"}
        environment.update({e["hashEnvironmentVariable"]: e["sha256"] for e in self.manifest["artifacts"]})
        for value in (None, "true", "FALSE", "0"):
            changed = dict(environment)
            if value is None:
                changed.pop(assets.MAP_ENABLED_ENV)
            else:
                changed[assets.MAP_ENABLED_ENV] = value
            with mock.patch.dict(os.environ, changed, clear=True):
                with self.assertRaisesRegex(ValueError, "map-enabled binding"):
                    assets.verify(self.manifest, self.root, check_environment=True)
        environment[assets.FILES["china.json"]] = "0" * 64
        with mock.patch.dict(os.environ, environment, clear=True):
            with self.assertRaisesRegex(ValueError, "exclude the map hash"):
                assets.verify(self.manifest, self.root, check_environment=True)

    def test_no_map_delivery_rejects_leftover_map_and_profile_relabel(self):
        (self.root / "china.json").write_text("private old map must not be delivered")
        with self.assertRaisesRegex(ValueError, "exactly the profile"):
            assets.verify(self.manifest, self.root)
        self.manifest["releaseProfile"] = "map-enabled"
        self.manifest_path.write_text(json.dumps(self.manifest))
        with self.assertRaisesRegex(ValueError, "profile artifact count"):
            assets.load_manifest(self.manifest_path)

    def test_future_map_enabled_profile_requires_all_three_files_and_hashes(self):
        content = b'{"fixture":"map"}'
        (self.root / "china.json").write_bytes(content)
        self.manifest["releaseProfile"] = "map-enabled"
        self.manifest["artifacts"].append({"filename": "china.json", "bytes": len(content),
            "sha256": hashlib.sha256(content).hexdigest(), "hashEnvironmentVariable": assets.FILES["china.json"]})
        self.manifest_path.write_text(json.dumps(self.manifest))
        manifest = assets.load_manifest(self.manifest_path)
        environment = {assets.ROOT_ENV: str(self.root), assets.MAP_ENABLED_ENV: "true"}
        environment.update({e["hashEnvironmentVariable"]: e["sha256"] for e in manifest["artifacts"]})
        with mock.patch.dict(os.environ, environment, clear=True):
            self.assertEqual("verified", assets.verify(manifest, self.root, check_environment=True)["byteIdentity"])
            environment[assets.FILES["china.json"]] = "0" * 64
            with mock.patch.dict(os.environ, environment, clear=True):
                with self.assertRaisesRegex(ValueError, "hash binding"):
                    assets.verify(manifest, self.root, check_environment=True)
        (self.root / "china.json").unlink()
        with self.assertRaisesRegex(ValueError, "exactly the profile"):
            assets.verify(manifest, self.root)

    def test_root_and_asset_owner_cannot_claim_read_only(self):
        paths = [self.root / e["filename"] for e in self.manifest["artifacts"]]
        with mock.patch.object(assets.os, "geteuid", return_value=0):
            with self.assertRaisesRegex(ValueError, "non-root"):
                assets.check_service_permissions(self.root, paths)
        with mock.patch.object(assets.os, "geteuid", return_value=self.root.stat().st_uid):
            with self.assertRaisesRegex(ValueError, "separate owner|non-root"):
                assets.check_service_permissions(self.root, paths)

    def test_simulated_service_permissions_include_ancestors_and_file_access(self):
        # Simulates access(2), not acceptance of a real deployment service UID.
        paths = [self.root / e["filename"] for e in self.manifest["artifacts"]]
        with mock.patch.object(assets.os, "geteuid", return_value=self.root.stat().st_uid + 10000):
            with mock.patch.object(assets.os, "access", side_effect=lambda p, mode, **kw: mode != os.W_OK):
                assets.check_service_permissions(self.root, paths)
            with mock.patch.object(assets.os, "access", return_value=True):
                with self.assertRaisesRegex(ValueError, "write an asset directory"):
                    assets.check_service_permissions(self.root, paths)
            with mock.patch.object(assets.os, "access", return_value=False):
                with self.assertRaisesRegex(ValueError, "traverse"):
                    assets.check_service_permissions(self.root, paths)
            with mock.patch.object(assets.os, "access", side_effect=lambda p, mode, **kw: mode == os.X_OK):
                with self.assertRaisesRegex(ValueError, "cannot read"):
                    assets.check_service_permissions(self.root, paths)

    def test_manifest_cannot_select_other_files(self):
        self.manifest["artifacts"][0]["filename"] = "../private.env"
        self.manifest_path.write_text(json.dumps(self.manifest))
        with self.assertRaisesRegex(ValueError, "Invalid artifact filenames"):
            assets.load_manifest(self.manifest_path)


if __name__ == "__main__":
    unittest.main()
