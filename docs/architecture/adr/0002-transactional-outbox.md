# ADR 0002: Transactional Outbox for Analysis Task Delivery

## Status

Accepted, 2026-07-28.

## Context

Synchronous RabbitMQ publication inside task creation created a database/message dual-write window: a task could commit while its message was not durably delivered, or publication could succeed independently of the database transaction.

## Decision

Write an `analysis_outbox` event in the same task-creation transaction as the task, then publish it asynchronously after a conditional claim. The publisher waits for the existing Broker confirm before marking an event `PUBLISHED`.

Task creation now means "task and delivery intent persisted," not "message already present in RabbitMQ."

## Delivery Semantics

Delivery is at least once. A process crash after Broker confirm but before the database row is marked `PUBLISHED` can cause a later re-publication. The task consumer uses its atomic task claim, so a duplicate message whose task is no longer claimable is returned as `IGNORED`.

Events move `PENDING -> PROCESSING -> PUBLISHED`. A conditional update grants a claim only to a due `PENDING` event. Publication or decoding failure reschedules the claimed event as `PENDING` with an incremented attempt count; stale `PROCESSING` claims are returned to `PENDING` for recovery.

## Failure Recovery

The publisher recovers `PROCESSING` rows older than the configured claim timeout, selects due pending rows in batches, and safely retries failed publication after the configured delay. The publish error stored in the row is a safe, generic message; operators must not expose event payload JSON in diagnostics.

## Operations

Monitor pending count, oldest pending age, processing age, attempt count, and publisher failures. Use metadata-only inspection queries; do not select or print `payload_json` because it may contain operationally sensitive event data.

The current implementation has no automatic cleanup of `PUBLISHED` rows. Retention and cleanup are a production-readiness follow-up, not a completed capability.

## Consequences

The dual-write window is removed from task creation, at the cost of asynchronous delivery, persistent rows, background polling, and an at-least-once duplicate path. Unit and contract coverage exists for the Outbox, task creation, and execution boundary, but real-MySQL/Testcontainers contention coverage is still missing.
