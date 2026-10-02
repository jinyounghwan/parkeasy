# Public parking realtime full sync

## Scope

`REALTIME_FULL` is a nationwide bootstrap and repair job for the provider's
`PrkRealtimeInfo` feed. It is separate from the small, recurring realtime sync
used by `ParkingSyncScheduler`. The scheduler is deliberately not connected to
the full-sync worker.

The full sync keeps one current `parking_realtime` row per parking lot. It does
not delete a row merely because the provider omits that lot from a later run.
Omitted rows age naturally through the existing 30-minute stale policy. A future
`parking_realtime_history` table may capture append-only observations, but no
history table or retention policy is part of this implementation.

## Processing and recovery

The worker reuses `parking_sync_job`, the single-thread background-worker
pattern, page checkpoints, failure classification, client retry/backoff,
automatic resume (five attempts by default), manual resume with a reset retry
budget, pause, stale `RUNNING` recovery, skipped-page records, empty-page
observations, and trailing-empty completion used by the facility and operation
full syncs.

HTTP work happens outside a database transaction. A successfully received page
is committed in one transaction with its realtime writes, counters, and next-page
checkpoint. A failure therefore retains all earlier page commits and retries the
failed page.

The page size is configured by
`parkeasy.public-data.full-sync.realtime.num-of-rows` (default 1000, allowed
1..1000). A non-final short page is retried without committing partial data. A
successful empty response is observed three times before it is recorded as
`EMPTY_SUCCESS_PAGE`; ten consecutive confirmed empty page numbers complete the
job as `COMPLETED_WITH_GAPS` with
`PROVIDER_TRAILING_EMPTY_PAGES`. Timeouts do not count as empty observations.

## Mapping, validation, and writes

The existing realtime parser maps `prk_center_id`,
`pkfc_ParkingLots_total`, and `pkfc_Available_ParkingLots_total`. Malformed or
blank numeric fields become null. Rows are skipped when both values are null,
either value is negative, available exceeds total, or the external ID is absent.
Zero available spaces is valid. `observedAt` remains null because the provider
does not supply an observation time.

Within a page, external IDs are deduplicated. The last valid value wins; an
invalid later duplicate cannot erase an earlier valid value. Each occurrence
after the first increments `duplicateCount`, including invalid duplicates.

Each page uses one `receivedAt` captured when processing starts. The service
batch-loads `PUBLIC_DATA_PORTAL` parking lots by external ID, then batch-loads
existing realtime rows by parking-lot ID, and finally calls `saveAll`. An
unmatched item increments `orphanCount` and is never stored independently.
Existing rows receive new total, available, received, and updated timestamps.
The unique key on `parking_realtime.parking_lot_id` supports both the one-row
invariant and nearby/detail batch lookup; no additional index is needed.

Counters describe provider rows, not distinct database rows:

- `processedCount`: raw rows on committed data pages.
- `invalidRealtimeCount`: rows rejected after numeric parsing.
- `missingExternalIdCount`: rows without an external ID.
- `skippedCount`: missing-ID plus invalid rows; orphans are reported separately.
- `zeroAvailableCount`: valid rows whose available value is zero.
- `fullParkingCount`: valid rows with non-null total and zero available.
- `missingTotalSpacesCount` / `missingAvailableSpacesCount`: rows where that
  field is blank or malformed, including rows later rejected for another reason.
- `relationshipInvalidCount`: rows with a negative value or available greater
  than total; this is a subset of `invalidRealtimeCount`.
- `matchedCount` / `exactMatchRate`: exact `(PUBLIC_DATA_PORTAL, externalId)`
  matches and their share of valid match candidates.
- `zeroAvailableMatchedCount` / `zeroAvailableOrphanCount`: valid zero-available
  rows split by exact facility match. These counters do not assert that zero
  means a confirmed full parking lot.

Each committed data page logs only `firstExternalId`, `lastExternalId`, matched,
orphan, and aggregate counters. Raw response bodies and complete ID lists are
not persisted.

## Freshness and refresh strategy

`receivedAt` is the real page receipt time and is never rewritten to the job end
time. Consequently, if a nationwide scan takes longer than the 30-minute stale
threshold, early pages can become stale before the scan finishes. Rewriting their
timestamps would misrepresent freshness, so the three-page trial must measure
response time, match rate, provider realtime coverage, estimated full duration,
and its conflict with the stale threshold before an unlimited run is approved.

For recurring production refresh, the alternatives are:

- A nationwide sequential refresh is simple but provides uneven freshness and
  may take hours for the currently reported 788 pages.
- Demand-prioritized regions/lots improve useful freshness but require demand
  ranking and provider filtering support.
- Refresh-on-stale nearby requests improves locality but adds request-path
  latency, rate-limit pressure, and stampede control requirements.
- Maintaining a provider-realtime-capable target set avoids known non-realtime
  lots but requires a reliable discovery/update process.
- A low-frequency nationwide repair plus short-frequency hot-region refresh
  combines coverage and useful freshness.

The documented realtime endpoint and the current client contract expose only
`serviceKey`, `numOfRows`, `pageNo`, and `format`. The response also has no
region field. No authoritative contract for external-ID lookup or provider-side
region filtering has been found. Selecting targets from `parking_realtime` would
therefore not avoid a nationwide provider scan, so a `REALTIME_REFRESH` job and
a separate target table are intentionally not implemented yet.

The recommended long-term direction remains low-frequency nationwide repair plus
short-frequency hot-region refresh, but only after the provider offers a target
or region query (or another suitable feed). The legacy bounded page scheduler is
disabled by default with `parkeasy.sync.realtime.enabled=false`; enabling a
five-to-ten-minute page-1 scan is not a valid targeted refresh. Do not schedule
an unlimited `REALTIME_FULL` or assume a ten-minute nationwide cycle is feasible.

Zero remains a valid stored numeric value. Provider documentation has not yet
confirmed whether zero always means physical occupancy rather than unavailable
collection, so clients should describe it as “0 spaces available” and avoid a
stronger “confirmed full” claim. The frontend is unchanged in this phase.

## Development endpoints

These endpoints are development-only and require authentication before
production exposure:

```text
POST /api/admin/parking-sync/realtime/full/start?maxPages=3
POST /api/admin/parking-sync/realtime/full/resume?jobId={jobId}&maxPages=10
POST /api/admin/parking-sync/realtime/full/resume?jobId={jobId}&full=true&confirm=REALTIME_FULL_SYNC
POST /api/admin/parking-sync/realtime/full/pause
GET  /api/admin/parking-sync/realtime/full/status
GET  /api/admin/parking-sync/realtime/full/status?jobId={jobId}
```

After restarting the backend so Flyway applies `V12` and `V13`, run only the three-page
start first. Decide whether to run an unlimited sync from those measurements;
the application must not start it automatically.
