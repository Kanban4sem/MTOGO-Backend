# Definition of Done

**Version:** 4.0 · **Date:** 2026-11-24

An issue is done when its commit is on `main`. Before you run `gh tt deliver`, check that:

1. The acceptance criteria in the issue are met.
2. The code follows the [coding standards](coding-standards.md), and its behaviour is covered by tests as described in [Testing](coding-standards.md#testing).
3. You have reviewed the change and the decisions in it, following the [four-eyes principle](../CONTRIBUTING.md#four-eyes-principle).
4. Documentation affected by the change is updated: the issue's spec and its implementation note, if it has one, ADRs and glossary terms, the README and the API documentation.
5. Follow-up work is a new issue on the board, not a `TODO` in the code.
6. The **Time spent (h)** field on the board is filled in for the issue, as described in [Issues](../CONTRIBUTING.md#issues).

After `deliver`, the [ready flow](../CONTRIBUTING.md#delivery) checks the rest automatically. Formatting, build, tests and the SonarQube quality gate must all pass before the commit reaches `main`.

## Changelog

How to change this document is described in [CONTRIBUTING](../CONTRIBUTING.md#changing-the-documents).

| Version | Date       | Change                                             | Issue |
| ------- | ---------- | -------------------------------------------------- | ----- |
| 1.0     | 2026-11-10 | First version                                      | -     |
| 1.1     | 2026-11-11 | Moved from `MTOGO`                                 | #5    |
| 2.0     | 2026-11-12 | You review your own change, time spent is recorded | #34   |
| 3.0     | 2026-11-16 | The documentation to update is listed              | #39   |
| 4.0     | 2026-11-24 | Time spent is a board field                        | #51   |
