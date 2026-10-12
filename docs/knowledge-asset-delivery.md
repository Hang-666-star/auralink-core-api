# Knowledge external asset delivery: approved no-map scope

This is a reviewed procedure for future coordinated release, **not an executed
production installation**. The user approved first release without the map;
geographic statistics and all other Knowledge features remain. Map permission
is deferred and does not block this profile, which must neither deliver nor serve
map bytes. Do not change the existing preview or private legacy map files.

## Exact candidate contract

The [schemaVersion 2 manifest](knowledge-assets-manifest.json) binds
`releaseProfile=poetry-only`, `AURALINK_KNOWLEDGE_MAP_ENABLED=false`, and exactly
`poetry-graph.json` plus `poetry-stats.json`. It records exact sizes, SHA-256 values
and upstream revision. Actual files, archives, credentials, environments and
production data stay outside Git. Obtain only these two files via the approved
artifact channel; no startup download or CDN fallback is used.

The real snapshot has 711 unique nodes and 609 relationships. Provenance and
limitations are in [knowledge-snapshots.md](knowledge-snapshots.md). A future
`map-enabled` release uses its separately reviewed three-file manifest, explicit
true setting, map hash and permission evidence; it cannot be selected by relabeling
a two-file directory or by weakening validation.

`scripts/verify-knowledge-assets.py` is read-only: it validates profile, exact file
set, sizes and hashes, optionally environment bindings and actual service-account
read-only access. It makes no writes, downloads, API requests or provider calls.
It rejects symlinks in asset paths. Java still performs JSON and graph/statistics
consistency validation; byte preflight does not replace authenticated acceptance.

## Future installation, NOT EXECUTED

1. Use the approved asset parent outside Git, frozen packages and writable
   application trees. Create a **new** version directory; do not reuse an active
   directory or mix graph/statistics versions. Copy only the two manifest files.
2. Derive actual service identity from the installation contract. For a non-root
   service, use a separate asset owner and a read-only service group: typically
   files `0440`, directory `0550`. All ancestors must allow traversal and resist
   service writes/renames. Verify actual groups/ACLs, mounts and capabilities.
   For the observed root-running R25 Java service, use the V2 reviewed dedicated
   read-only mount and capability restrictions; do not claim root ownership/mode
   bits alone provide read-only protection or invent a replacement service UID.
3. Validate the staging directory, atomically rename it on the same filesystem,
   and verify it again as the effective application identity. Keep the previous
   accepted directory for rollback. The non-root CLI permission check deliberately
   rejects root; V2 has the separate root-service mount/capability contract.
4. Bind the exact directory, both SHA values and explicit false setting through
   the reviewed V2 configuration transaction. Preserve all unrelated configuration,
   especially authentication, public projection, media, PG and Critic policy.

Example configuration (placeholders are not production facts):

```sh
export AURALINK_KNOWLEDGE_MAP_ENABLED=false
export AURALINK_KNOWLEDGE_SNAPSHOT_ROOT='<approved-new-absolute-directory>'
export AURALINK_KNOWLEDGE_GRAPH_SHA256=899511b6b7d02e6ba515986db651c67b3702497d2031bdbb75029c34491da2c0
export AURALINK_KNOWLEDGE_STATS_SHA256=27e91284d28c05c9b7dde9fcb4181c713761d3c2eebf84816b45c9f7d9af7501
unset AURALINK_KNOWLEDGE_CHINA_SHA256
python3 scripts/verify-knowledge-assets.py \
  --snapshot-root "$AURALINK_KNOWLEDGE_SNAPSHOT_ROOT" --check-environment
# Additionally use --require-read-only only as the actual non-root service UID,
# with its real groups; root-running R25 uses the V2 mount/capability checks.
```

`--check-environment` verifies the invoking process only. The publisher must bind
and inspect actual JVM configuration precedence; a successful shell check is not
proof of effective Java settings. Poetry-only verification rejects an extra map
file, any map hash binding, a missing/true map setting, and missing/bad poetry
hashes. Verification output contains no raw data, filesystem paths or credentials.
Without permission checks, output explicitly says permissions were not checked.
No successful verifier result is production acceptance.

## Isolated acceptance and recovery

Use existing isolated authentication; do not create production accounts or print
tokens. Use disposable copies, never the old preview or production asset tree.

| Scenario | Expected result |
| --- | --- |
| Anonymous graph, statistics or map GET | Existing JSON 401. |
| Valid two-file pair, authenticated graph GET | 200, no-store, exact hashes, 711 nodes/609 edges, matching summaries. |
| No map file/configuration, authenticated map GET | 503 `KNOWLEDGE_SNAPSHOT_UNAVAILABLE`, explicit disabled message, no map bytes. |
| Legacy map remains and hash supplied, but map-enabled=false | Same disabled 503 before reading map; delivery preflight still rejects including map in this release bundle. |
| Required poetry file missing, unreadable, corrupt, wrong hash or mismatched summaries | Safe JSON 503; statistics remain independent. |
| Restore exact approved missing/corrupt file in disposable fault-test copy | Next request succeeds without a restart; no hash check is disabled. |
| Query parameters | Existing authenticated JSON 400; client cannot select files. |

Recovery in a real deployment uses a complete newly verified asset directory and
approved configuration switch through R25. Never recover by blanking hashes,
relabeling profiles, making the service an asset owner, or relaxing authentication.
Application rollback uses only the verified compatible baseline and configuration,
retaining database contents, huaniao records, tasks and media. No database restore
or arbitrary historical-JAR downgrade is part of application recovery.

The required production order remains reviewed V1 controller installation → V2
upgrade → Core → c5ea Web. c5ea is the sole Next lifecycle owner. Feature pushes
and main PR CI do not authorize activation. The equivalent-repackaging path cannot
carry these new business endpoints.

## Evidence scope

Run the focused standard-library tests with:

```sh
python3 -B -m unittest discover -s tests -p 'test_knowledge_assets.py'
```

The tests cover both profiles, required hashes, missing/extra files, tampering,
symlinks, configuration mismatch, and simulated permission refusal. Mocked
access(2) tests are not production permission acceptance. Prior real isolated
installation/mount/readability/recovery evidence remains documented in the
shared handoff; new no-map HTTP/browser and V2 evidence is recorded separately.
The original map-enabled preview and prior 10,000-record statistics evidence are
not relabeled as new no-map artifact verification.
