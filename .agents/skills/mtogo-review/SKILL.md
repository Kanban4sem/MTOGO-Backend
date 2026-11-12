---
name: mtogo-review
description: Review an MTOGO issue branch against main on two axes, Standards and Spec, plus the Definition of Done, then fix the findings the developer picks. Session 3 and later, one round per session.
disable-model-invocation: true
---

# MTOGO review

One review round on the issue branch. Run it in a fresh session so the review doesn't inherit the implementation's assumptions. Each round is: review, the developer picks findings, fix, save. Another round is a new session.

The review has two axes, each run by its own sub-agent with a fresh context window so neither biases the other:

- **Standards**: does the code follow MTOGO's documented rules?
- **Spec**: does the code do what the spec and the issue ask?

Then the Definition of Done items an agent can check.

## 1. Pin the diff

1. Run `git fetch origin main`. The fixed point is `origin/main`, unless the developer names another.
2. Capture `git diff origin/main...HEAD` and `git log origin/main..HEAD --oneline`.
3. Stop here if the ref doesn't resolve or the diff is empty.

## 2. Find the sources

- **Spec:** `docs/specs/<issue>-*.md` from the branch's issue number, or a spec this issue amends (its changelog lists the issue). Also fetch the issue's acceptance criteria with `gh issue view <issue>`. If there's no spec, the Spec axis reviews against the issue alone and says so.
- **Standards:** [CONTRIBUTING](../../../CONTRIBUTING.md), [coding standards](../../../docs/coding-standards.md) and [Definition of Done](../../../docs/definition-of-done.md), plus the ADRs in `docs/adr/` the diff touches and the glossary. Then the smell baseline below.

### Smell baseline

A fixed set of Fowler code smells (_Refactoring_, ch. 3) that applies on top of the documents:

- **The documents override.** Where a documented rule endorses something a smell would flag, the smell is suppressed. "A new feature copies the shape of an existing one" and "share only what is shared" are such rules.
- **Always a judgement call.** Each smell is a labelled heuristic ("possible Feature Envy"), never a hard violation. Skip anything tooling already enforces (formatting, lint, spelling).

Each smell reads _what it is_ → _how to fix_:

- **Mysterious Name**: a name that doesn't reveal what it does or holds. → rename it; if no honest name comes, the design is murky.
- **Duplicated Code**: the same logic shape in more than one hunk or file. → extract the shared shape, call it from both.
- **Feature Envy**: a method that reaches into another object's data more than its own. → move it onto the data it envies.
- **Data Clumps**: the same few fields or parameters travelling together. → bundle them into one type.
- **Primitive Obsession**: a primitive or string standing in for a domain concept. → give the concept its own small type.
- **Repeated Switches**: the same `switch` or `if` cascade on the same type in several places. → polymorphism, or one shared map.
- **Shotgun Surgery**: one logical change forces scattered edits across many files. → gather what changes together.
- **Divergent Change**: one file edited for several unrelated reasons. → split so each changes for one reason.
- **Speculative Generality**: abstractions or hooks for needs the spec doesn't have. → delete and inline until a real need shows.
- **Message Chains**: long `a.b().c().d()` navigation. → hide the walk behind one method.
- **Middle Man**: a class that mostly delegates onward. → cut it, call the target directly.
- **Refused Bequest**: a subclass that ignores most of what it inherits. → use composition.

## 3. Run both axes in parallel

Start both sub-agents together, in the foreground. If your harness has no sub-agents, run the axes one after the other and keep their findings apart.

**Standards sub-agent** gets the diff command, the commit list, the paths of the standards sources and the smell baseline pasted in full. Brief: "Report per file and hunk: (a) every place the diff breaks a documented rule, citing the document and the rule; (b) any baseline smell, named, with the hunk quoted. Breaches of a documented rule can be hard violations; smells are always judgement calls, and a documented rule overrides a smell. Check the tests against the Testing section: rule sets designed from the acceptance criteria, CSV files, Detroit school. Skip what tooling enforces. Under 400 words."

**Spec sub-agent** gets the diff command, the commit list, the spec path and the issue's acceptance criteria. Brief: "Report: (a) acceptance criteria, behaviours or testing decisions that are missing or partial; (b) anything the diff does that the spec didn't ask for; (c) requirements that look implemented but wrongly; (d) places where the implementation note disagrees with the code. Quote the spec line for each finding. Under 400 words."

## 4. Check the Definition of Done

Check these yourself against the diff:

- **Item 4, documentation:** the spec's implementation note matches the current spec version; new ADRs and glossary terms are in place; README and API documentation are updated where the change affects them.
- **Item 5, follow-up:** no `TODO` or `FIXME` in the diff; deferred work is an issue.
- **Item 3, four-eyes:** the code review is the developer's, and this review helps them. For a `development` issue, check that it has two `Approved by:` labels. Flag any change that breaks or reshapes something outside the issue, so the developer can create an issue or tell the person responsible.
- **Item 6, time spent:** remind the developer to fill in **Time spent** on the issue before `deliver`.

Items 1 and 2 are covered by the Spec and Standards axes.

## 5. Report

Present the reports under `## Standards`, `## Spec` and `## Definition of Done`, verbatim or lightly cleaned. Keep the axes separate and unranked against each other: code can follow every rule and still build the wrong thing, or build the right thing against the rules. End with one line: the number of findings per axis and the worst finding within each.

## 6. Fix what the developer picks

1. Ask the developer which findings to fix. Their choice is final, and unpicked findings stay unfixed.
2. Fix them following the build rules in [mtogo-implement](../mtogo-implement/SKILL.md#2-build).
3. Update the implementation note as described in [Implementation notes](../mtogo-spec/SPEC-FORMAT.md#implementation-notes). If a fix changes the spec, bump the spec version first.
4. Run `gh insitu run fix-all`, then `gh tt wrapup -m "<type>(<scope>): <description>"`.

## 7. Finish

Tell the developer the round is done. Another round is a fresh session with `/mtogo-review`. When the developer is satisfied, they finish the issue themselves: ADRs accepted by the team, `git pull --rebase origin main`, the Definition of Done checked, then `gh tt deliver`.
