---
name: mtogo-review
description: Review an MTOGO issue branch against main on two axes, Standards and Spec, plus the Definition of Done, then fix the findings the developer picks. One round per session, after /mtogo-implement or /mtogo-quick.
disable-model-invocation: true
---

# MTOGO review

One review round on the issue branch, in a fresh session so the review doesn't inherit the implementation's assumptions: review, the developer picks findings, fix, save. Another round is a new session.

Two axes, each run by its own sub-agent so neither biases the other:

- **Standards**: does the code follow MTOGO's documented rules?
- **Spec**: does the code do what the issue, and the spec if there is one, ask?

## 1. Pin the diff

1. Run `git fetch origin main`. The fixed point is `origin/main`, unless the developer names another.
2. Capture `git diff origin/main...HEAD` and `git log origin/main..HEAD --oneline`.
3. Stop here if the ref doesn't resolve or the diff is empty.

## 2. Find the sources

- **Spec:** the issue's acceptance criteria from `gh issue view <issue>`, and `docs/specs/<issue>-*.md` or a spec this issue amends (its changelog lists the issue). Without a spec, the Spec axis reviews against the issue alone and says so.
- **Standards:** the paths of [CONTRIBUTING](../../../CONTRIBUTING.md), the [coding standards](../../../docs/coding-standards.md), the [Definition of Done](../../../docs/definition-of-done.md), the ADRs in `docs/adr/` the diff touches, the glossary and the [smell baseline](SMELLS.md). The Standards sub-agent reads them; you don't need to.

## 3. Run both axes in parallel

Start both sub-agents together, in the foreground. If your harness has no sub-agents, run the axes one after the other and keep their findings apart.

**Standards sub-agent** gets the diff command, the commit list and the standards paths. Brief: "Report per file and hunk: (a) every place the diff breaks a documented rule, citing the document and the rule; (b) any smell from the smell baseline, named, with the hunk quoted. A documented rule overrides a smell, and smells are always judgement calls. Check the tests against the Testing section of the coding standards. Skip what tooling enforces. Under 400 words."

**Spec sub-agent** gets the diff command, the commit list, the issue's acceptance criteria and the spec path, if there is a spec. Brief: "Report: (a) acceptance criteria, and the spec's behaviours and testing decisions if there is a spec, that are missing or partial; (b) anything the diff does that the issue or spec didn't ask for; (c) requirements that look implemented but wrongly; (d) if there is a spec, places where its implementation note disagrees with the code. Quote the issue or spec line for each finding. Under 400 words."

## 4. Check the Definition of Done

The axes cover items 1 and 2. Check the rest of the [Definition of Done](../../../docs/definition-of-done.md) yourself:

- **Item 3:** a `development` issue has two `Approved by:` labels. Flag any change that breaks or reshapes something outside the issue, so the developer can create an issue or tell the person responsible.
- **Item 4:** the documentation the item lists is updated where the change affects it, and, if there is a spec, its implementation note matches the current spec version.
- **Item 5:** no `TODO` or `FIXME` in the diff.
- **Item 6:** remind the developer to fill in Time spent.

## 5. Report

Present the reports under `## Standards`, `## Spec` and `## Definition of Done`, verbatim or lightly cleaned. Keep the axes separate and unranked against each other: code can follow every rule and still build the wrong thing, or build the right thing against the rules. End with one line: the number of findings per axis and the worst finding within each.

## 6. Fix what the developer picks

1. Ask the developer which findings to fix. Their choice is final, and unpicked findings stay unfixed.
2. Fix them following the build rules in [mtogo-implement](../mtogo-implement/SKILL.md#2-build).
3. If there is a spec, update its implementation note as described in [Implementation notes](../mtogo-spec/SPEC-FORMAT.md#implementation-notes), and bump the spec version first if a fix changes the spec. Without a spec, a fix that changes what the issue asks for is agreed with the developer and recorded on the issue.
4. Run `gh insitu run fix-all`, then `gh tt wrapup -m "<type>(<scope>): <description>"`.

## 7. Finish

Tell the developer the round is done. Another round is a fresh session with `/mtogo-review`. Delivery stays with the developer.
