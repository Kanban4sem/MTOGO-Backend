# ADR-004: Backend Structure

## Status

Accepted — [#13](https://github.com/Kanban4sem/MTOGO-Backend/issues/13)

## Context

The backend needs a consistent package structure as features are added. We want to keep the root package small, separate responsibilities and avoid writing CRUD code that Spring Data already provides.

## Decision

Group features under `mtogo.feature.<feature>`. Each feature has `presentation` for controllers and separate `dto/request` and `dto/response` records, `domain` for JPA entities and services, and `data` for repositories and entity/DTO mappers. Shared infrastructure stays outside `feature`.

Use Spring Data JPA repositories for CRUD and custom queries, with no additional DAO layer. Services own business rules and transactions; entities do not access the database. Use concrete service classes by default and introduce interfaces only for a concrete need. Flyway owns schema changes.

The detailed structure and responsibilities live in the [coding standards](../coding-standards.md#backend-structure).

```text
MTOGO-Backend/
    │
    ├── .env                                                            local secrets, never committed
    │
    └── src/main/
        │
        ├── resources/
        │   │
        │   ├── application.properties                                  configuration with placeholders
        │   └── db/migration/                                           Flyway SQL migrations
        │
        └── java/mtogo/
                │
                ├── feature/                                            user, order, restaurant, ...
                │   │
                │   ├── user/                                           everything user (entity) related
                │   └── <feature>/                                      next feature implemented
                │           │
                │           │
                │           ├── presentation/
                │           │   │
                │           │   ├── <Feature>Controller.java
                │           │   │
                │           │   └── dto/
                │           │       ├── request/                        request records, e.g. CreateUserDTO - SoC
                │           │       └── response/                       response records, e.g. UserDTO - Soc
                │           │
                │           ├── domain/
                │           │   ├── <Entity>.java                       JPA entity, fields and relationships
                │           │   ├── I<Feature>Service.java              optional, only when a concrete need exists
                │           │   └── <Feature>Service.java
                │           │
                │           │
                │           └── data/
                │               ├── <Entity>Repository.java
                │               └── <Entity>Mapper.java
                │
                ├── config/                                             application configuration
                ├── exceptions/                                         ApiException hierarchy and GlobalExceptionHandler
                ├── security/                                           authentication, JWT and SecurityConfig
                ├── server/                                             application startup
                ├── utils/                                              helpers used by multiple features
                └── Main.java
```

## Alternatives

- **Features directly under `mtogo`:** Keeps paths shorter, but adds a root package for every feature.
- **A `domain` container:** Groups features, but repeats the name of the domain layer inside each feature.
- **Separate DAOs and mandatory service interfaces:** Adds files without a current need and duplicates responsibilities already covered by repositories.

## Consequences

- Each feature keeps its HTTP handling, business logic and database access together with clear responsibilities.
- Spring Data supplies standard CRUD; request and response DTOs define separate API contracts.
- Existing packages, DTOs and service interfaces must be aligned in a later code change; this decision does not move code.
