---
name: mtogo-quick
description: Implement a small MTOGO issue in one session without grill or spec, to the same standards, then wrapup. Intended for ad hoc issues, including bug fixes; stops and recommends the full workflow when the work needs it.
disable-model-invocation: true
---

# MTOGO quick

The light workflow: one session from issue to saved work, without `/mtogo-grill` or `/mtogo-spec`. The coding standards and the Definition of Done apply exactly as in the full workflow. The developer can start it on any issue, but it is meant for `ad hoc` issues, including bug fixes.

## 1. Load

1. Take the issue number from the argument, or from the branch name. The branch must start with that number. If it doesn't, stop and ask the developer to run `gh tt workon -i <issue>`.
2. Run `gh issue view <issue> --comments` and state the issue's title and work label.
3. If the issue is labelled `development`, recommend the full workflow and continue only if the developer confirms.
4. The issue needs acceptance criteria, since the Definition of Done checks them. Each one must be checkable, by a test or by reading the change, as for a documentation update. If they are missing or can't be checked, propose new ones and, once the developer approves, update the issue with `gh issue edit <issue> --body-file <file>`.
5. Explore the code the issue changes, and read any spec in `docs/specs/` that describes that behaviour.

## 2. Check that it fits

If the change affects behaviour, ask the developer whether it needs a feature flag.

Stop and recommend the full workflow when the work needs anything listed in [Choosing a workflow](../../../docs/ai-workflow.md#choosing-a-workflow), including a flag. Name the reason. The developer decides whether to relabel the issue and run `/mtogo-grill`, or split it on the board. Check again whenever the work grows.

## 3. Plan

State the plan in a few lines: what changes and where, the rule set and test level if behaviour changes, and the documentation the change affects (item 4 of the [Definition of Done](../../../docs/definition-of-done.md)). Build once the developer confirms.

## 4. Build

- **A change to behaviour** follows the build rules in [mtogo-implement](../mtogo-implement/SKILL.md#2-build), with the issue's acceptance criteria in place of the spec. A bug fix starts with a failing test or CSV row that reproduces the bug.
- **A change to documentation, configuration or tooling only** needs no tests. Run the checks that cover the files you change.
- If the change alters behaviour an existing spec describes, update that spec, bump its version as described in [SPEC-FORMAT.md](../mtogo-spec/SPEC-FORMAT.md#versioning) and update its implementation note.
- Without a spec, the issue is the record. Propose a short comment on the issue for any decision the code doesn't show, and post it once the developer approves.
- Work outside the issue becomes a proposed follow-up issue.

The build is done when every acceptance criterion is met, each change to behaviour is covered by a passing test (or, in the frontend, a listed manual check), and the documentation is updated.

## 5. Save

1. Run `gh insitu run fix-all`.
2. `gh tt wrapup -m "<type>(<scope>): <description>"`, following [Commit messages](../../../CONTRIBUTING.md#commit-messages). The pre-commit hook runs `trunk-worthy`, and a failing check means the build isn't done.

## 6. Finish

If behaviour changed, tell the developer the next step is a fresh session with `/mtogo-review`. Otherwise the developer reviews the diff. Delivery stays with the developer.
