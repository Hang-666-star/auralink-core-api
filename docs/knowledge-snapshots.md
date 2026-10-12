# Knowledge runtime snapshots

The Knowledge module reads an existing poetry export and map from a separately
provisioned, server-local directory. These are runtime data, not source code.
Do not commit production CSV, GraphML, graph/map snapshots, database files or
credentials. Only synthetic test fixtures belong in this repository.

Release preparation: [exact asset manifest](knowledge-assets-manifest.json),
[delivery and read-only verification](knowledge-asset-delivery.md),
[map source and permission evidence](knowledge-map-provenance.md), and
[Core business-upgrade proposal](knowledge-core-upgrade-proposal.md).
These documents do not authorize production publication or grant dataset rights.

Configure `AURALINK_KNOWLEDGE_SNAPSHOT_ROOT` (or Spring property
`auralink.knowledge.snapshot-root`) to an absolute directory containing:

- `poetry-graph.json`
- `poetry-stats.json`
- `china.json`

Optional SHA-256 bindings are `AURALINK_KNOWLEDGE_GRAPH_SHA256`,
`AURALINK_KNOWLEDGE_STATS_SHA256`, `AURALINK_KNOWLEDGE_CHINA_SHA256`
(Spring properties `auralink.knowledge.graph-sha256`, `stats-sha256`, `china-sha256`).
Use the bindings in a reviewed release configuration. No default production path
or remote download is provided. The loader has no relationship to Guide provider
invocation and does not change Guide/Critic configuration.

## API contract

All three Knowledge GET routes require the existing authenticated session.
`GET /api/v1/knowledge/poetry-graph` accepts no query parameters. It returns
`schemaVersion`, `source=legacy-poetry-snapshot`, byte-level `graphSha256` and
`statsSha256`, `nodeCount`, `edgeCount`, `nodes`, `links`, `entityTypes`,
`relationTypes`, and `sourceLimits`. Nodes contain id/name/category/size/description;
links contain source/target/value/label/summary/detail. Summary items contain
name/value. Relationships and uncertain labels are preserved exactly; no new
edges, translations or knowledge corrections are generated.

`GET /api/v1/knowledge/china-map` accepts no query parameters. It returns a
GeoJSON FeatureCollection plus the original byte-level SHA-256. Each feature
contains its name and Polygon/MultiPolygon coordinates; unused metadata is not
exposed. The inherited file has one unnamed boundary feature, retained as such.
Failure of this endpoint does not disable painting statistics or poetry.

Responses use `Cache-Control: no-store`. Unknown query parameters produce the
existing JSON 400 error contract. Missing/unconfigured/malformed/hash-mismatched
artifacts return JSON 503 `KNOWLEDGE_SNAPSHOT_UNAVAILABLE` without filesystem
paths. Missing snapshots do not prevent application startup or other modules.
Reads are lazy and retryable: a subsequent request revalidates the files. Files
must resolve directly beneath the configured root; escaped symlinks are rejected.
Each file is bounded to 8 MiB, graph nodes to 2,000, edges to 10,000, map features
to 100. Duplicate JSON keys/trailing documents, duplicate node IDs, dangling
endpoints, nonfinite numbers and inconsistent summary counts are rejected.
Polygon and MultiPolygon linear rings must contain at least four positions and
close with coordinates numerically equal to their first position; short or open
rings return the same safe JSON 503 as other malformed snapshots.
Clients must render every label/description as text, never trusted HTML.

## Existing artifacts validated locally on 2026-10-12

The source is the inherited `auralink-web` repository under GitHub account
`bailangguihun`, revision `aa1da07086696835de7c361886115e602a88c471`,
`public/data/`. Its Database files were also previously hash-matched against
the ignored legacy AutoDL frontend. The new source repositories do not contain
these data files.

| File | Bytes | SHA-256 |
|---|---:|---|
| poetry-graph.json | 528422 | `899511b6b7d02e6ba515986db651c67b3702497d2031bdbb75029c34491da2c0` |
| poetry-stats.json | 20130 | `27e91284d28c05c9b7dde9fcb4181c713761d3c2eebf84816b45c9f7d9af7501` |
| china.json | 582521 | `206cf7fd5acba4bb08d9586899acc144cea2b29d9e93ff70f93895c5e5ffde77` |

Graph: 711 unique nodes, 609 edges, no dangling endpoints. Summaries must match
actual category counts and first-comma-separated relation labels, as produced by
the existing `scripts/convert-graphml.js`. Its purported source GraphML is missing.
Counts for categories containing “诗词”/“诗人” are not verified distinct works or
people. The snapshot is not a PostgreSQL graph, has not had its upstream extraction
accuracy independently verified, and does not automatically include new paintings.

Map: 35 inherited GeoJSON features. This establishes the inherited artifact's
identity, not its upstream authorship/license or geographic accuracy. Reuse is
limited to the project's existing map purpose; confirm redistribution permissions
when preparing a release. Provision all runtime files outside Git and the frozen
application release, through the separately reviewed deployment configuration.

Release delivery must give the application account read/traverse access without
write access to the files or their containing directory, using a separate asset
owner or a read-only mount. Hash bindings verify identity but do not enforce OS
permissions. The isolated developer preview uses owner-writable 0644 files for
retry/fault tests; it is not evidence of production read-only ownership. Before
release, verify all three bindings and permissions as the service account, and
verify missing-file 503/retry behavior in the approved staging environment.

For updates, validate a new complete directory and bindings before switching the
runtime configuration; do not edit a live graph/stats pair in place. Rolling back
application code requires no data migration or removal of these artifacts and
must retain all existing huaniao data/media.
