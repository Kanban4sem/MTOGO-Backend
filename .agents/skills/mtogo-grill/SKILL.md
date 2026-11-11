---
name: mtogo-grill
description: Grill an MTOGO issue until developer and agent agree on the implementation plan, recording ADRs and glossary terms as they land. Session 1, step 1.
disable-model-invocation: true
---

# MTOGO grill

Turn one issue into an implementation plan the developer agrees with. This is session 1, step 1. `/mtogo-spec` follows in the same session, so the grilling stays a primary source for the spec.

## 1. Load the issue

1. Take the issue number from the argument, or from the branch name (`git rev-parse --abbrev-ref HEAD` starts with it).
2. The branch must start with that issue number. If it doesn't, stop and ask the developer to run `gh tt workon -i <issue>`.
3. Run `gh issue view <issue> --comments` and state the issue's title.
4. If `docs/specs/` already has a spec for the behaviour this issue changes, read it. This issue then amends that spec.

## 2. Read the context

Read the three source documents named in `AGENTS.md`, the glossary and the ADRs in the area the issue touches. Explore the code the issue will change.

## 3. Grill

Call the Skill tool with `grilling` and `domain-modeling`, and run both. Record glossary terms and ADRs where `AGENTS.md` says they live. A new ADR gets `Status: Proposed` with a link to this issue. It becomes `Accepted` when the team agrees, before delivery.

The design tree must cover these branches as well as the issue's own decisions:

- **Acceptance criteria.** The Definition of Done checks the issue's criteria, so they must be testable. If the grilling changes them, write out the new criteria and, once the developer approves, update the issue body with `gh issue edit <issue> --body-file <file>`.
- **One repository.** If the work also needs changes in the other code repository, propose a linked issue there for that part.
- **One session.** If the plan is too large to implement in one session, propose splitting the issue on the board.
- **Seams and rule sets.** Where tests go and which rule sets they cover, as described in [Testing](../../../docs/coding-standards.md#testing). In the frontend, the manual checks.
- **API contract.** Any change to paths, DTOs or status codes, and the frontend issue it implies.
- **Follow-up.** Work the grilling defers becomes a proposed issue, never a `TODO`.

## 4. Finish

The grilling is done when the frontier is empty and the developer confirms the shared understanding. Then tell the developer to run `/mtogo-spec` in this same session.
