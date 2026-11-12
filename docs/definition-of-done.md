# Definition of Done

**Version:** 2.0 · **Date:** 2026-11-12

An issue is done when its commit is on `main`. Before you run `gh tt deliver`, check that:

1. The acceptance criteria in the issue are met.
2. The code follows the [coding standards](coding-standards.md), and its behaviour is covered by tests as described in [Testing](coding-standards.md#testing).
3. You have reviewed the change and the decisions in it, following the [four-eyes principle](../CONTRIBUTING.md#four-eyes-principle). A `development` issue was approved by two people before it moved to Ready.
4. Documentation affected by the change is updated.
5. Follow-up work is a new issue on the board, not a `TODO` in the code.
6. **Time spent** on the issue is filled in, rounded up to the nearest 30 minutes.

After `deliver`, the [ready flow](../CONTRIBUTING.md#delivery) checks the rest automatically. Formatting, build, tests and the SonarQube quality gate must all pass before the commit reaches `main`.

## Changelog

How to change this document is described in [CONTRIBUTING](../CONTRIBUTING.md#changing-the-documents).

| Version | Date       | Change                                             | Issue |
| ------- | ---------- | -------------------------------------------------- | ----- |
| 1.0     | 2026-11-10 | First version                                      | -     |
| 1.1     | 2026-11-11 | Moved from `MTOGO`                                 | #5    |
| 2.0     | 2026-11-12 | You review your own change, time spent is recorded | #34   |
