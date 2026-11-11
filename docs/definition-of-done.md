# Definition of Done

**Version:** 1.1 · **Date:** 2026-11-11

An issue is done when its commit is on `main`. Before you run `gh tt deliver`, check that:

1. The acceptance criteria in the issue are met.
2. The code follows the [coding standards](coding-standards.md), and its behaviour is covered by tests as described in [Testing](coding-standards.md#testing).
3. At least two people have seen the change, following the [four-eyes principle](../CONTRIBUTING.md#four-eyes-principle).
4. Documentation affected by the change is updated.
5. Follow-up work is a new issue on the board, not a `TODO` in the code.

After `deliver`, the [ready flow](../CONTRIBUTING.md#delivery) checks the rest automatically. Formatting, build, tests and the SonarQube quality gate must all pass before the commit reaches `main`.

## Changelog

How to change this document is described in [CONTRIBUTING](../CONTRIBUTING.md#changing-the-documents).

| Version | Date       | Change             | Issue |
| ------- | ---------- | ------------------ | ----- |
| 1.0     | 2026-11-10 | First version      | -     |
| 1.1     | 2026-11-11 | Moved from `MTOGO` | #5    |
