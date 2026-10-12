# Knowledge external asset delivery

This is a reviewable procedure for a future coordinated release. It installs
neither assets nor application code by itself. Do not execute production steps
until the deployment owners approve the business-upgrade contract and map
redistribution evidence. The existing isolated preview stays untouched.

## Exact delivery identity

The machine-readable [manifest](knowledge-assets-manifest.json) identifies the
three inherited files by filename, byte length, SHA-256 and configuration binding.
It contains metadata only; obtain the actual bytes through the separately approved
artifact delivery channel. Do not commit the runtime bundle, its archive, real
environment files or credentials. Do not download substitute data at startup.

The reviewed source is `bailangguihun/auralink-web` revision
`aa1da07086696835de7c361886115e602a88c471`, `public/data/`. The snapshot contract,
graph limitations and exact hashes are in [knowledge-snapshots.md](knowledge-snapshots.md).
The graph has 711 unique IDs and 609 edges, with matching statistics and no
dangling endpoints. The map has 35 features, including one unnamed boundary.
Byte identity does not establish upstream extraction accuracy, geographic accuracy
or redistribution permission. The chart library's license is not map-dataset
license evidence; the unresolved exact-map authorization remains a release gate.
The [map provenance report](knowledge-map-provenance.md) records the exact
DataV/Amap content match and the permission evidence still required.

`scripts/verify-knowledge-assets.py` is a Python standard-library, read-only
preflight. It verifies the reviewed manifest, all three byte identities, optional
environment bindings and optional service-account permissions. It performs no
download, writes, API requests or provider calls. It requires exactly three
regular files and rejects symlinks in their paths. This delivery policy is
deliberately stricter than the runtime loader's accepted in-root symlinks.
The tool does not duplicate the Java JSON/graph/GeoJSON validation; the existing
Java validator and authenticated staging GETs remain authoritative for that.

## Prepare a new immutable version directory

The deployment owner chooses an absolute directory outside Git, frozen application
packages and application-owned writable trees. Record it in the c5ea/R25 release
record. Do not infer a production path from the local preview. Use a new version
directory, for example `<approved-asset-parent>/knowledge-legacy-aa1da070-20261012`;
the placeholder is not an existing or approved production path.

1. Check map authorization for these exact bytes. Transfer the three files to a
   private staging directory through the approved channel and run the identity
   preflight below. Never mix versions of the graph and statistics files.
2. The asset administrator, distinct from the non-root application service UID,
   creates a **new** sibling incoming directory beneath the approved asset parent.
   Abort if the intended version or incoming directory already exists; do not
   overwrite or reuse an existing release's assets.
3. Copy only the three manifest files. Give them the asset administrator as owner
   and a read-only service group, mode `0440`; give the directory the same owner
   and group, mode `0550`. The service must not own these files or directories,
   and must not be in an ACL/group that grants write access. Ensure every parent
   directory permits service traversal and cannot be renamed or written by the
   service. Use deployment-managed ownership/ACLs or a read-only mount; mode bits
   alone do not prove access when ACLs, mounts or capabilities are involved.
4. Run the identity and read-only preflight **as the actual service UID with its
   actual supplementary groups**. The verifier intentionally rejects root and
   asset-owner execution for read-only acceptance. Record mount restrictions and
   privileged service capabilities separately; this tool cannot prove that a
   process with mount/chown privileges will never change its own access.
5. Atomically rename the completed incoming directory to the new version directory
   on the same filesystem. Re-run preflight there. Do not edit an active directory
   or graph/stats pair in place. Keep the prior accepted directory for rollback.

These are future operator steps, not commands executed by this change. Ownership
changes and installation belong to the approved deployment procedure, not to this
read-only verifier or application startup.

## Bind and verify the configuration

Supply all four values in the existing private deployment configuration. Bindings
are optional in Java for compatibility, but **all three are required by this
delivery acceptance procedure**. No credentials belong in the following example:

```sh
export AURALINK_KNOWLEDGE_SNAPSHOT_ROOT='<approved-absolute-version-directory>'
export AURALINK_KNOWLEDGE_GRAPH_SHA256=899511b6b7d02e6ba515986db651c67b3702497d2031bdbb75029c34491da2c0
export AURALINK_KNOWLEDGE_STATS_SHA256=27e91284d28c05c9b7dde9fcb4181c713761d3c2eebf84816b45c9f7d9af7501
export AURALINK_KNOWLEDGE_CHINA_SHA256=206cf7fd5acba4bb08d9586899acc144cea2b29d9e93ff70f93895c5e5ffde77

# Run in the reviewed Core checkout, as the actual application service UID.
# Its interpreter, manifest and verifier must also be readable/traversable.
python3 scripts/verify-knowledge-assets.py \
  --snapshot-root "$AURALINK_KNOWLEDGE_SNAPSHOT_ROOT" \
  --check-environment --require-read-only
```

For byte identity only, omit the two switches. Successful output explicitly marks
unchecked permissions/configuration; it never claims production acceptance or a
license grant. Nonzero exit is a blocked delivery preflight. Failure output omits
raw data, filesystem paths and configuration values. Do not treat a successful
root invocation without `--require-read-only` as service-account acceptance.

Equivalent Spring properties are `auralink.knowledge.snapshot-root`,
`auralink.knowledge.graph-sha256`, `auralink.knowledge.stats-sha256` and
`auralink.knowledge.china-sha256`. If configuration uses these properties, the
release operator must also compare the effective property values to the manifest;
`--check-environment` verifies the invoking process environment only and cannot
inspect JVM property precedence or prove which configuration a running JVM read.
Record the selected directory and bindings in the coordinated release record,
not public API responses. Activate changed configuration only through the
approved Core lifecycle; c5ea remains the only Next lifecycle owner.

## Staging API acceptance and recovery

Use the existing approved test authentication mechanism; never print tokens or
create production accounts. Through the actual candidate Core, verify:

| Scenario | Expected result |
| --- | --- |
| Anonymous GET of graph or map | Existing JSON 401; no data or paths. |
| Valid graph pair, authenticated | 200, `Cache-Control: no-store`, the two manifest hashes, 711 nodes/609 edges and matching summaries. |
| Valid map, authenticated | 200, `Cache-Control: no-store`, manifest SHA and 35 features; unneeded metadata omitted. |
| Missing/unconfigured/unreadable file, wrong hash or malformed content | JSON 503 `KNOWLEDGE_SNAPSHOT_UNAVAILABLE`, no internal paths; retry remains available. |
| Map unavailable | Painting statistics/numeric geography and poetry continue independently. |
| Graph or stats unavailable | Painting statistics and map remain independent; no substitute graph is generated. |
| Invalid query parameters | Existing JSON 400; paths cannot be selected by clients. |

Perform missing/invalid tests against a disposable staging asset copy, never the
user's running preview or production directory. File reads are lazy per request;
with an unchanged configured directory and bindings, restoring the exact valid
files makes the next request recover without an application restart. This is a
failure test, not the approved version-update procedure. For a real update or
repair, provision a new complete validated version directory and matching
bindings, then coordinate the Core configuration switch/restart. If the new
candidate fails acceptance, switch through the approved controller to the prior
accepted Core/configuration/directory; do not restore the database or delete any
huaniao records, existing media or user assets. Keep logs and artifact identities.

When asset hashes change, changing files alone while retaining the old configured
hashes must remain a 503. Never recover by disabling hash checks, making the
service an asset owner, or relaxing authentication. A configuration switch is
not hot-reloaded by this component. Production rollback is not validated by the
isolated evidence described below.

## Evidence and remaining acceptance

The 2026-10-12 isolated migration already verified the real graph/map integrity,
safe missing-snapshot JSON 503 followed by recovery, and unavailable/retry UI;
the shared handoff records the existing unit, HTTP and browser artifacts.
This procedure adds only the missing byte/configuration/permission preflight and
synthetic tool tests:

```sh
python3 -m unittest discover -s tests -p 'test_knowledge_assets.py'
python3 scripts/verify-knowledge-assets.py --snapshot-root '<local-existing-snapshot-directory>'
```

The actual developer preview has developer-owned `0644` files and a `0755`
directory. Byte checks can pass there; the formal `--require-read-only` check
must reject its ownership. Do not change this running preview to claim release
acceptance. Permission-positive unit cases simulate access(2); actual staging
service-account permissions, mount/capability restrictions, effective JVM
configuration, exact map licensing, asset transport and coordinated rollback
remain release-stage checks. The Core equivalent-repackaging publisher cannot
carry these new business endpoints; this asset procedure does not remove that
separate business-upgrade requirement.

On 2026-10-12, the eight synthetic verifier tests passed on Python 3.12.3 (the
isolated repository tool environment) and Python 3.13.12. Read-only verification
of the existing external files and the four selected Knowledge environment
bindings read from the running isolated Java process passed; no other process
environment values were printed or recorded. Adding `--require-read-only`
correctly rejected this developer-owned directory. Local handoff evidence is
`evidence/KNOWLEDGE_ASSET_PREFLIGHT.json` and
`evidence/knowledge-asset-preflight-tests-python312.log`, outside Git. No assets, permissions,
configuration, services or preview builds were modified for these checks.
