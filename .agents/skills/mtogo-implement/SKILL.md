---
name: mtogo-implement
description: Implement a whole MTOGO spec on its issue branch, test-first to the coding standards, then write the implementation note and wrapup. Session 2.
disable-model-invocation: true
---

# MTOGO implement

Build everything the spec asks for. This is session 2, started fresh: the spec is your only input from session 1.

## 1. Load

1. Take the spec path from the argument, or find `docs/specs/<issue>-*.md` from the branch's issue number. State the spec's title and version.
2. The branch must start with the spec's issue number. If it doesn't, stop and ask the developer to run `gh tt workon -i <issue>`.
3. Read the spec, the issue (`gh issue view <issue>`), the ADRs the spec names and the glossary.
4. Read the three source documents named in `AGENTS.md` in full. In the coding standards, the sections that bind every line you write are Naming, Architecture, Comments, Error handling, Testing and API documentation.

## 2. Build

Work through the spec's testing decisions one rule set at a time. Each rule set is one vertical slice.

**Backend.** Call the Skill tool with `tdd` for the red-green loop at the seams the spec agreed. Where `tdd` and [Testing](../../../docs/coding-standards.md#testing) differ, the coding standards win:

- Design the rule set's cases from the acceptance criteria with EP, BVA and decision tables, and consolidate them into the CSV file first. The CSV file plus its `@ParameterizedTest` is the failing test of the slice.
- Test with real collaborators and a real PostgreSQL database through Testcontainers. Mock only systems outside our control.
- When a CSV row fails, fix the code or raise the rule with the developer. `expected` follows the spec.

From the repository root, run the test class you are working on after each change (`./mvnw -q test -Dtest=<TestClass>`) and the full `./mvnw verify` once at the end.

**Frontend.** There is no test framework yet. Follow the frontend structure in the coding standards, run `npm run lint` from the repository root after each change, and list the manual checks from the spec for the developer to run against the API.

**Throughout:**

- A spec that turns out wrong or impossible stops the build. Agree the change with the developer, update the spec and bump its version as described in [SPEC-FORMAT.md](../mtogo-spec/SPEC-FORMAT.md#versioning), then continue.
- Work outside the spec becomes a proposed follow-up issue. Create it with `gh issue create` once the developer approves.
- At green checkpoints you may save with `gh tt wrapup -m "<message>"`.

The build is done when every acceptance criterion and user story maps to a passing test (or, in the frontend, a listed manual check), and the full suite passes.

## 3. Write the implementation note

Add the note for this spec version as described in [Implementation notes](../mtogo-spec/SPEC-FORMAT.md#implementation-notes).

## 4. Save

1. Run `gh insitu run fix-all`.
2. `gh tt wrapup -m "<type>(<scope>): <description>"`, following [Commit messages](../../../CONTRIBUTING.md#commit-messages). The pre-commit hook runs `trunk-worthy`, and a failing check means the build isn't done.

## 5. Finish

Tell the developer that session 2 is done and the next step is a fresh session: `/mtogo-review`. Leave the review to that session, so it reads the code without this session's assumptions.
