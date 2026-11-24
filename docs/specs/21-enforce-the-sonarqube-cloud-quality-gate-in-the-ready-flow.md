# Enforce the SonarQube Cloud quality gate in the ready flow

**Version:** 1.0 · **Date:** 2026-11-24 · **Issue:** #21 · **Feature flag:** None

## Acceptance criteria

Copied from #21. The issue is the source: change the issue first, then this copy.

- [ ] The ready flow sends the JaCoCo coverage to SonarQube Cloud, and the project shows non-zero coverage
- [ ] A delivery whose new code fails the "Sonar way" gate doesn't reach `main`
- [ ] A delivery that passes the gate reaches `main` as before
- [ ] Every commit on `main` is analysed, so new code is measured against `main`
- [ ] Automatic analysis is off, and the setup is described in the README
- [ ] CONTRIBUTING names the gate the ready flow enforces

## Behaviour

1. The ready flow analyses each delivery in SonarQube Cloud after `trunk-worthy` has passed, using the JaCoCo report that `trunk-worthy` wrote.
2. The ready flow waits for the quality gate result before it finishes the build job.
3. A delivery whose new code fails the "Sonar way" gate fails the build job, so it is not merged to `main`.
4. A delivery whose new code passes the gate is merged to `main` as before.
5. A delivery fails the build job when SonarQube Cloud can't be reached, the token is missing or the gate result doesn't arrive in time. A missing result never counts as a pass.
6. The new code of a delivery is measured against `main`, the reference branch.
7. A delivery with fewer than 20 new lines is gated on issues and duplication only, because SonarQube Cloud ignores the coverage condition below that size.
8. Every commit on `main` is built, tested and analysed in SonarQube Cloud, so the reference branch is always up to date.
9. The analysis of `main` never blocks or delays the stage deployment, and a failed analysis of `main` doesn't stop a deployment.
10. Code that Lombok generates is not counted as uncovered.
11. SonarQube Cloud's automatic analysis is off, so only the CI analysis reports to the project.
12. The pre-commit hook and `trunk-worthy` don't run the analysis, so they stay local, fast and work without a token.

## Implementation decisions

- **No ADR:** this enforces a gate CONTRIBUTING already names. The issue's Discussion records why a JaCoCo check rule in the pom was rejected.
- **SonarQube Cloud (manual setup):** automatic analysis is turned off, the project uses the built-in "Sonar way" gate, and the project's new code definition stays "Previous version". SonarQube Cloud has no reference branch option at project level, so the ready flow sets the reference branch for each analysis instead. The thresholds stay in SonarQube Cloud, as CONTRIBUTING says.
- **Secret:** a repository secret `SONAR_TOKEN`, created in SonarQube Cloud with permission to analyse the project. The workflows pass it as an environment variable and never print it.
- **`pom.xml`:** adds `sonar-maven-plugin` with a pinned version, and the properties `sonar.organization` and `sonar.projectKey` taken from the existing SonarQube Cloud project, plus `sonar.host.url` for SonarQube Cloud. The JaCoCo plugin is unchanged. Sonar finds `target/site/jacoco/jacoco.xml` on its own, or through `sonar.coverage.jacoco.xmlReportPaths` if it doesn't.
- **`lombok.config`:** at the repository root with `config.stopBubbling = true` and `lombok.addLombokGeneratedAnnotation = true`. JaCoCo then leaves out generated methods.
- **Ready flow (`on_ready.yml`):** in the existing `build` job, after the step that runs `trunk-worthy`, a step runs `./mvnw -B sonar:sonar -Dsonar.qualitygate.wait=true -Dsonar.newCode.referenceBranch=main` with `SONAR_TOKEN`. The reference branch is passed only here, not in the pom, because `on_main.yml` analyses `main` itself. It reuses the compiled classes and the JaCoCo report from `trunk-worthy`, so it doesn't build or test again. A failed gate fails the step, and `merge-to-trunk` doesn't run because it needs `build`. The checkout uses `fetch-depth: 0`, because Sonar needs the history to find new code against `main` and to blame lines. The branch name is detected by the scanner from GitHub Actions. The TakT action `ready-to-trunk` in `merge-to-trunk` is pinned to a commit SHA, with the tag as a comment, because it receives the `READY_PUSHER` token and SonarQube Cloud reports an action pinned by tag as a security issue. Updating TakT means updating the SHA by hand.
- **Main (`on_main.yml`):** a new job `analyse` beside `build` and `deploy`, with no `needs` either way and only read access to contents. It checks out with `fetch-depth: 0`, sets up Java with the same action and version as the other jobs and runs `./mvnw -B verify sonar:sonar` without `sonar.qualitygate.wait`. It runs the tests, which the image build skips, because the report must come from this commit. The job has the concurrency group `sonar-main` without cancelling a running analysis, so analyses of `main` arrive in commit order.
- **README:** a section "Code quality" after "Build and test": SonarQube Cloud analyses each delivery in the ready flow and each commit on `main`, the gate is "Sonar way", automatic analysis is off, `SONAR_TOKEN` is a repository secret, the project's new code stays "Previous version" while the ready flow measures new code against `main`, and how to run the analysis locally with your own token. The local command passes `sonar.branch.name` with the current branch, because the scanner detects the branch by itself only in GitHub Actions and a local run would otherwise be recorded as `main`.
- **CONTRIBUTING:** the Quality gates table names the "Sonar way" gate in SonarQube Cloud as the gate the ready flow enforces, notes that `main` is analysed after each merge, and that changes under 20 new lines aren't gated on coverage. The 20-line rule and the analysis of `main` change what a delivery is held to, so the version is bumped as a major change. CONTRIBUTING is shared, and the frontend's analysis is Kanban4sem/MTOGO-Frontend#58.
- **Definition of Done:** unchanged. It already says the SonarQube quality gate must pass before a commit reaches `main`.

## Testing decisions

No Java behaviour changes, so there are no seams or rule sets. `prettier`, `markdownlint` and `cspell` check the workflow and Markdown files in `trunk-worthy`, and `./mvnw verify` checks that the pom still builds and that the tests pass with `lombok.config`. Everything else is checked by hand on the real SonarQube Cloud project and pipeline, because the workflows only run on `ready/*` and `main`.

Before deliver:

1. **SonarQube Cloud setup:** turn off automatic analysis, check that new code is "Previous version" and the project uses "Sonar way", and add `SONAR_TOKEN` as a repository secret. (Criterion 5, behaviour 11)
2. **Local analysis:** on the issue branch, never on `main`, run `./mvnw -B clean verify sonar:sonar -Dsonar.branch.name=$(git branch --show-current) -Dsonar.newCode.referenceBranch=main` with your own token. The analysis must be recorded under the issue branch in SonarQube Cloud, not under `main`, and its quality gate result must be visible there. Coverage on new code isn't required here, since a branch that changes no covered Java code has too few lines to compute it. Non-zero coverage is checked on `main` in after-deliver check 2. (Behaviour 1)

After deliver, in this order:

1. **Passing delivery:** this issue's own delivery must pass the gate in the ready flow, with the analysis step visible in the `build` job, and reach `main`. The analysis must show new code measured against the reference branch `main`. (Criterion 2 and 3, behaviour 2, 4 and 6)
2. **Main analysed:** the `on_main.yml` run for that commit must run `analyse` beside `deploy`, and SonarQube Cloud must show an analysis of `main` with non-zero coverage, and Lombok-generated methods must not appear as uncovered. The Lombok check is done on `main`, because the branch view shows only changed files. (Criterion 1 and 4, behaviour 8, 9 and 10)
3. **Failing delivery:** on a throwaway branch, add 20 or more lines of code that no test covers and deliver it. The ready flow's `build` job must fail at the gate, `merge-to-trunk` must be skipped and `main` must be unchanged. Then delete the `ready/*` branch and the throwaway issue branch, and close any throwaway issue. (Criterion 2, behaviour 3)

The results (links to the runs and the SonarQube Cloud analyses) are written as a comment on #21 and in the implementation note. A check that fails becomes a new bug issue.

## Out of scope

- The frontend's analysis (Kanban4sem/MTOGO-Frontend#58).
- A custom quality gate or changed thresholds. "Sonar way" is used as it is.
- A JaCoCo check rule or any coverage threshold in the pom.
- Running the analysis in `.insitu.yml`, the pre-commit hook or the wrapup workflow on issue branches.
- A separate required status check for the gate. It is part of the ready flow job the ruleset already requires, which answers #75.

## Further notes

- CI analysis fails while automatic analysis is on, so automatic analysis must be turned off before the first CI or local analysis.
- Until `main` has been analysed by CI once, the reference branch has no CI analysis with coverage. The first delivery is compared with the automatic analysis of `main` from when the project was imported, which has no coverage data and no quality gate status.
- Re-running an `on_main.yml` run, for example to roll stage back, also re-runs `analyse`. SonarQube Cloud then records the older commit as the latest analysis of `main` until the next merge.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.0 - 2026-11-24 - #21

- **Built:** the ready flow's `build` job checks out the full history and, after `trunk-worthy`, runs the SonarQube Cloud analysis and waits for the "Sonar way" gate. It passes `sonar.newCode.referenceBranch=main`, because SonarQube Cloud has no reference branch option at project level, and the project's new code stays "Previous version". The step fails at once when `SONAR_TOKEN` is missing, and the scanner fails it when the gate fails or its result doesn't arrive. `on_main.yml` has an `analyse` job beside `build` and `deploy` that builds, tests and analyses each commit on `main` without waiting for the gate, one at a time in the concurrency group `sonar-main`. The pom pins `sonar-maven-plugin` 5.9.0.7382 and names the organisation `kanban4sem` and the project `Kanban4sem_MTOGO-Backend`, both read from the public SonarQube Cloud API. `lombok.config` marks Lombok's methods as generated. The README has a "Code quality" section, with the reference branch in the local command too, and CONTRIBUTING 8.0 names the gate, the analysis of `main` and the 20-line rule.
- **Deviations:** The ready flow step checks for the token itself, so a missing secret gives a clear message instead of a scanner error. The TakT action `ready-to-trunk` in the ready flow is pinned to commit `d354e40` (`v1`) instead of the tag, because the gate failed on the unpinned action. It receives the `READY_PUSHER` token, so updating TakT now means updating the SHA by hand. The SonarQube Cloud organisation `kanban4sem` on the Free plan was deleted and created again on the OSS plan, because the Free plan analyses only `main`, the ready flow needs branch analysis and the plan can only be chosen when an organisation is created. The organisation key and the project key `Kanban4sem_MTOGO-Backend` are unchanged. The frontend project in the new organisation keeps automatic analysis for now.
- **Tests:** `./mvnw verify` passes with `lombok.config`, the compiled classes carry `@lombok.Generated` and the JaCoCo report is written to `target/site/jacoco/jacoco.xml`. `cspell`, `markdownlint` and `prettier` pass. SonarQube Cloud setup (check 1): automatic analysis is off, the gate is "Sonar way", new code stays "Previous version", and `SONAR_TOKEN` is set as a repository secret and validates. Local analysis (check 2): the first run, without `sonar.branch.name`, was recorded as `main` (gate Passed, coverage 96.2%, duplications 0.0%), which is why the local command and check 2 now pass it. That run was in the deleted organisation, so it is gone. In the new organisation, `main` has one analysis, from automatic analysis when the project was imported before it was turned off: 2,392 lines in `src`, one security issue (the CSRF finding), no coverage data and no quality gate status. "Previous version" on `main` uses the pom version, which is always `1.0-SNAPSHOT`, so the new code period of `main` effectively starts at its first analysis. The rerun on the issue branch passed 398 tests, wrote the JaCoCo report and uploaded the analysis to the branch dashboard. Its gate failed on one new security issue, the TakT action pinned by tag. The rerun after the SHA pin, recorded under the issue branch and not `main`, passed the gate: 0 new issues, 0 accepted issues and 0.0% duplications on 210 new lines. Coverage on new code wasn't computed, because the branch changes no covered Java code. Check 2 is done. Whether Lombok-generated methods appear as uncovered isn't checked yet: the branch view shows only changed files, so it moved to after-deliver check 2 on `main`. The passing delivery, the analysis of `main` and the failing delivery after deliver are still open. SonarQube Cloud also reports a CSRF finding in `SecurityConfig`. It is outside this branch's new code, so it doesn't affect this gate.
- **Follow-up:** #86 evaluates the CSRF finding in `SecurityConfig`.

## Changelog

| Version | Date       | Change        | Issue |
| ------- | ---------- | ------------- | ----- |
| 1.0     | 2026-11-24 | First version | #21   |
