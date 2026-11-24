# Contributing

**Version:** 7.0 · **Date:** 2026-11-24

MTOGO is developed with Kanban and trunk-based development using [TakT](https://github.com/devx-cafe/gh-tt). Every change starts as an issue and ends as one commit on `main`.

This repository holds the backend. The frontend lives in [MTOGO-Frontend](https://github.com/Kanban4sem/MTOGO-Frontend), which has its own copy of this document.

- [Definition of Done](docs/definition-of-done.md): when an issue is finished
- [Coding standards](docs/coding-standards.md): how code is written, tested and documented

## Getting started

Setup is described in the [README](README.md#getting-started).

All commands in this document run inside the dev container.

## Board

Work is planned on the [project board](https://github.com/orgs/Kanban4sem/projects/1). Pick the next issue yourself. Nobody assigns work.

## Language

Everything written in the project is in English: issues, comments, commit messages, documentation, code, test data, log and exception messages and API documentation. The exception is text shown to end users in the app, such as the friendly error messages in the frontend, which is written in Danish to give the user a better experience.

When the text is about the domain, use domain language. Call a concept by the name the code uses for it (`order`, `restaurant`, `menu item`), and keep that name instead of inventing synonyms. Use technical terms only for technical concerns. The names are recorded in the [glossary](docs/glossary.md).

## Issues

Every change starts with an issue created from the issue template.

- Create the issue in the repository whose code it changes and add it to the board. `gh tt workon` only finds issues in the current repository, and one issue ends as one commit on one `main`. Work that changes both repositories gets one linked issue in each.
- Write the title in imperative mood without a type prefix: `Add order endpoint`. The title becomes the subject of the commit on `main`.
- Give the issue one of TakT's work labels: `development` for planned work, or `ad hoc` for a small change outside the plan. A bug fix is an `ad hoc` issue with the Type field set to Bug. Only `development` issues need the [four-eyes](#four-eyes-principle) approval before Ready. Also set the Type field and the `backend` or `frontend` label.
- Fill in the acceptance criteria. The Definition of Done is checked against them.
- Before `deliver`, fill in the **Time spent (h)** field on the board: the total time everyone spent on the issue, rounded up to the nearest 30 minutes, in hours (2 h 12 min is `2.5`).
- Don't fill in the **Cycle time (h)** field on the board. The `cycle-time` workflow computes it every hour for each closed issue on the board, in both repositories: the hours from the first move to In Progress until the last close.
- When the issue is done, add the label of the course week it was delivered in (`week 1`, `week 2`, …).

## Workflow

| Step    | Command                         | What happens                                                           |
| ------- | ------------------------------- | ---------------------------------------------------------------------- |
| Start   | `gh tt workon -i <issue>`       | Creates or reuses the issue branch and moves the issue to In Progress  |
| Save    | `gh tt wrapup -m "<message>"`   | Commits, pushes and notifies responsibles. Use it as often as you like |
| Sync    | `git pull --rebase origin main` | Rebases the issue branch onto the latest `main`                        |
| Deliver | `gh tt deliver`                 | Squashes the branch into one commit and pushes it to a `ready/` branch |

`gh tt workon -t "<title>"` creates a new issue and starts work on it in one step.

- Commit only through `gh tt`. Never push to `main`.
- Rebase before delivering. `deliver` aborts if the branch cannot fast-forward onto `main`.

### Delivery

Pushing to a `ready/` branch starts the ready flow, which runs the [quality gates](#quality-gates) on the squashed commit. If all pass, it fast-forwards `main`, closes the issue and deletes both branches. If a check fails, keep working on the issue branch and run `deliver` again.

## Commit messages

The messages you write with `gh tt wrapup` follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```text
<type>[optional scope]: <description>
```

- Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`, `style`, `build` and `ci`.
- English, imperative mood, lowercase, no trailing period, at most 72 characters.
- One line only: no body. `deliver` writes the commit on `main` from the issue title and lists your `wrapup` messages in its body. Do not edit it.
- The scope is the feature or area: `order`, `security`.
- Mark a breaking change with `!`: `feat(api)!: remove v1 endpoints`.
- Do not add the issue number. `wrapup` appends it.

```bash
gh tt wrapup -m "docs: add definition of done"
# commits: docs: add definition of done - #11

gh tt wrapup -m "feat(order): add order entity"
# commits: feat(order): add order entity - #12
```

## Four-eyes principle

At least two people agree on every feature before it is built. The check is on the issue, not on the code:

- **Before work starts:** a `development` issue moves from Specification to Ready only when two people have read and approved its content. Each approval is an `Approved by: <name>` label. `ad hoc` issues skip this.
- **While the work is done:** once the issue is picked up with `gh tt workon`, the developer owns the code review and the decisions made while building, helped by the AI review in `/mtogo-review`. Nobody waits for a second person to review the code.
- **When a change reaches further:** a change that breaks or reshapes something outside the issue gets a new issue, or a note to the developer responsible for it, who decides what to do.

`.github/RESPONSIBLES` maps files to the people responsible for them, one glob pattern and one or more GitHub handles per line. The file is read from the bottom up, so the last matching line wins. When `wrapup` commits changes to files you are not responsible for, it mentions the responsibles on the issue, so they know about the change.

## Quality gates

The checks are defined in `.insitu.yml` and run the same way locally and in CI.

| Gate                         | Runs                        | Checks                                          |
| ---------------------------- | --------------------------- | ----------------------------------------------- |
| `trunk-worthy`               | Pre-commit hook, ready flow | Spelling, Markdown, formatting, build and tests |
| `wrapup`                     | Issue branches              | Spelling, Markdown, formatting                  |
| SonarQube Cloud quality gate | Ready flow                  | Code quality and test coverage on new code      |

The quality gate thresholds are configured in SonarQube Cloud, not in this document.

The build and tests run once per wrapup, in the pre-commit hook, as TakT prescribes. The wrapup workflow on issue branches repeats only the fast checks.

- Fix formatting automatically with `gh insitu run fix-all`.
- Add correct words that the spell checker does not know to `.dict/repo.dictionary`.

## Releases

Releases follow [Semantic Versioning 2.0](https://semver.org/). Create the tag with `gh tt semver bump --major`, `--minor` or `--patch`, then push it with `git push origin --tags`.

## Architecture decisions

Decisions that are hard to reverse are recorded as Architecture Decision Records (ADRs) in `docs/adr/`, named `<number>-<title>.md` (`001-framework.md`). Each ADR has the sections Status, Context, Decision, Alternatives and Consequences. An ADR is proposed and accepted through an issue, and its Status links to that issue: `Proposed` while the issue is in progress, `Accepted` once the team agrees, before the issue is delivered.

An ADR lives in the repository that owns the decision, and the other repository links to it instead of keeping a copy. The backend owns the API contract, so most decisions that affect both repositories are recorded here.

## Feature flags

Unfinished behaviour that reaches `main` stays behind a feature flag that is off by default. Flags are environment variables read at startup, and the backend serves them to the frontend from `GET /v1/features`, as decided in [ADR-001](https://github.com/Kanban4sem/MTOGO-Backend/blob/main/docs/adr/001-feature-flags.md).

- Test the feature with its flag enabled, disabled and unset.
- A temporary flag gets a linked follow-up issue to remove it once the feature is fully released.
- How to add, use and remove a flag in the code is described in the [coding standards](docs/coding-standards.md#feature-flags).

## Working with AI

The [AI workflow](docs/ai-workflow.md) describes how we take an issue from the board to `main` with AI agents. `AGENTS.md` holds the rules the agents follow.

## Changing the documents

These rules apply to the documents below. A change to a document shared with [MTOGO-Frontend](https://github.com/Kanban4sem/MTOGO-Frontend) gets a linked issue in both repositories, so the copies stay the same.

| Document               | Shared with the other repository           |
| ---------------------- | ------------------------------------------ |
| CONTRIBUTING           | Yes                                        |
| Definition of Done     | Yes                                        |
| AI workflow            | Yes                                        |
| Glossary               | Yes                                        |
| Minimum viable product | Yes                                        |
| Coding standards       | The general sections named in its overview |
| AGENTS.md              | No. Each repository has its own            |

1. Create an issue that describes the change.
2. Agree on it as a team.
3. Make the change with the normal [workflow](#workflow).
4. Update the version and date, and add a row to the document's changelog with the issue number. Compare with `main`: an issue bumps a document's version once, however many changes it makes.
   - Minor version (1.0 → 1.1): clarification without a change in meaning.
   - Major version (1.1 → 2.0): a rule is added, removed or changed.

## Changelog

| Version | Date       | Change                                                                    | Issue    |
| ------- | ---------- | ------------------------------------------------------------------------- | -------- |
| 1.0     | 2026-11-10 | First version                                                             | -        |
| 1.1     | 2026-11-10 | Glossary, issue per repository, ADR status, one-line commits, AI workflow | MTOGO#32 |
| 2.0     | 2026-11-11 | Moved from `MTOGO`. Linked issues for shared documents, ADR ownership     | #5       |
| 2.1     | 2026-11-11 | The minimum viable product is a shared document                           | #19      |
| 2.2     | 2026-11-11 | ADR Documentation for Feature Flags added                                 | #7       |
| 3.0     | 2026-11-12 | Feature flags are added to and removed from `FeatureFlagsDTO`             | #30      |
| 3.1     | 2026-11-12 | Flag steps name the tests to update, one version bump per issue           | #33      |
| 4.0     | 2026-11-12 | Four-eyes on the issue before Ready, time spent and week labels on issues | #34      |
| 4.1     | 2026-11-13 | Use the shared authentication flag in feature flag examples               | #35      |
| 4.2     | 2026-11-16 | Flag steps move to the coding standards, repeated rules link to one place | #39      |
| 5.0     | 2026-11-16 | TakT's work labels: a bug fix is `ad hoc` with Type Bug                   | #47      |
| 6.0     | 2026-11-24 | Issue branches run the fast checks, the build and tests run in the hook   | #58      |
| 7.0     | 2026-11-24 | Time spent is a board field, cycle time is computed on the board          | #51      |
