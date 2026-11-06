# ADR 0001: Analysis Module Boundary

## Status

Accepted, 2026-07-28.

## Context

The backend's layer-oriented packages created broad coupling to Mappers and infrastructure. Analysis-task behavior was distributed across legacy services, MQ code, persistence code, AI integration, and storage handling, making dependencies difficult to constrain and domain rules difficult to test without framework types.

## Decision

Keep the Analysis behavioral boundary, but normalize its physical packages to
the repository-wide `<technical root>/<functional owner>` convention. Analysis
use cases, ports, adapters, and artifact storage live under `service/analysis`;
commands, domain models, events, and persistence records live under
`pojo/analysis`; MyBatis, messaging, AI, and configuration code live under
`mapper/analysis`, `mq/analysis`, `ai/analysis`, and `config/analysis`.

`AnalysisTaskExecutionServiceImpl` remains a transactional compatibility facade
for the existing MQ entry point. It adapts the message to the Analysis execution
use case rather than implementing the execution workflow itself.

## Consequences

This is still a modular monolith: the Java application remains one deployable
process and one database rather than becoming microservices. The normalized
layout improves directory lookup and aligns Analysis with the rest of the
backend. Behavioral boundaries are expressed through ports, adapters, and
architecture tests instead of relying on a separate top-level package tree.

Compatibility facades remain where existing callers need their transactional or
messaging contracts. Moving packages does not change endpoints, SQL, queue
names, state transitions, or public method signatures.

## Enforcement

`AnalysisModuleArchitectureTest` enforces framework-free Analysis domain models,
prevents Analysis services from acquiring new concrete MQ, AI, or storage
dependencies outside explicit compatibility baselines, and keeps unrelated
controllers from using Analysis technical adapters directly. The use-case layer
coordinates framework-agnostic ports; MyBatis, AI, storage, reporting, and MQ
implementations remain adapters.

`LayerFunctionalPackageArchitectureTest` independently requires production
technical roots and migrated tests to use approved functional subpackages. This
prevents the former flat `service`, `mapper`, `pojo`, Agent, LLM, RAG, and
configuration layouts from returning.
