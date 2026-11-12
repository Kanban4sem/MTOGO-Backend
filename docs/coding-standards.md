# Coding Standards

**Version:** 3.0 · **Date:** 2026-11-12

## Overview

These standards keep the code consistent, readable and easy to change. This copy covers the backend. `MTOGO-Frontend` has its own copy with the frontend rules.

The general rules under [Naming](#naming), the sections [REST API and JSON](#rest-api-and-json), [Documentation files](#documentation-files) and [Comments](#comments) (apart from the Javadoc rule, which is backend only) are shared with `MTOGO-Frontend`. Change them through a linked issue in both repositories, as described in [CONTRIBUTING](../CONTRIBUTING.md#changing-the-documents).

| Source                                      | Answers                                   |
| ------------------------------------------- | ----------------------------------------- |
| [CONTRIBUTING](../CONTRIBUTING.md)          | How do we work and deliver?               |
| [Definition of Done](definition-of-done.md) | When is an issue finished?                |
| This document                               | How do we structure, write and test code? |
| `.editorconfig`                             | How is code formatted?                    |

Exact versions live in `pom.xml`. Technology choices are recorded as ADRs in `docs/adr/`. Add or upgrade a dependency only through an issue the team has agreed on, and prefer what the project already has.

Good design reduces complexity. If a rule here makes the code more complicated, propose a change as described in [CONTRIBUTING](../CONTRIBUTING.md#changing-the-documents).

## Formatting

- `.editorconfig` is the source of truth for indentation, line endings, encoding and line length.
- Java uses Allman style: opening braces go on their own line.
- Spotless checks Java formatting. Prettier formats Markdown, JSON and YAML.
- Format the code you change, but do not reformat unrelated code.
- Change formatting rules only in `.editorconfig` or the tool configuration, in a separate issue.

## Naming

Names describe what something is or does in domain language. One concept keeps one name in every layer (`Order` → `OrderDTO` → `/orders`). Avoid abbreviations and vague names such as `data`, `info`, `handle` or `manager`. If a good name is hard to find, the design is probably unclear.

### Java

| Element                    | Convention                                         | Example                          |
| -------------------------- | -------------------------------------------------- | -------------------------------- |
| Package                    | lowercase, feature first                           | `mtogo.order.domain`             |
| Class, record, enum        | PascalCase noun                                    | `OrderService`                   |
| Interface                  | `I` + PascalCase. The implementation drops the `I` | `IOrderService` → `OrderService` |
| Method                     | camelCase verb                                     | `getAll()`, `cancel()`           |
| Variable, field, parameter | camelCase, descriptive                             | `deliveryAddress`                |
| Boolean                    | `is` / `has` / `can` prefix                        | `isPaid`                         |
| Constant, enum value       | UPPER_SNAKE_CASE                                   | `IN_DELIVERY`                    |
| Test class                 | `<ClassUnderTest>Test`                             | `OrderServiceTest`               |

Spring Data repositories are interfaces without the `I` prefix, because Spring generates the implementation.

| Suffix       | Role                                              | Example                      |
| ------------ | ------------------------------------------------- | ---------------------------- |
| `Controller` | Handles HTTP requests                             | `OrderController`            |
| `Service`    | Business logic                                    | `OrderService`               |
| `Repository` | Database access (Spring Data)                     | `OrderRepository`            |
| `Mapper`     | Converts entity ↔ DTO                             | `OrderMapper`                |
| `DTO`        | Request or response record, `<Action><Entity>DTO` | `CreateOrderDTO`, `OrderDTO` |
| `Exception`  | Error type                                        | `NotFoundException`          |
| `Config`     | Spring configuration                              | `SecurityConfig`             |
| `Util`       | Stateless helper                                  | `DateUtil`                   |

### REST API and JSON

- **Paths:** lowercase plural nouns, kebab-case for more than one word (`/orders`, `/menu-items/{id}`). The HTTP method carries the action.
- **JSON fields:** camelCase (`deliveryAddress`).
- **Enum values:** UPPER_SNAKE_CASE (`IN_DELIVERY`).
- **Dates:** `YYYY-MM-DD`. **Timestamps:** ISO-8601.

### Documentation files

- Files in the repository root that GitHub or other tools look for by name are UPPERCASE: `README.md`, `CONTRIBUTING.md`, `AGENTS.md`, `LICENSE`.
- Everything in `docs/` is lowercase kebab-case: `definition-of-done.md`, `coding-standards.md`.
- ADRs are numbered: `docs/adr/001-framework.md`.

## Architecture

The code follows [Presentation Domain Data Layering](https://martinfowler.com/bliki/PresentationDomainDataLayering.html): the code is organised by feature first, with presentation, domain and data layers inside each feature.

### Backend structure

```text
mtogo
├── <feature>            order, restaurant, customer, ...
│   ├── presentation     Controller, dto/
│   ├── domain           entities, service interface and implementation
│   └── data             Repository, Mapper
├── security             authentication, JWT, SecurityConfig
├── config               application configuration
├── exceptions           ApiException hierarchy and GlobalExceptionHandler
└── utils                stateless helpers
```

| Layer            | Responsible for                                                                               | Must not                                   |
| ---------------- | --------------------------------------------------------------------------------------------- | ------------------------------------------ |
| **Presentation** | Reading the request, validating its shape, calling one service method, returning the response | Contain business rules or use repositories |
| **Domain**       | Business rules, authorization decisions that depend on data, orchestration                    | Know about HTTP requests or responses      |
| **Data**         | Persistence with Spring Data JPA, entity ↔ DTO mapping                                        | Contain business rules                     |

Dependencies point one way: presentation → domain → data.

1. **Depend on interfaces and inject through constructors.** Fields are `final`. No `@Autowired` on fields.
2. **Entities never cross the service boundary.** Services return DTOs. The DTOs are the API contract, so an entity can change without breaking the API.
3. **Field rules are Bean Validation annotations** on request DTOs (`@NotBlank`, `@Size`, `@Positive`) and are checked with `@Valid` in the controller. Rules that need stored data or combine several fields belong in the service.
4. **Access control is declared per endpoint** with `@PreAuthorize` on the controller method. Decisions that depend on data, such as "only your own orders", belong in the service.
5. **Queries bind values as parameters.** Use derived query methods or `@Query` with named parameters. Never build a query by concatenating strings.
6. **Flyway owns the schema.** Every schema change is a new migration in `src/main/resources/db/migration`, named `V<number>__<description>.sql`. A migration that has run is never edited.
7. **No secrets in code or configuration.** `application.properties` holds placeholders. Local values go in `.env`, which is never committed.
8. **Lombok removes boilerplate.** Do not use `@Data` on JPA entities, because its `equals` and `hashCode` break with lazy loading.
9. **Share only what is shared.** Code moves out of a feature only when at least two features use it.
10. **A new feature copies the shape of an existing one.**

## Comments

- Code shows what and how. Comments say what the code cannot: why, assumptions, constraints, domain rules and non-obvious choices.
- If a comment repeats the next line, delete it or improve the name.
- No divider or section comments - If a class needs sections, split it.
- For an interface method whose behaviour is not obvious from its signature, write a short Javadoc describing the contract: edge cases, exceptions and side effects.
- Update comments in the same commit as the code they describe.
- No commented-out code.
- No `TODO` or `FIXME` - Create an issue instead.

## Error handling

Errors are returned as [`ProblemDetail`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html) (RFC 9457). Expected failures are `ApiException` subclasses, thrown in the layer that detects them. One `GlobalExceptionHandler` turns everything else into a response.

### Backend errors

| Where                        | Does                                                                                                               |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| **Presentation**             | Validates the request shape with `@Valid`. Never builds error responses.                                           |
| **Domain**                   | Enforces business rules and throws `ApiException` subclasses.                                                      |
| **Data**                     | Does not catch persistence exceptions. Spring translates them to `DataAccessException`.                            |
| **`GlobalExceptionHandler`** | Extends `ResponseEntityExceptionHandler`. Handles unexpected exceptions, adds `errorId` to 5xx responses and logs. |
| **`SecurityConfig`**         | Returns `ProblemDetail` for `401` and `403`, which are raised before a controller is reached.                      |

| Exception               | Status | Use when                                     |
| ----------------------- | ------ | -------------------------------------------- |
| `BadRequestException`   | 400    | Input is well-formed but breaks a rule       |
| `UnauthorizedException` | 401    | Credentials or token are missing or invalid  |
| `ForbiddenException`    | 403    | The user is authenticated but not allowed    |
| `NotFoundException`     | 404    | The resource does not exist                  |
| `ConflictException`     | 409    | The request conflicts with the current state |

Failed `@Valid` checks become `400` automatically. Any other exception becomes a generic `500`.

- All exceptions are unchecked.
- **Keep the cause:** `new NotFoundException("Order not found with id: " + id, e)`.
- **Never swallow an exception.** No empty `catch` blocks. Catch only to translate, recover or clean up.
- **Log once, in `GlobalExceptionHandler`.** 4xx as warnings, 5xx as errors with the stack trace. Do not log and rethrow.
- **4xx details are for the client.** They say what is wrong and are safe to show.
- **5xx responses are always generic** (`Internal server error`) and carry an `errorId`. The details stay in the log under the same `errorId`. Never put secrets, SQL or stack traces in a response.
- **Add an exception class only when it needs its own status code.** `NotFoundException` with a message covers every entity.

### Adding an exception

1. Look up the status in the [MDN HTTP status reference](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status).
2. Name the exception after the MDN status name in PascalCase with the `Exception` suffix: `422 Unprocessable Content` → `UnprocessableContentException`.
3. Create it in `mtogo.exceptions`, extending `ApiException`, with two constructors.
4. Add it to the table above.

`ApiException` extends Spring's `ErrorResponseException`, so the handler renders every subclass as a `ProblemDetail` without further code:

```java
public abstract class ApiException extends ErrorResponseException
{
    protected ApiException(HttpStatus status, String detail, Throwable cause)
    {
        super(status, ProblemDetail.forStatusAndDetail(status, detail), cause);
    }
}
```

```java
public class UnprocessableContentException extends ApiException
{
    public UnprocessableContentException(String detail)
    {
        this(detail, null);
    }

    public UnprocessableContentException(String detail, Throwable cause)
    {
        super(HttpStatus.UNPROCESSABLE_CONTENT, detail, cause);
    }
}
```

## Testing

Every change ships with tests. The [Definition of Done](definition-of-done.md) requires that behaviour is covered. This section defines how.

### Detroit school

Tests follow the Detroit (classical) school:

- Test behaviour through public methods, with real collaborators.
- Verify the resulting state, not which methods were called.
- Use a real PostgreSQL database through Testcontainers, never an in-memory replacement.
- Mock only systems outside our control, such as external APIs or email. Never mock our own classes.

### General rules

- Test at the level where the behaviour lives (see [Techniques per test level](#techniques-per-test-level)).
- Tests mirror the main package structure under `src/test/java`.
- Describe behaviour, not implementation: `@DisplayName("Cancel - should reject an order that is in delivery")`.
- Tests are independent of each other and of execution order. Each test creates the data it needs, and the database is cleaned between tests.
- Tests never touch a shared or production database.
- Coverage is measured with JaCoCo and reported to SonarQube Cloud. It shows what is untested. It is not a goal in itself.

### Test case design

Test cases are chosen systematically with three black-box techniques. They are derived from the acceptance criteria and the documented API rules, not from the implementation. If the code and the specification disagree, the test follows the specification and the disagreement is raised with the team.

```text
Acceptance criteria → test design (EP, BVA, decision table) → consolidated test cases → CSV test data → automated test
```

| Technique                     | Use when                                                                     | Produces                                                      |
| ----------------------------- | ---------------------------------------------------------------------------- | ------------------------------------------------------------- |
| Equivalence Partitioning (EP) | An input has groups of values that the system treats the same way            | One representative per class, valid and invalid               |
| Boundary Value Analysis (BVA) | A class has an edge: a minimum, a maximum, or an ordering between two values | The value on each boundary and the nearest value on each side |
| Decision table                | The outcome depends on a combination of two or more conditions               | One test case per rule in the table                           |

#### Equivalence Partitioning

- Split every input into classes where all values should give the same result. Always include the invalid classes.
- Pick one representative from the middle of each class. Boundaries are covered by BVA.
- Start from these classes and add the ones the domain rules introduce:

| Input type              | Classes to consider                                                                |
| ----------------------- | ---------------------------------------------------------------------------------- |
| Required string         | `null`, empty (`""`), whitespace only, valid. Too long, if a maximum length exists |
| Number with a range     | Below minimum, within range, above maximum                                         |
| Date pair               | Second date before the first, second date not before the first                     |
| Enum                    | Each value that a rule treats differently. An unknown value at endpoint level      |
| Path ID                 | Non-numeric, zero or negative, positive but unknown, existing                      |
| Boolean or binary state | Both values                                                                        |

#### Boundary Value Analysis

For every boundary, test the value on the boundary and the nearest value on each side, using the smallest step of the type:

| Type          | Step                     | Example                                                               |
| ------------- | ------------------------ | --------------------------------------------------------------------- |
| Integer       | `1`                      | Range 1–20: `0, 1, 2, 19, 20, 21`                                     |
| Decimal       | Smallest meaningful unit | Price above 0: `0.00, 0.01`                                           |
| Date          | 1 day                    | Not before `startDate`: `startDate − 1`, `startDate`, `startDate + 1` |
| String length | 1 character              | Max 100: lengths `99, 100, 101`                                       |

- A range open at one end has one boundary. `>= 0` gives `-1, 0, 1`.
- A point inside the valid range where a rule changes behaviour is also a boundary. Test both sides of it.

#### Decision tables

- Use a decision table when two or more conditions together decide the outcome, such as access control (token, role, ownership) or rules that combine state and input.
- Conditions are columns, the result is the last column, and `-` means the condition does not affect the result in that rule.
- Each rule becomes at least one test case.

Example: `GET /orders/{id}`, where customers may only see their own orders:

| Rule | Valid token? | Order exists? | Own order? | Result |
| ---- | ------------ | ------------- | ---------- | ------ |
| R1   | No           | -             | -          | `401`  |
| R2   | Yes          | No            | -          | `404`  |
| R3   | Yes          | Yes           | No         | `403`  |
| R4   | Yes          | Yes           | Yes        | `200`  |

### Consolidation and implementation

The techniques overlap. `0` can be both an invalid EP class and a boundary value. The techniques are used to find test cases, not to create three separate test suites.

- Merge the test cases into one consolidated set per rule set, such as the status rules of `cancel` in `OrderService`.
- Every EP class, boundary value and decision table rule appears in at least one row. Remove rows that cover nothing new.
- Each row has a `reason` that names the techniques it represents and what it tests.
- Implement the set as one data-driven `@ParameterizedTest` with `@CsvFileSource`.

#### Test data files

Test cases are stored in CSV files under version control, not inline in the test class. This keeps the test design reviewable as a table, separate from the code that runs it. Use a CSV file even for small sets.

```text
src/test/resources/
└── testcases/
    └── <feature>/                 order, restaurant, customer, ...
        └── <class-under-test>/    kebab-case, e.g. order-service, order-controller
            └── <rule-set>.csv     kebab-case, e.g. cancel-status.csv
```

- One file per parameterized test method, and one method per file.
- The rule-set name starts with the method or endpoint action: `create-items`, `cancel-status`, `get-by-id-access`.
- Reference the file with an absolute classpath path: `/testcases/order/order-service/cancel-status.csv`.
- CSV files are changed in the same commit as the tests that use them.

| Item           | Convention                                                                                                                                                                    |
| -------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Header         | First line, camelCase names matching the method parameters, skipped with `numLinesToSkip = 1`                                                                                 |
| Column order   | Inputs, then `expected`, then `reason`                                                                                                                                        |
| `expected`     | Service tests: `ACCEPT` or the exception's simple class name. Endpoint tests: the HTTP status code. Configuration tests: the bound value or the exception's simple class name |
| `reason`       | `<techniques>: <what the row tests>`, e.g. `"EP: cancel an order in delivery"`                                                                                                |
| Quoting        | Quote values that contain a comma or leading or trailing spaces. Always quote `reason`                                                                                        |
| `null` / empty | `NULL` with `nullValues = "NULL"`. Empty string is `""`                                                                                                                       |
| Dates          | `YYYY-MM-DD`, fixed values. Never relative to today                                                                                                                           |
| Display name   | `name = "[{index}] {n}"`, where `n` is the zero-based index of `reason`                                                                                                       |

```text
status,expected,reason
PLACED,ACCEPT,"EP: cancel a placed order"
ACCEPTED,ACCEPT,"EP: cancel an accepted order"
IN_DELIVERY,ConflictException,"EP: cancel an order in delivery"
DELIVERED,ConflictException,"EP: cancel a delivered order"
```

```java
@ParameterizedTest(name = "[{index}] {2}")
@CsvFileSource(resources = "/testcases/order/order-service/cancel-status.csv", numLinesToSkip = 1)
@DisplayName("Cancel - is allowed only before delivery")
void cancelIsAllowedOnlyBeforeDelivery(OrderStatus status, String expected, String reason)
{
    long orderId = testData.createOrder(status);

    if (expected.equals("ACCEPT"))
    {
        OrderDTO result = orderService.cancel(orderId);
        assertThat(result.status(), is(OrderStatus.CANCELLED));
    }
    else
    {
        ApiException exception = assertThrows(ApiException.class, () -> orderService.cancel(orderId));
        assertThat(exception.getClass().getSimpleName(), is(expected));
    }
}
```

The service, its repository and the database are all real. The test checks the returned state, not the calls made.

Tests that do not check an input or business rule, such as mapping or a single happy path, are ordinary `@Test` methods with a `@DisplayName`.

### Techniques per test level

| Level         | Setup                                                                               | Apply                                                                                                                                                                               |
| ------------- | ----------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Domain        | Plain JUnit, no Spring                                                              | EP, BVA and decision tables for logic in entities and other classes that need no database                                                                                           |
| Service       | `@SpringBootTest`, Testcontainers                                                   | The main place. EP, BVA and decision tables for business rules and data-dependent authorization                                                                                     |
| Endpoint      | `@SpringBootTest` with a random port, REST Assured, Testcontainers                  | EP and BVA for Bean Validation rules on request DTOs. A decision table for access (`401` / `403` / `404` / `2xx`). One invalid case per service rule set to prove the error mapping |
| Repository    | `@DataJpaTest`, Testcontainers                                                      | Only for custom queries                                                                                                                                                             |
| Configuration | `ApplicationContextRunner` with `withPropertyValues`, no database and no web server | EP over each property value, including an invalid value that stops the backend at startup                                                                                           |

### Designing test cases step by step

1. **List the rules** for the method or endpoint: the acceptance criteria, the validation annotations on the request DTO, the access rule on the endpoint and data-dependent authorization.
2. **Partition** every input with EP.
3. **Find the boundaries** of every class with BVA.
4. **Build a decision table** where two or more conditions combine.
5. **Consolidate** into one CSV file per rule set, with `expected` and `reason`.
6. **Implement** the rule set as one `@ParameterizedTest` that reads the CSV file. A case covered by a CSV row is not repeated as a separate `@Test`.
7. **Treat a failing row as a bug or an unclear rule.** Never change `expected` to match the code. Fix the code, or raise the rule with the team.

## API documentation

The API documentation is generated from the code with springdoc-openapi. The specification is served at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

- Annotate each controller with `@Tag` and each endpoint with `@Operation(summary = "...")`.
- Document error responses specific to an endpoint with `@ApiResponse`. Errors that every protected endpoint can return (`401`, `403`) are not repeated.
- Add `@Schema(description = "...", example = "...")` to DTO fields whose meaning is not obvious from the name. Bean Validation rules appear in the specification automatically.
- A change to the API is a change to the contract with the frontend. Create a linked issue in `MTOGO-Frontend` for the frontend change (see [Issues](../CONTRIBUTING.md#issues)).
- The OpenAPI specification is the contract with the frontend. The frontend reads errors from the `ProblemDetail` responses described in [Error handling](#error-handling).

## Changelog

| Version | Date       | Change                                  | Issue    |
| ------- | ---------- | --------------------------------------- | -------- |
| 1.0     | 2026-11-10 | First version                           | -        |
| 1.1     | 2026-11-10 | API changes get a linked frontend issue | MTOGO#32 |
| 1.2     | 2026-11-10 | Fixes to Frontend Architecture          | MTOGO#33 |
| 2.0     | 2026-11-11 | Moved from `MTOGO`, backend rules only  | #5       |
| 3.0     | 2026-11-12 | Configuration tests                     | #30      |
