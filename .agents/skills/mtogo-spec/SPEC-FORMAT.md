# Spec format

A spec lives at `docs/specs/<issue>-<slug>.md` in the repository whose code it changes. `<issue>` is the number of the issue that created it, and `<slug>` is the issue title in kebab-case. The file keeps that name for life, even when later issues amend it.

The issue stays the card on the board and the checklist for the [Definition of Done](../../../docs/definition-of-done.md). The spec adds what the issue lacks: the decisions behind the implementation. It does not repeat the issue's Problem, Solution and Value.

## Template

```md
# <Issue title>

**Version:** 1.0 · **Date:** <YYYY-MM-DD> · **Issue:** #<issue> · **Feature flag:** <None, or `<flag-name>`, off by default>

## Acceptance criteria

Copied from #<issue>. The issue is the source: change the issue first, then this copy.

- [ ] <criterion>

## Behaviour

1. <actor> can <action> when <condition>

## Implementation decisions

- <decision>

## Testing decisions

- <rule set or manual check>

## Out of scope

- <item>

## Further notes

<notes, or "None.">

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

## Changelog

| Version | Date         | Change        | Issue    |
| ------- | ------------ | ------------- | -------- |
| 1.0     | <YYYY-MM-DD> | First version | #<issue> |
```

## Sections

- **Feature flag**: the developer's answer to whether the issue needs a flag, as `AGENTS.md` requires. `None`, or the flag's name. A flag is off by default.
- **Behaviour**: an extensive numbered list covering everything the issue asks the system to do, one behaviour per line, in domain language from the [glossary](../../../docs/glossary.md). Include what the system refuses, not only what it allows. For example: "1. A customer can cancel an order while it is Pending." and "2. A customer cannot cancel an order that is Delivered." A user story ("As a …, I want …, so that …") is also fine where the benefit matters to the reader, but no format is required. The rule is that each line is readable and can be tested.
- **Implementation decisions**: the modules built or changed and their interfaces, API contracts (paths, DTOs, status codes), schema changes (Flyway migrations), access rules and the ADRs that apply. No file paths or code snippets, since they go stale. The exception is a prototype snippet that states a decision more precisely than prose (a state machine, a schema), trimmed to the decision and marked as coming from a prototype.
- **Testing decisions**: the seams agreed with the developer, and for each one the rule sets from [Testing](../../../docs/coding-standards.md#testing): which method or endpoint, the techniques (EP, BVA, decision table) and the CSV rule-set name (`cancel-status`). Name existing tests to copy as prior art. In the frontend, list the manual checks against the running API instead.
- **Out of scope**: what the grilling ruled out, and the follow-up issues it created.

## Versioning

Versions follow [Changing the documents](../../../CONTRIBUTING.md#changing-the-documents):

- A new spec starts at **1.0**.
- **Minor** (1.0 → 1.1): a clarification that does not change what is built.
- **Major** (1.1 → 2.0): a requirement or acceptance criterion is added, changed or removed.

Every version change updates the header's version and date and adds a changelog row with the issue that caused it. Versions compare with `main`: an issue bumps a spec once, however many changes it makes, and a spec that isn't on `main` yet stays at 1.0. A later issue that changes the behaviour of an existing spec amends that spec with a new version rather than creating another spec.

## Implementation notes

The implementation notes record what was actually built for each spec version, under `## Implementation notes`, newest last:

```md
### Spec <version> - <YYYY-MM-DD> - #<issue>

- **Built:** <what now exists, in domain language>
- **Deviations:** <where the code differs from the spec and why, or "None">
- **Tests:** <seams, rule-set CSV files and test classes, or the manual checks>
- **Follow-up:** <issues created, or "None">
```

- `/mtogo-implement` adds the note for the spec version it implemented.
- A review fix that leaves the spec unchanged updates the note for the current version in place and sets its date to today. Git keeps the earlier text.
- A fix that changes the spec bumps the spec version first, unless this issue already bumped it, then adds a new note under the new version. Otherwise it updates the current note in place. Earlier notes stay as history.
