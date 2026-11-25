---
name: mtogo-test
description: Find and fill missing or noncompliant tests for specified MTOGO backend work against CONTRIBUTING, the Definition of Done and coding standards. Use for test backfills and focused test compliance checks, including work already on main.
---

# MTOGO test

Bring the tests for the backend work the developer names into compliance with the project's documented rules. Add and correct tests, rather than stopping at a list of gaps. Neither `/mtogo-implement` nor `/mtogo-review` is a prerequisite. Stay within testing; this is not a general code review or a new feature implementation.

## 1. Load and scope

1. Read `AGENTS.md` and the three source documents in full: [CONTRIBUTING](../../../CONTRIBUTING.md), [coding standards](../../../docs/coding-standards.md) and [Definition of Done](../../../docs/definition-of-done.md). These override this skill and existing test examples. Derive requirements only from these documents; do not add a smell baseline or invent coverage thresholds.
2. Take the scope from the developer: features, paths, an issue, a spec or a commit range. Inspect the current branch, working tree and relevant history. Include staged, unstaged and untracked work belonging to the scope, and preserve unrelated work.
3. If no scope was given, use the current issue branch's changes against `origin/main`, including local work. State the comparison used. If that leaves no clear scope, ask which work to cover. An empty diff does not prevent testing named features already on `main`.
4. Read relevant specs, testing decisions, glossary terms and ADRs. Fetch the relevant issues' acceptance criteria with `gh issue view <issue>` where available. A backfill need not have its own feature spec. Identify missing, ambiguous or inaccessible requirements and ask only for the information needed; continue with rule sets whose requirements are clear.
5. Inspect scoped production code, request validation, access rules, custom queries, existing tests, CSV files and test setup. Use implementation details to locate behaviour and validation boundaries, not as the authority for expected outcomes. Read `pom.xml`, `.insitu.yml` and the setup instructions in `docs/getting-started.md` and `docs/build-and-test.md` before running checks.

State the scope and sources used. Work on the developer's existing issue branch; do not create branches, switch branches or modify `main`.

## 2. Map the test gaps

Build a compact table: behaviour or rule set, requirement source, test level, existing test and CSV, missing cases or standards violations, and the concrete defect each proposed test would catch. Distinguish missing coverage from tests using the wrong setup or design. Assess scoped behaviour even if implemented in an earlier issue.

Apply [Testing](../../../docs/coding-standards.md#testing) at the level where the behaviour lives:

| Level | Setup and responsibility |
| ----- | ------------------------ |
| Domain | Plain JUnit, no Spring. Logic that needs no database. |
| Service | `@SpringBootTest` and PostgreSQL through the shared test container (`@SharedDatabase`). Business rules and data-dependent authorization. |
| Endpoint | `@SpringBootTest` with a random port, REST Assured and `@SharedDatabase`. Request DTO validation, access decisions and error mapping. |
| Repository | `@DataJpaTest` and `@SharedDatabase`, only for custom queries. |
| Configuration | `ApplicationContextRunner` with `withPropertyValues`, no database or web server. Property binding, including invalid values that prevent startup. |

Do not test inherited Spring Data CRUD solely to create a test per class. At endpoint level, cover request validation and access, plus one invalid case per service rule set to prove error mapping; keep the full business-rule matrix at service level. Verify the documented response contract, including `ProblemDetail`, where relevant. For feature flags in scope, cover enabled, disabled and unset as CONTRIBUTING requires.

### Purpose and sufficient coverage

The developer requires purposeful, thorough tests that remain practical to maintain. Before adding a test or CSV row, identify the observable behaviour it protects and the plausible defect it would detect. Put that purpose in the gap map and make it clear in the display name or CSV `reason`. A test that would still pass if the relevant behaviour were broken does not provide adequate evidence.

- Cover the full documented behaviour: successful outcomes, meaningful failures, applicable boundaries and access decisions. Prefer a small, deliberate set of cases that collectively covers those obligations. Reducing duplication must not remove a required EP class, boundary value or decision-table rule.
- Reuse or extend an existing test and CSV when they already cover the same rule set. Do not duplicate the full matrix across layers, enumerate combinations whose outcome is unaffected, or add arbitrary inputs from the same equivalence class without a distinct purpose.
- Do not add tests solely for line coverage, a test-per-class convention, generated accessors or framework behaviour. A mapping test is useful when it protects an actual contract, such as omitted, transformed or sensitive fields; its existence is not mandatory merely because a mapper exists.
- Assert the outcome that matters. A success status alone is insufficient when the requirement concerns persisted changes, filtering, relationships or data disclosure. For a rejected operation, check unchanged state when preventing side effects is part of the rule. Avoid asserting unrelated details that make tests brittle.
- Keep setup and assertions focused on the rule under test. Use small, readable fixtures and helpers where they remove real repetition. Do not build a generic test framework, sprawling dispatch logic or enormous test files to accommodate unrelated cases. Follow the documented class and package conventions; there is no arbitrary line limit or test-count target.

For example, several arbitrary valid restaurant names add no value if they exercise the same class. A valid name, applicable invalid classes and the documented length boundaries serve distinct purposes. A menu test should detect the relevant defect, such as returning another restaurant's items, rather than merely prove that the request returns `200`.

## 3. Design and implement

Work one rule set at a time. Present the gap map and proceed with scoped test work; a separate approval round is not required.

1. Derive cases from acceptance criteria and documented API rules. Use Equivalence Partitioning (EP) for valid and invalid classes, Boundary Value Analysis (BVA) at each boundary and its nearest neighbours, and decision tables when two or more conditions determine the outcome. Include combinations of authentication, roles, existence and ownership where documented access rules require them. Do not invent constraints.
2. Consolidate overlapping cases into one set per rule set. Every applicable EP class, boundary value and decision-table rule must be represented, without duplicate cases that add no coverage.
3. Store each set in `src/test/resources/testcases/<feature>/<class-under-test>/<rule-set>.csv`. Use kebab-case for directory and file names; start the rule-set name with the method or endpoint action. One CSV file serves one `@ParameterizedTest` method, and one method reads one file, even for small sets.
4. Follow the CSV conventions in the coding standards: camelCase headers matching parameters; inputs, then `expected`, then quoted `reason`; `reason` names the techniques and what the row tests. Service expectations are `ACCEPT` or the exception's simple class name; endpoint expectations are HTTP statuses; configuration expectations are bound values or exception names. Use `NULL` with `nullValues = "NULL"`, `""` for empty strings, and fixed dates. Quote values containing commas or leading or trailing spaces.
5. Use `@CsvFileSource` with an absolute classpath resource path and `numLinesToSkip = 1`. Use `@ParameterizedTest(name = "[{index}] {n}")`, replacing `n` with the zero-based index of `reason`. Add behaviour-focused `@DisplayName` text. Mapping tests and single happy paths that do not check an input or business rule may be ordinary `@Test` methods. Do not repeat CSV cases as ordinary tests.
6. Follow the Detroit school: exercise public methods with real collaborators and assert returned or persisted state. Use real PostgreSQL through the shared test container with `@SharedDatabase` (see Test database in the coding standards), never an in-memory replacement or a container of your own. Mock only systems outside our control, never our own classes; do not verify internal call sequences.
7. Mirror production packages under `src/test/java` and name classes `<ClassUnderTest>Test`. Each test creates its own data, cleans the database between tests and is independent of execution order. Tests must never use shared or production databases. Reuse compliant test setup; correct noncompliant scoped tests rather than copying their violations. Keep code, display names and CSV reasons in English.

Run the focused test class as each rule set is added or corrected. Expected results follow the specification. Never weaken assertions, change CSV expectations to match a bug, disable failing tests or replace Testcontainers to get a green run. If a test exposes a production defect, report the requirement, failing case and affected code. Fix production code only when the developer has authorized that scope; otherwise continue independent test work and identify the unresolved defect. Resolve unclear rules before claiming compliance.

## 4. Verify and record

Run commands from the repository root inside the dev container, as CONTRIBUTING requires:

1. Run `./mvnw -q test -Dtest=<TestClass>` for changed test classes.
2. Run `gh insitu run fix-all` after editing and inspect its diff. Keep formatting changes within the requested scope.
3. Run the full `./mvnw -q -B clean verify` and the configured `trunk-worthy` checks (`gh insitu run trunk-worthy`). The latter already includes full Maven verification, so one successful execution there satisfies both checks.
4. Inspect the JaCoCo report for remaining gaps in scoped behaviour. Coverage helps find omissions; it is not proof of correct tests or a target percentage. SonarQube Cloud thresholds live in its quality gate. Do not claim that gate passed without its result.

If setup, Docker, dependencies or tools prevent a check, report the exact blocker and which checks remain unverified. A skipped or unrun test is not a pass.

Update an affected spec's existing implementation note with test classes, CSV rule sets and verification results, following [Implementation notes](../mtogo-spec/SPEC-FORMAT.md#implementation-notes). Do not invent a feature spec or bump its version solely for added tests. Keep deferred work as proposed follow-up issues, never `TODO` or `FIXME`; create issues only when authorized. Do not change architectural rules or examples to describe test additions.

Save only when the developer asks, using `gh tt wrapup -m "test(<scope>): <description>"` under CONTRIBUTING's commit rules. Leave delivery to the developer.

Before finishing, review the added tests and CSV rows for purpose, sufficient assertions and duplication. Remove cases that add no distinct evidence, simplify excessive setup, and confirm that every required behaviour and applicable test-design case remains covered.

## 5. Finish

Report the covered scope, tests and CSV rule sets added or corrected, checks actually run and their results, remaining gaps and unresolved defects. Cite the relevant documented rule for remaining compliance findings. Distinguish test compliance from overall Definition of Done: passing this skill does not certify unrelated documentation, approvals, time spent or delivery. Do not claim completion while required scoped test cases or checks remain unresolved.
