# ADR 0001: Analysis Module Boundary

## Status

Accepted, 2026-07-28.

## Context

The backend's layer-oriented packages created broad coupling to Mappers and infrastructure. Analysis-task behavior was distributed across legacy services, MQ code, persistence code, AI integration, and storage handling, making dependencies difficult to constrain and domain rules difficult to test without framework types.

## Decision

Migrate one analysis vertical slice to `analysis/application/domain/infrastructure`. The legacy `AnalysisTaskExecutionServiceImpl` remains a transactional compatibility facade for the existing MQ entry point; it adapts the legacy message to the analysis execution use case rather than implementing the execution workflow itself.

## Consequences

Temporary legacy facades and adapters remain while callers and adjacent workflows are migrated incrementally. This is a modular monolith: the Java application remains one deployable process and one database, rather than becoming microservices. The slice provides a boundary and a migration pattern, not a wholesale package rewrite.

## Enforcement

`AnalysisModuleArchitectureTest` enforces a framework-free analysis domain, application-layer independence from analysis infrastructure, and keeps legacy controllers from using analysis infrastructure directly. The application layer coordinates framework-agnostic ports; MyBatis, AI, storage, reporting, and MQ implementations remain infrastructure adapters.
