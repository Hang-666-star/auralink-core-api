#!/usr/bin/env python3
"""Read-only Knowledge delivery preflight; no downloads, writes or service calls.

This verifies reviewed byte identity and, optionally, the invoking service user's
access. The application's KnowledgeSnapshotService remains the authoritative
JSON/graph/GeoJSON validator. A successful preflight is not a license grant or
production acceptance.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys


FILES = {
    "poetry-graph.json": "AURALINK_KNOWLEDGE_GRAPH_SHA256",
    "poetry-stats.json": "AURALINK_KNOWLEDGE_STATS_SHA256",
    "china.json": "AURALINK_KNOWLEDGE_CHINA_SHA256",
}
ROOT_ENV = "AURALINK_KNOWLEDGE_SNAPSHOT_ROOT"
MAP_ENABLED_ENV = "AURALINK_KNOWLEDGE_MAP_ENABLED"
PROFILES = {"poetry-only": set(FILES) - {"china.json"}, "map-enabled": set(FILES)}
MAX_BYTES = 8 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def load_manifest(path):
    manifest = json.loads(path.read_text(encoding="utf-8"))
    require(manifest.get("schemaVersion") == 2, "Unsupported manifest schema")
    profile = manifest.get("releaseProfile")
    require(profile in PROFILES, "Invalid release profile")
    require(manifest.get("mapEnabledEnvironmentVariable") == MAP_ENABLED_ENV, "Invalid map-enabled binding")
    require(manifest.get("rootEnvironmentVariable") == ROOT_ENV, "Invalid root binding")
    entries = manifest.get("artifacts", [])
    require(isinstance(entries, list) and len(entries) == len(PROFILES[profile]), "Invalid profile artifact count")
    require({entry.get("filename") for entry in entries} == PROFILES[profile], "Invalid artifact filenames")
    for entry in entries:
        require(entry.get("hashEnvironmentVariable") == FILES[entry["filename"]], "Invalid hash binding")
        require(isinstance(entry.get("bytes"), int) and not isinstance(entry["bytes"], bool)
                and 0 < entry["bytes"] <= MAX_BYTES, "Invalid artifact size")
        require(isinstance(entry.get("sha256"), str)
                and re.fullmatch("[0-9a-f]{64}", entry["sha256"]), "Invalid artifact SHA-256")
    return manifest


def check_service_permissions(directory, paths):
    """Run as the actual non-root service account, including its actual groups/ACLs."""
    uid = os.geteuid()
    require(uid != 0, "Read-only verification must run as the non-root application service account")
    require(directory.stat().st_uid != uid, "Asset directory must have a separate owner")
    for path in [directory, *directory.parents]:
        require(os.access(path, os.X_OK, effective_ids=True), "Service cannot traverse an asset ancestor")
        require(not os.access(path, os.W_OK, effective_ids=True), "Service can write an asset directory or ancestor")
    for path in paths:
        require(path.stat().st_uid != uid, "Asset file must have a separate owner")
        require(os.access(path, os.R_OK, effective_ids=True), "Service cannot read an asset")
        require(not os.access(path, os.W_OK, effective_ids=True), "Service can write an asset")


def verify(manifest, directory, require_read_only=False, check_environment=False):
    require(directory.is_absolute(), "Snapshot root must be absolute")
    require(directory.is_dir() and not directory.is_symlink(), "Snapshot root must be a real directory")
    require(all(not path.is_symlink() for path in directory.parents), "Snapshot ancestors must not be symlinks")
    directory = directory.resolve(strict=True)
    require({path.name for path in directory.iterdir()} == PROFILES[manifest["releaseProfile"]],
            "Delivery directory must contain exactly the profile artifacts")
    paths = [directory / entry["filename"] for entry in manifest["artifacts"]]
    if require_read_only:
        check_service_permissions(directory, paths)
    if check_environment:
        expected_map = "true" if manifest["releaseProfile"] == "map-enabled" else "false"
        require(os.environ.get(MAP_ENABLED_ENV) == expected_map, "Runtime map-enabled binding does not match")
        if manifest["releaseProfile"] == "poetry-only":
            require(not os.environ.get(FILES["china.json"]), "Poetry-only configuration must exclude the map hash")
        root = os.environ.get(ROOT_ENV, "")
        require(bool(root) and Path(root).is_absolute()
                and Path(root).resolve(strict=True) == directory, "Runtime snapshot root binding does not match")
    results = []
    for entry, path in zip(manifest["artifacts"], paths):
        require(not path.is_symlink() and stat.S_ISREG(path.stat().st_mode), "Artifacts must be regular files without symlinks")
        require(path.stat().st_size == entry["bytes"], "Artifact byte count does not match: " + entry["filename"])
        with path.open("rb") as stream:
            data = stream.read(MAX_BYTES + 1)
        require(len(data) == entry["bytes"], "Artifact changed while being read: " + entry["filename"])
        digest = hashlib.sha256(data).hexdigest()
        require(digest == entry["sha256"], "Artifact SHA-256 does not match: " + entry["filename"])
        if check_environment:
            require(os.environ.get(entry["hashEnvironmentVariable"], "").lower() == digest,
                    "Runtime hash binding does not match: " + entry["filename"])
        results.append({"filename": entry["filename"], "bytes": len(data), "sha256": digest})
    return {
        "manifestId": manifest["manifestId"], "releaseProfile": manifest["releaseProfile"], "artifacts": results,
        "byteIdentity": "verified",
        "runtimeEnvironmentBindings": "verified" if check_environment else "not_checked",
        "serviceReadOnlyPermissions": "verified_for_invoking_uid" if require_read_only else "not_checked",
        "invokingUid": os.geteuid(),
        "productionAcceptance": "not_established",
        "licenseAuthorization": "not_established_by_this_tool",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot-root", required=True, type=Path)
    parser.add_argument("--manifest", type=Path,
                        default=Path(__file__).resolve().parents[1] / "docs/knowledge-assets-manifest.json")
    parser.add_argument("--require-read-only", action="store_true")
    parser.add_argument("--check-environment", action="store_true")
    args = parser.parse_args()
    try:
        result = verify(load_manifest(args.manifest), args.snapshot_root,
                        args.require_read_only, args.check_environment)
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        # Do not emit paths, environment values, raw data or exception contents.
        print(json.dumps({"status": "failed", "reason": "Asset identity, configuration or service permissions failed preflight; check the reviewed delivery procedure."}))
        return 1
    print(json.dumps({"status": "verified", **result}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
