# Knowledge runtime snapshots

The approved first release is **poetry-only, with geographic statistics retained**.
Its runtime asset set contains only `poetry-graph.json` and `poetry-stats.json`.
Painting geography remains PostgreSQL-backed aggregate data; no geographic map is
packaged, requested by the Web candidate, or served by this Core profile.
Runtime data and credentials stay outside Git. Only synthetic test fixtures are
committed. No model, remote download, new extraction, or database migration is used.

Release references: [two-file manifest](knowledge-assets-manifest.json),
[delivery procedure](knowledge-asset-delivery.md), and
[coordinated Core business upgrade](knowledge-core-upgrade-proposal.md).
Map redistribution is deferred to a future map-enabled release and is not a
condition of this candidate, provided that the map is neither delivered nor served.
The separately retained [map-enabled manifest](knowledge-map-enabled-assets-manifest.json)
and [permission record](knowledge-map-provenance.md) do not authorize that release.

## Selected configuration

Set `auralink.knowledge.map-enabled=false` (environment variable
`AURALINK_KNOWLEDGE_MAP_ENABLED=false`). It defaults to false, but the V2 release
contract binds an explicit false value to the approved candidate. Also set:

| Spring property | Environment variable | Requirement |
| --- | --- | --- |
| `auralink.knowledge.snapshot-root` | `AURALINK_KNOWLEDGE_SNAPSHOT_ROOT` | Absolute directory containing the two poetry files. |
| `auralink.knowledge.graph-sha256` | `AURALINK_KNOWLEDGE_GRAPH_SHA256` | Required exact graph SHA-256. |
| `auralink.knowledge.stats-sha256` | `AURALINK_KNOWLEDGE_STATS_SHA256` | Required exact statistics SHA-256. |
| `auralink.knowledge.snapshot-transport` | `AURALINK_KNOWLEDGE_SNAPSHOT_TRANSPORT` | `filesystem` (default) or explicit `sealed-memfd-v1`; no other value or automatic fallback. |

The selected no-map configuration excludes `china-sha256` and its environment
variable. No map configuration or file is needed for startup, painting statistics,
or poetry. Even if a legacy map and its hash remain on disk/in runtime settings,
`map-enabled=false` refuses the map **before reading any map file or hash**. The
delivery verifier is stricter: a poetry-only delivery directory must contain
exactly the two required files and its environment must not include a map hash.

Snapshot checks are lazy, per-request and retryable. Missing root, missing/invalid
hashes, inaccessible or malformed files do not prevent application startup, but
the affected endpoint returns the existing safe JSON 503. No default production
path or remote fallback exists. Guide/Critic configuration remains unchanged.

## Explicit sealed descriptor transport

`sealed-memfd-v1` is a Linux-only, poetry-only transport for an installation whose
reviewed coordinated launcher can create and attest sealed anonymous files without
mount privileges. It requires `map-enabled=false`; a map-enabled combination is
unavailable and never reads map data. This property must be bound to the reviewed
candidate configuration. It is not an automatic substitute when filesystem
permissions or mount validation fail.

The launcher verifies the complete durable two-file bundle and its existing exact
SHA-256 bindings, creates one memfd per file, applies kernel seals
`F_SEAL_WRITE | F_SEAL_GROW | F_SEAL_SHRINK | F_SEAL_SEAL`, verifies `F_GET_SEALS`,
and passes graph descriptor **198** and statistics descriptor **199** through the
actual Java exec. The coordinator must independently verify the runtime descriptor
identities/seals under its existing lifecycle and approval contract. Real assets,
configuration and private approvals remain outside Git.

Core reads only `/proc/self/fd/198` and `/proc/self/fd/199` in this mode. There is no
configurable descriptor, user path or disk fallback. `snapshot-root` must remain an
absolute, approved **durable-source identity**, but Core does not read live JSON
from it. The default filesystem mode retains its original directory/symlink checks.
The sealed mode checks anonymous regular-file shape (`unix:nlink == 0`), the same
8 MiB bound, the required SHA-256 identities and all original strict JSON/graph
checks on every request. Anonymous shape **does not prove seals**: Java does not
perform `F_GET_SEALS`, and this adapter by itself is not runtime protection. The
reviewed launcher/coordinator must establish that kernel guarantee; direct ad-hoc
Java startup with arbitrary inherited descriptors is not an approved deployment.

Missing descriptors, unsupported transport, hash mismatch or malformed snapshots
produce the existing safe `KNOWLEDGE_SNAPSHOT_UNAVAILABLE` 503 without private
paths. The two inherited descriptors remain open for that Java process lifetime;
each request opens/closes its own read handle. Restart/recovery must create and
verify a fresh sealed pair from the complete approved durable bundle before exec.
This transport changes neither authentication nor painting statistics, writes no
database or media, and does not authorize activating a different JAR/controller.

Linux tests execute an actual Python memfd/seal launcher followed by `exec` of
Java 17. They cover valid reads with a nonexistent live directory, missing fixed
descriptors despite valid disk data, wrong hash, corruption, ordinary linked
files, unknown transport and the forbidden map-enabled combination. The launcher
asserts a write is rejected by the kernel. These are isolated synthetic transport
tests, not production controller/rollback acceptance.

The local candidate JAR `a799f4af9d2b5702a732de1745d561b72c76288765fd89425e470e1eae5d07b8`
was also exercised through the actual coordinated launcher and Core publication
transaction in disposable mount/network/PID namespaces with PostgreSQL 16. The
real inherited poetry snapshot returned 711 nodes / 609 edges; authenticated
statistics matched the six eligible synthetic paintings and the map returned
503. The controller independently observed both kernel-sealed descriptors,
zero capability sets and `NoNewPrivs=1`. A separate real Python-to-Java exec
preserved PID/start ticks; kernel write, truncate, shared writable mmap,
chmod/chown and direct execution attempts were refused. Tampering with the
disposable durable source did not change the live snapshot, and prevented a
subsequent preflight until the complete approved pair was restored.

The transaction exercise covered successful activation, repeated no-op,
application restoration, lock/foreign-process refusal, and fresh-coordinator
recovery after abrupt exit following the first actual JAR write. All 44 fixture
database table digests and four fixture media files remained unchanged, including
nonempty synthetic users/tasks. Paired c5ea/Guide/Critic/Tunnel/watchdog controls
and approvals were fixtures; this is not production rollout or rollback
acceptance. Byte-different future CI/release JARs need their own artifact binding
and applicable checks; these results must not be attributed to them by name alone.

## API contract

All three Knowledge GET routes retain existing session authentication. Anonymous
requests receive the normal JSON 401. Unknown query parameters receive existing
JSON 400. No route lets clients select paths or runtime configuration.

- `/api/v1/knowledge/painting-statistics` retains the existing Gallery population,
  public projection, field coverage and metric contract, including numeric geography.
- `/api/v1/knowledge/poetry-graph` returns `schemaVersion`,
  `source=legacy-poetry-snapshot`, byte-level `graphSha256`/`statsSha256`,
  `nodeCount`, `edgeCount`, `nodes`, `links`, `entityTypes`, `relationTypes`,
  and `sourceLimits`. Nodes contain id/name/category/size/description; links
  source/target/value/label/summary/detail. Summary items contain name/value.
  Original uncertain labels and relationships are preserved; no edges are generated.
- `/api/v1/knowledge/china-map` in this release returns HTTP 503,
  `code=KNOWLEDGE_SNAPSHOT_UNAVAILABLE`, message
  `此版本未启用地理底图；地域统计仍可读取。`. It never returns map features or hash.
  Invalid parameters still return 400 after authentication.

Successful snapshots use `Cache-Control: no-store`. Unavailable errors contain no
filesystem paths. Required SHA-256 bindings must be 64 hexadecimal characters and
match the actual bytes; blank bindings do not bypass integrity validation.
Files must resolve directly beneath the configured root; escaped symlinks are
rejected. Bounds: 8 MiB per file, 2,000 nodes and 10,000 edges. Duplicate JSON
keys/trailing documents, duplicate IDs, dangling endpoints, nonfinite numbers and
inconsistent summary counts are rejected. Render all labels/descriptions as text.

## Existing poetry artifacts validated locally

Source: inherited `bailangguihun/auralink-web` revision
`aa1da07086696835de7c361886115e602a88c471`, `public/data/`, previously hash-matched
against the ignored legacy AutoDL frontend. The new repositories do not contain
these files.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| poetry-graph.json | 528422 | `899511b6b7d02e6ba515986db651c67b3702497d2031bdbb75029c34491da2c0` |
| poetry-stats.json | 20130 | `27e91284d28c05c9b7dde9fcb4181c713761d3c2eebf84816b45c9f7d9af7501` |

The graph contains 711 unique nodes and 609 edges, without dangling endpoints.
Summary counts match categories and first-comma-separated relation labels from
legacy `scripts/convert-graphml.js`. Original GraphML is missing; upstream
extraction accuracy is not independently verified. Categories containing “诗词”
or “诗人” do not establish verified distinct works or people. This is a file
snapshot, not a PostgreSQL graph, and painting ingestion does not extend it.

Application access must be read-only, enforced by separate ownership, a verified
read-only mount, or the separately reviewed sealed-descriptor launch contract.
Hashes or anonymous-file shape alone do not prove runtime protection. The original isolated
preview uses developer-owned files and is not production ownership acceptance.
Update assets using a complete new verified directory and bound configuration;
never edit an active graph/stats pair in place. Application rollback needs no
database restore and must retain all huaniao data, user data, tasks and media.

## Retained future map-enabled mode

Only a separately reviewed `map-enabled=true` configuration can use `china.json`.
It requires `auralink.knowledge.china-sha256` / `AURALINK_KNOWLEDGE_CHINA_SHA256`
and the three-file `map-enabled` manifest. Map integrity and permissions remain
mandatory. Missing, mismatched or malformed maps return safe JSON 503 independently
of poetry/statistics. GeoJSON FeatureCollection output retains only feature name
and Polygon/MultiPolygon coordinates, with a maximum of 100 features; rings need
at least four positions and numerically equal first/last coordinates.

The inherited map is 582521 bytes, SHA-256
`206cf7fd5acba4bb08d9586899acc144cea2b29d9e93ff70f93895c5e5ffde77`,
35 features including one unnamed boundary. Its redistribution permission remains
unresolved. Keeping this validator and metadata is not permission to deliver or
serve the map. Existing private map files remain untouched.
