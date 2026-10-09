# R25 coordinated backend artifact contract

This source revision starts from `d17bee0f84ac1af24faa08fe99acca263cdf0b97`
(the `release` merge of the current `main` source; no source-tree difference).
It retains the current PostgreSQL runtime dependency, existing API code,
upload contract, authentication adapters, Guide/Critic behavior, and CI
promotion rules. It does not enable an import, submit a task, or deploy an
artifact merely because a build succeeds.

## Restored accepted boundaries

1. `SecurityConfig` permits only internal `DispatcherType.ERROR` rendering
   without reclassifying a business resource error as an authentication
   error. Normal request dispatches retain the original security rules.
2. `ApiV1ExceptionHandler` explicitly emits `application/json`, including
   missing-media errors when a caller requests image/binary content.
3. `PaintingQueryService` hides top-level compatibility `sourceSequence`
   and `imageStorageName` when the source mapping explicitly marks the
   corresponding source identifier/image-reference fields non-public.
   Existing public legacy mappings and legitimate public annotation values
   retain their original behavior. Private source/cell evidence is not
   deleted from PostgreSQL.

The first two boundaries are sourced from the accepted backend
`2e273a737b68aeac4095cc490c5933a81b4b02fca96409252368b0a7c51a611b`.
The projection correction is sourced from its minimal derivative
`3a37ad49be1b9c45461302c1dcc51f55399e5fc2536e7959e35e5decda2a0417`.
All eleven `application-catalog-postgres*.yml` resources are restored
byte-for-byte from that derivative. Passwords in these resources are
environment placeholders; integration-only literals are synthetic test
fixtures, not production credentials. Production private configuration
remains outside this repository.

`scripts/verify-r25-artifact.py` binds the eleven packaged resource hashes,
the three affected class contracts, and the PostgreSQL driver. The focused
JUnit tests protect public/hidden compatibility fields, JSON business error
responses, and packaging of the accepted error-dispatch rule. These gates
are not a substitute for real packaged-JAR PostgreSQL/HTTP acceptance.

## Production publication boundary

CI builds and tests this source, checks the actual packaged JAR, and retains
the JAR and its verification report as a commit-bound GitHub artifact.
Production publication remains separately paused until the R25 delegated
writer, shared lease, ownership/process checks, complete CURRENT recovery,
and retained-huaniao downgrade barrier are validated. Do not revive a
direct JAR/PID replacement fallback.

The automatic publication gate must not be bypassed by downloading a CI
artifact. An approved artifact can be transferred only into a private
staging area; the validated R25 coordinator is the sole production writer.
Frontend publication remains owned by the existing c5ea supervisor.
