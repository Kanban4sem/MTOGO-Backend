# AI Workflow

**Version:** 1.1 · **Date:** 2026-11-11

How we use AI agents (Claude Code and Codex) to take an issue from the board to `main`. The flow is based on the skills in [mattpocock/skills](https://github.com/mattpocock/skills), adapted to TakT and to Kanban issues without tickets.

[CONTRIBUTING](../CONTRIBUTING.md), the [coding standards](coding-standards.md) and the [Definition of Done](definition-of-done.md) apply to AI work exactly as they do to your own. Where a skill and these documents disagree, the documents win. The rules agents follow are in [`AGENTS.md`](../AGENTS.md).

## The flow

Every issue goes through the same steps. You create the issue yourself, alone or with AI, before the flow starts.

| Step      | Who   | Command                                          | Result                                                        |
| --------- | ----- | ------------------------------------------------ | ------------------------------------------------------------- |
| Start     | You   | `gh tt workon -i <issue>`                        | Issue branch, issue moves to In Progress                      |
| Session 1 | Agent | `/mtogo-grill`                                   | Agreed plan, proposed ADRs, glossary terms                    |
|           | Agent | `/mtogo-spec`                                    | `docs/specs/<issue>-<slug>.md`, saved and linked on the issue |
| Session 2 | Agent | `/mtogo-implement docs/specs/<file>`             | Code, tests and the implementation note, saved                |
| Session 3 | Agent | `/mtogo-review`                                  | Review findings; the fixes you pick, saved                    |
| Repeat    | Agent | `/mtogo-review` in a new session                 | Until you are satisfied                                       |
| Deliver   | You   | `git pull --rebase origin main`, `gh tt deliver` | One commit on `main`                                          |

Start each session fresh (`/clear` in Claude Code, a new chat in Codex):

- **Session 1** keeps grilling and spec together, so the spec is written from the full conversation rather than a summary of it.
- **Session 2** starts from the spec alone. If the spec isn't enough to build from, the spec is incomplete, and that's worth finding out.
- **Session 3 and later** review the code without the assumptions made while writing it.

Before `deliver`, check the Definition of Done yourself. In particular, the team must have accepted the ADRs from session 1, and a second person must have seen the change.

## One issue, one spec

We use no tickets. Each issue has exactly one spec, so the board shows the whole picture:

- A new behaviour gets a new spec, named after the issue that created it.
- An issue that changes behaviour an existing spec describes amends that spec with a new version.
- An issue too large to implement in one session is split on the board, not into tickets.
- An issue belongs to one repository. Work in the other repository (`MTOGO-Backend` or `MTOGO-Frontend`) gets its own linked issue and spec.

The issue stays the source of the acceptance criteria. If grilling changes them, the agent proposes the new text and updates the issue once you approve. The spec copies them and adds user stories, implementation decisions, testing decisions and what is out of scope.

## Specs and implementation notes

A spec carries a version and a changelog like our other documents. Its implementation notes record what was actually built for each spec version: what exists, deviations from the spec, the tests, and follow-up issues. Review fixes update the current note. A fix that changes the spec bumps the spec version and adds a new note. The template and the exact rules are in [SPEC-FORMAT.md](../.agents/skills/mtogo-spec/SPEC-FORMAT.md).

## Git

You own every commit. An agent never commits as itself.

| Action                                           | You                     | Agent        |
| ------------------------------------------------ | ----------------------- | ------------ |
| `gh tt workon`                                   | Yes                     | No           |
| `gh tt wrapup` (commits under your git identity) | Yes                     | Yes          |
| `git pull --rebase origin main`                  | Yes                     | When you ask |
| `gh tt deliver`                                  | Yes                     | No           |
| `git commit`, `git push`, changes to `main`      | Not needed: use `gh tt` | No           |

Commits carry no AI trailer such as `Co-Authored-By`. For Claude Code, `.claude/settings.json` turns off attribution, and a hook in `.claude/hooks/guard-git.mjs` blocks the commands in the last row. Codex relies on the rules in `AGENTS.md`.

When an agent's work shapes a change, the spec and its implementation notes are the record. Link to the chat or summarise it on the issue only when it holds decisions the spec doesn't.

## Four-eyes

AI review does not count toward the [four-eyes principle](../CONTRIBUTING.md#four-eyes-principle). `/mtogo-review` is a tool for you; a second person still has to see the change.

## Other skills

Use these outside the main flow, or within a session when they fit:

| Skill                 | Use when                                                               |
| --------------------- | ---------------------------------------------------------------------- |
| `/diagnosing-bugs`    | A bug resists a first look. It builds a failing loop before theorising |
| `/prototype`          | A design question needs runnable code to answer, during grilling       |
| `/research`           | You need facts from primary sources gathered into a file               |
| `/codebase-design`    | You are designing a module's interface or deciding where a seam goes   |
| `/handoff`            | Work moves to another harness, repository or colleague                 |
| `/wait-what`          | The agent's last answer didn't make sense                              |
| `/writing-for-agents` | You are editing `AGENTS.md` or a skill                                 |

`grilling`, `domain-modeling` and `tdd` are used by the `mtogo-*` skills and rarely called directly.

## Changing the AI setup

- The `mtogo-*` skills in `.agents/skills/` carry out this document. Change them in the same issue as the document, following [Changing the documents](../CONTRIBUTING.md#changing-the-documents).
- The other skills come unchanged from `mattpocock/skills` and are pinned in `skills-lock.json`. Don't edit them. If one needs to behave differently, fork it under the `mtogo-` prefix.

## Changelog

How to change this document is described in [CONTRIBUTING](../CONTRIBUTING.md#changing-the-documents).

| Version | Date       | Change             | Issue    |
| ------- | ---------- | ------------------ | -------- |
| 1.0     | 2026-11-10 | First version      | MTOGO#32 |
| 1.1     | 2026-11-11 | Moved from `MTOGO` | #5       |
