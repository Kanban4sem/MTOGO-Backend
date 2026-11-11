# Contributing

**Version:** 2.0 · **Date:** 2026-11-11

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

Everything written in the project is in English:

- Issues: title, description and acceptance criteria
- Comments on issues and the project board, including summaries of AI chats
- Commit messages
- ADRs, READMEs and other documentation
- Code, code comments, test display names and the `reason` column in test data
- Log messages, exception messages and API documentation

The exception is text shown to end users in the app - user-facing text, such as the friendly error messages in the frontend, is written in Danish to give the user a better experience.

When the text is about the domain, use domain language. Call a concept by the name the code uses for it (`order`, `restaurant`, `menu item`), and keep that name instead of inventing synonyms. Use technical terms only for technical concerns. The names are recorded in the [glossary](docs/glossary.md).

## Issues

Every change starts with an issue created from the issue template.

- Create the issue in the repository whose code it changes and add it to the board. `gh tt workon` only finds issues in the current repository, and one issue ends as one commit on one `main`. Work that changes both repositories gets one linked issue in each.
- Write the title in imperative mood without a type prefix: `Add order endpoint`. The title becomes the subject of the commit on `main`.
- Set the type of work with the issue's Type field or a label.
- Fill in the acceptance criteria. The Definition of Done is checked against them.

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

| Type       | Use for                                              |
| ---------- | ---------------------------------------------------- |
| `feat`     | A new feature                                        |
| `fix`      | A bug fix                                            |
| `docs`     | Documentation only                                   |
| `refactor` | A change that neither fixes a bug nor adds a feature |
| `test`     | Adding or correcting tests                           |
| `chore`    | Maintenance, build, dependencies, tooling            |

`perf`, `style`, `build` and `ci` are also allowed.

- English, imperative mood, lowercase, no trailing period, at most 72 characters.
- One line only: no body. `deliver` writes the body of the commit on `main` from your `wrapup` messages.
- The scope is the feature or area: `order`, `security`.
- Mark a breaking change with `!`: `feat(api)!: remove v1 endpoints`.
- Do not add the issue number. `wrapup` appends it.

```bash
gh tt wrapup -m "feat: add CONTRIBUTING.md"
# commits: feat: add definition-of-done.md - #11

gh tt wrapup -m "feat(order): add order entity"
# commits: feat(order): add order entity - #12
```

`deliver` generates the commit on `main` from the issue title and lists your `wrapup` messages in its body. Do not edit it.

## Four-eyes principle

At least two people see every change before it is delivered. This happens while the work is done, not as an approval step afterwards:

- Pair or mob program on the issue, or
- Involve a responsible for the files you change

`.github/RESPONSIBLES` maps files to the people responsible for them, one glob pattern and one or more GitHub handles per line. The file is read from the bottom up, so the last matching line wins. When `wrapup` commits changes to files you are not responsible for, it mentions the responsibles on the issue so they can join or help. Nobody waits for an approval.

## Quality gates

The checks are defined in `.insitu.yml` and run the same way locally and in CI.

| Gate                         | Runs                                        | Checks                                          |
| ---------------------------- | ------------------------------------------- | ----------------------------------------------- |
| `trunk-worthy`               | Pre-commit hook, issue branches, ready flow | Spelling, Markdown, formatting, build and tests |
| SonarQube Cloud quality gate | Ready flow                                  | Code quality and test coverage on new code      |

The quality gate thresholds are configured in SonarQube Cloud, not in this document.

- Fix formatting automatically with `gh insitu run fix-all`.
- Add correct words that the spell checker does not know to `.dict/repo.dictionary`.

## Releases

Releases follow [Semantic Versioning 2.0](https://semver.org/). Create the tag with `gh tt semver bump --major`, `--minor` or `--patch`, then push it with `git push origin --tags`.

## Architecture decisions

Decisions that are hard to reverse are recorded as Architecture Decision Records (ADRs) in `docs/adr/`, named `<number>-<title>.md` (`001-framework.md`). Each ADR has the sections Status, Context, Decision, Alternatives and Consequences. An ADR is proposed and accepted through an issue, and its Status links to that issue: `Proposed` while the issue is in progress, `Accepted` once the team agrees, before the issue is delivered.

An ADR lives in the repository that owns the decision, and the other repository links to it instead of keeping a copy. The backend owns the API contract, so most decisions that affect both repositories are recorded here.

## Working with AI

- [AI workflow](docs/ai-workflow.md) describes how we take an issue from the board to `main` with AI agents.
- `AGENTS.md` holds the rules AI agents follow.
- When an AI chat shapes a change, the issue's spec records the decisions. Link to the chat or summarize it in a comment on the issue only when it holds decisions the spec doesn't.
- Run `gh insitu run fix-all` after AI edits, since they bypass format-on-save.

## Changing the documents

This applies to this document, the Definition of Done, the coding standards, the AGENTS document, the AI workflow and the glossary.

This document, the Definition of Done, the AI workflow, the glossary and the general sections of the coding standards are shared with [MTOGO-Frontend](https://github.com/Kanban4sem/MTOGO-Frontend), which keeps its own copy. A change to a shared document gets a linked issue in both repositories, so the copies stay the same.

1. Create an issue that describes the change.
2. Agree on it as a team.
3. Make the change with the normal [workflow](#workflow).
4. Update the version and date, and add a row to the document's changelog with the issue number.
   - Minor version (1.0 → 1.1): clarification without a change in meaning.
   - Major version (1.1 → 2.0): a rule is added, removed or changed.

## Changelog

| Version | Date       | Change                                                                    | Issue    |
| ------- | ---------- | ------------------------------------------------------------------------- | -------- |
| 1.0     | 2026-11-10 | First version                                                             | -        |
| 1.1     | 2026-11-10 | Glossary, issue per repository, ADR status, one-line commits, AI workflow | MTOGO#32 |
| 2.0     | 2026-11-11 | Moved from `MTOGO`. Linked issues for shared documents, ADR ownership     | #5       |
