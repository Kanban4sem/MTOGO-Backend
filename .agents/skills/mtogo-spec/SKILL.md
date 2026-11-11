---
name: mtogo-spec
description: Turn the grilled MTOGO issue into its one spec file, save it with wrapup and link it from the issue. Session 1, step 2.
disable-model-invocation: true
---

# MTOGO spec

Synthesise the conversation into the issue's spec. This is session 1, step 2, right after `/mtogo-grill`. The grilling is done, so write from what was agreed rather than interviewing the developer.

## 1. Decide: new or amended

- **New:** this issue's behaviour has no spec yet. Create `docs/specs/<issue>-<slug>.md`.
- **Amended:** the issue changes behaviour an existing spec describes. Bump that spec's version as described in [SPEC-FORMAT.md](SPEC-FORMAT.md#versioning).

## 2. Confirm the seams

List the test seams and rule sets from the grilling, each with a one-line note on what it catches. Prefer existing seams and the highest seam possible. Confirm the list with the developer before writing.

## 3. Write the spec

Write the spec using the template and section rules in [SPEC-FORMAT.md](SPEC-FORMAT.md). Use the glossary's terms throughout. Every acceptance criterion in the issue appears in the spec and is covered by at least one behaviour and one testing decision. Fill in the feature-flag field from the developer's answer during grilling, and ask now if it wasn't settled.

## 4. Save and link

1. Run `gh insitu run fix-all`.
2. Save the spec together with the ADRs and glossary changes from the grilling: `gh tt wrapup -m "docs: add spec for <short title>"`, or `"docs: amend spec for <short title>"`.
3. Comment the link on the issue: `gh issue comment <issue> --body "Spec: <url>"`, using the file's URL on `main` (`https://github.com/<owner>/<repo>/blob/main/docs/specs/<file>`). The link resolves once the issue is delivered.

## 5. Finish

Tell the developer that session 1 is done and the next step is a fresh session: `/mtogo-implement docs/specs/<file>`.
