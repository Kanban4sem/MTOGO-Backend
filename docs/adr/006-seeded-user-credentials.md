# ADR-006: Seeded User Credentials

## Status

Accepted — [#52](https://github.com/Kanban4sem/MTOGO-Backend/issues/52)

## Context

Customers register themselves, but restaurant users and delivery agents can't, so the MVP seeds them. Developers also need a user for each role right after setting up the database. Both repositories are public and stage and prod run the same image ([ADR-003](003-deployment-pipeline.md)), so any password or hash committed in a migration would give accounts with known passwords on every environment, including prod.

## Decision

- Seeded users never have a password in the repository. Their emails, roles, profiles and restaurants are committed. Each role's password comes from an environment variable.
- A versioned migration only changes the schema. The users are created by a startup step that runs on every startup after Flyway has migrated and JPA has started, before the backend accepts requests. Setting a variable later and restarting creates the missing users.
- The startup step only creates users whose email doesn't exist yet. It never changes an existing user. To rotate a password, delete the user and restart.
- An unset or blank variable skips that role's users. A variable that breaks the password rules stops the startup.
- Prod leaves the variables unset, so prod has no seeded users. `deploy.sh` refuses a prod deployment while prod sets one.

## Alternatives

- **Committed BCrypt hashes in a migration:** simplest, but anyone reading the repository knows the passwords on every environment the migration runs on.
- **A stage-only migration location:** keeps the users off prod, but the stage passwords are still public and the migration history differs between environments.
- **A versioned migration with Flyway placeholders:** runs only once, so variables set after the first startup have no effect.
- **A Flyway callback after every migrate:** runs at the right moment, but Flyway migrates before JPA starts, so the callback could only seed through SQL and not through the domain types that enforce the user rules.
- **The environment as the source of truth:** resetting passwords on every startup makes rotation easy but overwrites changes made in the application.

## Consequences

- Seeding runs on every startup, independent of feature flags.
- Each environment that wants seeded users must set the variables, and the team shares the stage passwords outside the repository.
- Hashing goes through the same password rules as registration.
- An email taken by a registered user is skipped, so that seeded user is missing until the conflicting user is removed.
