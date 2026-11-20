# Seed restaurant and delivery-agent users

**Version:** 1.0 · **Date:** 2026-11-20 · **Issue:** #52 · **Feature flag:** None

## Acceptance criteria

Copied from #52. The issue is the source: change the issue first, then this copy.

- [ ] A seeded restaurant user can log in and gets the RESTAURANT role
- [ ] A seeded delivery-agent user can log in and gets the DELIVERY_AGENT role
- [ ] `kebab@mtogo.example` is linked to MTOGO Kebab and `bakery@mtogo.example` to MTOGO Bakery
- [ ] With a role's seed password set in `.env`, the backend creates that role's seeded users on startup, and each can log in with its documented email and that password
- [ ] The seeded customer has a phone number and a profile address. Every seeded user has a phone number
- [ ] With a role's seed password unset or blank, none of that role's users are seeded
- [ ] A seed password that breaks the password rules stops the startup
- [ ] An existing user with a seeded email is not changed
- [ ] No account with a password known from the repository exists on prod

## Behaviour

1. A restaurant user handles orders for exactly one restaurant. A restaurant can have several restaurant users.
2. A customer and a delivery agent have no restaurant.
3. A user with the restaurant role cannot exist without a restaurant, and a user with any other role cannot have one.
4. A customer who registers still gets the customer role and no restaurant.
5. On every startup, the backend creates the seeded users of each role whose seed password is set:

   | Email                    | Role           | Restaurant   | Name           | Seed password                  |
   | ------------------------ | -------------- | ------------ | -------------- | ------------------------------ |
   | `customer@mtogo.example` | CUSTOMER       | None         | Test Customer  | `SEED_CUSTOMER_PASSWORD`       |
   | `kebab@mtogo.example`    | RESTAURANT     | MTOGO Kebab  | MTOGO Kebab    | `SEED_RESTAURANT_PASSWORD`     |
   | `bakery@mtogo.example`   | RESTAURANT     | MTOGO Bakery | MTOGO Bakery   | `SEED_RESTAURANT_PASSWORD`     |
   | `agent@mtogo.example`    | DELIVERY_AGENT | None         | Delivery Agent | `SEED_DELIVERY_AGENT_PASSWORD` |

6. Every seeded user is active, prefers Danish and has a phone number.
7. The seeded customer has a profile address: postal code 2800, Kongens Lyngby, Denmark, without a street. The other seeded users have no address.
8. A seeded user can log in with their email and their role's seed password, and gets their role.
9. When a role's seed password is unset or blank, none of that role's users are created, and the backend logs that it skipped them.
10. When a seed password has fewer than 8 characters or more than 72 UTF-8 bytes, the backend does not start.
11. When a user with a seeded email already exists, the backend leaves that user unchanged: password, role, profile and restaurant stay as they are. This includes a customer who registered with that email.
12. Restarting the backend creates no duplicate seeded users.
13. Seeding does not depend on the authentication flag.
14. Prod sets no seed passwords, so no account with a password known from the repository exists on prod. A prod deployment refuses to run while prod sets one.

## Implementation decisions

- **Credentials:** [ADR-006](../adr/006-seeded-user-credentials.md) (Proposed). Emails, roles, names, phone numbers, the customer's address and the restaurant links are committed. The passwords come from the environment only. Seeding creates missing users and never changes existing ones. To rotate a password, delete the user and restart.
- **Schema:** one Flyway migration adds a nullable `restaurant_id` to users, referencing restaurants, with an index. It seeds no users. The migration creates no database check that ties the link to the role, because the role is a row in another table.
- **Domain:** in the user feature, `User` gets a many-to-one restaurant, the same way an order references its restaurant. The rule in behaviour 3 is enforced where a user is created, so registration and seeding share it. Registration keeps creating customers without a restaurant.
- **Seeding:** a seed service in the user feature creates the seeded users through the user, address, role and restaurant features' existing domain types and repositories. It hashes through the existing password service, so registration's password rules apply. It finds restaurants by their seeded names, and startup fails with a clear message unless exactly one restaurant has that name. A startup bean in `server/` runs the seed service on every startup once all beans exist, so after Flyway has migrated and JPA has started, and before the web server accepts requests. A Flyway callback can't do it, because Flyway migrates before JPA starts. An invalid password throws, rolls back the seeding of every role and stops startup. A seeded email that already exists is skipped with a warning.
- **Configuration:** `app.seed.customer-password`, `app.seed.restaurant-password` and `app.seed.delivery-agent-password`, bound from `SEED_CUSTOMER_PASSWORD`, `SEED_RESTAURANT_PASSWORD` and `SEED_DELIVERY_AGENT_PASSWORD`, each defaulting to empty. They are added to `.env.example` and to the droplet's `backend.env.example` with a note to leave them empty on prod. `deploy.sh` refuses a prod deployment while prod's `backend.env` sets a non-blank seed password.
- **API:** no change to paths, DTOs or status codes. If the frontend later needs a restaurant user's restaurant on the current-user endpoint, #16 adds it.
- **Documentation:** a _Seeded users_ section in the README lists the table in behaviour 5, the variables and how to rotate a password, and says prod leaves the variables unset. Stage passwords live in the droplet's `backend.env`. The team has no shared secret store yet, so the README says to ask a team member with access to the droplet.
- **Architecture:** follows [ADR-004](../adr/004-backend-structure.md) and [ADR-005](../adr/005-account-authentication.md).

## Testing decisions

All database tests use `@SharedDatabase`, real collaborators and no mocks, following `UserServiceTest` and `UserControllerTest`.

- **Seed service, seed method:** decision table over each role's password set, unset or blank against which seeded users exist afterwards; `seed-roles`.
- **Seed service, seed method:** BVA for each role at 7, 8 and 9 characters and at 71, 72 and 73 UTF-8 bytes, with the other roles' passwords valid. Valid passwords seed, and an invalid one throws and seeds nobody, of any role; `seed-lengths`. Prior art: `PasswordServiceTest` with `hash-utf8-bytes`.
- **Seed service, scenarios:** an existing user with a seeded email, including a customer registered as `kebab@mtogo.example`, is unchanged and logged as skipped. A second run creates no new users. Each role without a seed password is logged as skipped.
- **Seed service, state:** the full persisted graph per seeded user: active, role, name, language, phone number, the customer's address and postal code, and each restaurant user's restaurant.
- **User creation:** EP over role (customer, restaurant, delivery agent) × restaurant present or absent. Only a restaurant user with a restaurant and the other roles without one are accepted; `restaurant-link`.
- **Login, HTTP:** with the seed passwords set, each seeded user logs in through `POST /v1/users/login`, and `GET /v1/users/me` reports their role; `seeded-login`. Prior art: `UserControllerTest`.
- **Startup wiring:** an application context with the seed passwords set has the seeded users after startup, also with the authentication flag off. An application context with an invalid seed password fails to start.
- **Configuration:** `AppPropertiesTest` binds the three seed passwords as unset, blank and set; `bind-seed-passwords`.

## Out of scope

- A street on profile addresses, and addresses for restaurants or delivery agents. Saved customer addresses are #22.
- Restaurant users seeing or handling orders (#16) and delivery agents delivering them (#17).
- Creating, editing or deleting restaurant users and delivery agents through the API.
- Resetting seeded passwords from the environment.
- Frontend changes. Logging in already works for every role. Copying the new glossary terms is a deliverable of Kanban4sem/MTOGO-Frontend#9.

## Further notes

The team must accept ADR-006 before #52 is delivered. The domain model now shows the link between a restaurant user and their restaurant.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.0 - 2026-11-20 - #52

- **Built:** `V5__link_users_to_restaurants.sql` adds the nullable, indexed `users.restaurant_id`. `User.assignRole(role, restaurant)` enforces behaviour 3 with a `BadRequestException`, and `User.activate(passwordHash, role, restaurant)` sets the hash, activates the user and assigns the role, so registration and seeding create users the same way; the role and restaurant have no public setters. `UserSeedService` creates the missing seeded users of each role whose password is set, in one transaction, hashing once per role through `PasswordService` so an invalid password stops startup even when the role's users already exist. It looks each restaurant up with `RestaurantRepository.findAllByName` and fails with a message naming the user and restaurant unless exactly one matches, since `restaurants.name` isn't unique. `mtogo.server.UserSeedInitializer` runs it on every startup. `AppProperties.Seed` binds `app.seed.*` from the three `SEED_*` variables and gives a role's password through `passwordFor(RoleName)`, documented in `.env.example`, `deploy/backend.env.example` and the README's _Seeded users_ section. `deploy.sh` refuses a prod deployment while prod's `backend.env` sets a non-blank `SEED_*_PASSWORD`. The seeded phone numbers are `+4500000001` to `+4500000004`, which no Danish subscriber can have.
- **Deviations:** The spec had a Flyway `Callback` run the seeder, but Flyway migrates before JPA starts, so a callback that uses the repositories fails startup with a circular bean reference. Agreed with the developer: a `SmartInitializingSingleton` runs the seed service once all beans exist and before the web server accepts requests. The spec stays at 1.0 because it isn't on `main` yet; its Seeding decision, ADR-006 (Proposed), the MVP document and the issue now say "a startup step". The test configuration sets the seed passwords empty, so a developer's `.env` never seeds users into test databases.
- **Tests:** `UserTest` with `user/user/restaurant-link.csv`. `UserSeedServiceTest` with `user/user-seed-service/seed-roles.csv` and `seed-lengths.csv`, plus the existing-user (with its warning), skipped-role log, second-run and persisted-state scenarios. `UserSeedInitializerTest` with `server/user-seed-initializer/seeded-login.csv` over `POST /v1/users/login` and `GET /v1/users/me`, startup seeding, startup seeding with the authentication flag off (behaviour 13), and an invalid seed password that stops a startup. `AppPropertiesTest` with `config/app-properties/bind-seed-passwords.csv`. `CauseUtil` in `mtogo.testsupport` walks a startup failure's causes for both configuration and startup tests. Behaviour 4 stays covered by `UserServiceTest` and `UserControllerTest`. Behaviour 14: `seed-roles.csv` shows that empty variables seed nobody, and the `deploy.sh` guard was checked with sample `backend.env` lines: a set password blocks, and an empty, blank or commented-out one passes. The guard runs only once the new `deploy.sh` is installed on the droplet with `sudo install`, as in #11.
- **Follow-up:** None

## Changelog

| Version | Date       | Change        | Issue |
| ------- | ---------- | ------------- | ----- |
| 1.0     | 2026-11-20 | First version | #52   |
