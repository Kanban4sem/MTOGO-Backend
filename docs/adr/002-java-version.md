# ADR-002: Java Version

## Status

Accepted

## Context

The backend requires a shared Java version. We considered Java 25, 21 and 17. The team has the most experience with Java 17, and Java 21 supports all required dependencies.

## Decision

We use Java 25 because it is the newest version among the options considered. We have identified no dependency compatibility issues or obstacles to developing with it.

## Alternatives

- **Java 21:** Supports all required dependencies, but was not chosen because we prefer Java 25.
- **Java 17:** The version the team knows best, but offers less opportunity to work with a newer Java version.

## Consequences

- Development environments and CI must use Java 25.
- New dependencies must be compatible with Java 25.
- The team may need to learn changes introduced since Java 17.
