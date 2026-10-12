# Knowledge map provenance and release permission

> Deferred: the approved first release is `poetry-only` and does not package or
> serve this map. The permission questions below apply to a future map-enabled
> release; they no longer block the explicitly disabled-map candidate.

Checked on 2026-10-12. This report records an exact dataset match and the remaining
permission requirement; it does not grant a redistribution licence. No map bytes
were added to this repository, and the isolated preview was left unchanged.

## Exact inherited artifact

| Item | Verified value |
| --- | --- |
| Runtime filename | `china.json` |
| Bytes | 582521 |
| SHA-256 | `206cf7fd5acba4bb08d9586899acc144cea2b29d9e93ff70f93895c5e5ffde77` |
| Legacy repository | `bailangguihun/auralink-web` |
| Legacy revision | `aa1da07086696835de7c361886115e602a88c471` |
| Legacy path | `public/data/china.json` |
| Git blob object | `4f1250cef1c0043ac6907aa988c87ff5b607cd66` |
| Content | GeoJSON FeatureCollection, 35 features including one unnamed boundary |

The existing external runtime file was byte-for-byte equal to the legacy Git
blob. The local legacy repository is not shallow; its available history contains
one change for this path, the initial import on 2026-08-04. The imported tree has
no LICENSE/LICENCE/NOTICE/COPYING file. Its painting page fetches the local file,
but neither that call nor the checked source documentation records where the
original contributor obtained permission. Initial import is evidence of custody,
not evidence of a data licence.

## Upstream content match

An anonymous read of the official
[DataV areas_v3 China export](https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json)
returned the following on 2026-10-12 at approximately 05:06 UTC:

| Item | Observed value |
| --- | --- |
| HTTP status | 200 |
| Response bytes | 582522 |
| Response SHA-256 | `99adfeded5223848bbe37a0a12f8023e11ee12161c7800521c27db42fdeac275` |
| Last-Modified | `Tue, 29 Jun 2021 11:16:54 GMT` |
| ETag | `"CB2BA75AD5F4A65BB00B748889479F93"` |
| Exact byte comparison | upstream response equals inherited bytes plus one final LF (`0a`) |
| Parsed JSON comparison | identical, including every property and coordinate |

The exact comparison establishes that the inherited dataset is the DataV
`areas_v3` export with its terminal newline removed. It does not establish whether
the original contributor downloaded it directly or via another distributor. The
endpoint is mutable: the response hash above is an observation, not a promise
about a future download. Keep the inherited artifact's existing release hash;
do not replace it with freshly downloaded bytes as part of this release.

The official [GeoAtlas page](https://datav.aliyun.com/portal/school/atlas/atlas_aigc)
identifies the `areas_v3` data as originating from Amap and updated in May 2021.
The current official
[DataV GeoAtlas documentation](https://help.aliyun.com/zh/datav/datav-7-0/user-guide/datav-geoatlas-widgets/)
(page update 2025-06-27) likewise attributes the dataset to the Amap Open Platform,
limits it to learning and exchange, and directs copyright questions to Amap.
The download function and public URL therefore do not establish permission for
ArtLIVE's production redistribution.

## Licence evidence and the specific unresolved item

No applicable open-data licence or project-specific written permission was found
in the inherited tree or the checked official documentation. ECharts' software
licence is unrelated to the rights in these map coordinates and boundaries.

The current [Amap Open Platform service terms](https://lbs.amap.com/pages/terms/)
(page update 2025-12-03), sections 2.2, 4.12 and 7.2–7.3, cover geographic and
administrative-boundary data and restrict copying, independent display and
redistribution outside the granted scope. These are current published terms;
they do not prove which historical or separately signed terms, if any, applied
to the original 2021 export or its 2026 import. They provide no positive evidence
of an ArtLIVE-specific permission. This report does not decide that legal issue.

Before production delivery, the release owner must attach an applicable licence
or written confirmation from the relevant rights holder covering this exact
DataV/Amap dataset and the intended use: server-side storage of the snapshot,
authenticated delivery of its projected GeoJSON through
`GET /api/v1/knowledge/china-map`, and display in ArtLIVE's painter-geography
chart. The evidence must identify permitted audience/use, hosting and
redistribution scope, required attribution or notices, and any required map
review/version conditions. Record the evidence URL or approval reference and
its effective date alongside the artifact hash. Any private agreement should
remain in the approved private release record rather than be committed here.

Until that evidence exists, map redistribution permission is **unresolved**.
This is a production-release requirement, not a reason to remove or silently
substitute the implemented map, stop the existing isolated review, or change the
loader. No contact with the rights holder was made in this task. See
[snapshot delivery requirements](knowledge-snapshots.md) for the separate
integrity, permissions and missing-file handling requirements.

## Reproducing the identity check without saving upstream data

This is an optional evidence check, not a runtime installation command. It makes
one read-only request to the public DataV asset and keeps the response in memory.
Set `KNOWLEDGE_MAP_FILE` to the existing external artifact; do not put it in Git.

```bash
python3 - <<'PY'
import hashlib
import json
import os
from pathlib import Path
from urllib.request import urlopen

local = Path(os.environ['KNOWLEDGE_MAP_FILE']).read_bytes()
assert len(local) == 582521
assert hashlib.sha256(local).hexdigest() == (
    '206cf7fd5acba4bb08d9586899acc144cea2b29d9e93ff70f93895c5e5ffde77'
)
url = 'https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json'
with urlopen(url, timeout=20) as response:
    upstream = response.read(8 * 1024 * 1024 + 1)
assert len(upstream) <= 8 * 1024 * 1024
print('upstream_sha256', hashlib.sha256(upstream).hexdigest())
print('upstream_is_local_plus_LF', upstream == local + b'\n')
print('same_parsed_JSON', json.loads(upstream) == json.loads(local))
PY
```

For the legacy custody check, compare the existing external bytes in memory with
`git show aa1da07086696835de7c361886115e602a88c471:public/data/china.json`
from the inherited repository. Do not export that blob into this source tree.
