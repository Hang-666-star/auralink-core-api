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

Application access must be read/traverse only, enforced by separate ownership or
a read-only mount. Hashes alone do not prove permissions. The original isolated
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
