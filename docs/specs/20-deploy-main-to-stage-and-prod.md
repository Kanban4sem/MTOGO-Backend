# Deploy main to stage and prod

**Version:** 1.0 · **Date:** 2026-11-13 · **Issue:** #20 · **Feature flag:** None

## Acceptance criteria

Copied from #20. The issue is the source: change the issue first, then this copy.

- [ ] A commit on `main` is running on stage without any manual steps
- [ ] Prod is only deployed when someone starts it
- [ ] The deployed health check responds after each deployment
- [ ] A deployment can be rolled back to the previous image

## Behaviour

1. Every commit on `main` is built into a backend image tagged with its commit SHA and pushed to GHCR, also when its deployment is skipped. A commit whose image already exists is not built again.
2. A commit on `main` is deployed to stage after its image is pushed, without any manual step.
3. Two stage deployments never run at the same time. When several commits land while one is deploying, the ones in between may be skipped, and the newest is deployed last.
4. Prod is deployed when someone pushes a version tag (`1.2.3` or `v1.2.3`). The deployment uses the image already built for that commit, now also tagged with the version without the `v`.
5. A version tag on a commit that `main` never built fails before anything is deployed.
6. Someone can start the prod release by hand from `main` with a version. That version is deployed to prod, which is how prod is rolled back to an earlier version.
7. A manual start from any branch other than `main`, or with a version that doesn't match `v1.2.3` or `1.2.3`, or whose image doesn't exist, fails before anything is deployed.
8. Two prod deployments never run at the same time, whether they come from a tag or a manual start.
9. After each deployment, the workflow calls the public API of that environment from outside. The run fails if it doesn't answer.
10. When the new image can't be pulled, the server keeps running the previous version, and the previous tag stays in its configuration.
11. When the new version fails its health check, the server rolls back to the previous version on its own (unchanged from #11).
12. Stage is rolled back by re-running an earlier "Deploy to stage" run. The re-run deploys the image already built for that commit without building it again.

## Implementation decisions

- **ADR:** [ADR-003](../adr/003-deployment-pipeline.md) applies: public GHCR images, stage on every commit to `main`, prod from a version tag without a rebuild.
- **Server contract (from #11):** the deploy key can only run `deploy.sh`, which takes `<environment> <service> <tag>`. Stage takes a 40-character commit SHA, and prod takes a version such as `1.2.3`. The workflows use the organization secrets `DEPLOY_SSH_KEY`, `DROPLET_HOST` and `DROPLET_KNOWN_HOSTS`, connect as `deploy` with strict host key checking and never print the key.
- **Deploy to stage workflow:** runs on push to `main`, in two jobs.
  - **Build** has no concurrency group, so every commit gets its image. Permissions are read access to contents and write access to packages. It logs in to GHCR with the workflow token and stops early when `ghcr.io/kanban4sem/mtogo-backend:<sha>` already exists. Otherwise it sets up Java with the same actions and versions as the existing workflows, packages the jar without tests (the ready flow already tested the commit), builds the image with the OCI `source` label pointing at this repository so the package is linked to it, and pushes it.
  - **Deploy** needs Build and runs in the GitHub environment `stage`, with concurrency group `deploy-stage` without cancelling. GitHub keeps one waiting run per group and cancels the older one, which is accepted because the newest commit is deployed last. It deploys `stage backend <sha>`.
- **Release to prod workflow:** runs on a pushed version tag or a manual start with a `version` input, in the GitHub environment `prod`, with concurrency group `deploy-prod` for both triggers and without cancelling. It doesn't build.
  - On a tag, it checks that the image for the tagged commit exists, adds the version (without `v`) as a tag on that image and deploys `prod backend <version>`.
  - On a manual start, it first fails unless the run was started from `main`, before it checks out code or uses the secrets, because the deploy action is loaded from the chosen branch. It then checks the input against `^v?[0-9]+\.[0-9]+\.[0-9]+$` and removes the `v` before it calls GHCR or the server, checks that the version's image exists and deploys it.
- **Check from outside:** the last step of both workflows calls `GET /v1/features` on the environment's public host, with retries (`curl -fsS --retry 5 --retry-delay 3 --retry-all-errors`). `/actuator/health` isn't reachable from outside, because Caddy only forwards `/v1/*` to the backend. The container's own health check still decides whether `deploy.sh` keeps the new version.
- **`deploy.sh`:** when the pull fails, it puts the previous tag back, writes `failed, could not pull image` to `deploy.log` and exits with an error.
- **README:** a section on deployment: what deploys stage, how to release to prod, how to roll back each environment, and that each GHCR package must be made public once by hand.
- **Frontend:** the frontend's workflows are Kanban4sem/MTOGO-Frontend#13, which links to ADR-003.

## Testing decisions

No Java code changes, so there are no rule sets. `prettier` and `cspell` check the workflow files in trunk-worthy. Everything else is checked by hand on the real stage and prod, because the workflows only run on `main`.

Before deliver:

- Install the new `deploy.sh` on the server with `sudo install`, as in #11. The first stage deployment runs as soon as the delivery lands on `main`, and it must use the fixed script. The command line it accepts is unchanged.
- Call it with an image that doesn't exist: `sudo -u deploy env SSH_ORIGINAL_COMMAND="stage backend 0000000000000000000000000000000000000000" /opt/mtogo/deploy.sh`. It must fail, `.env` must still have the previous tag, and `deploy.log` must have `failed, could not pull image`. (Criterion 4, behaviour 10)

Right after deliver, in this order:

1. **First stage deployment:** "Deploy to stage" starts by itself. It fails at the pull, because GHCR creates the package as private. (Criterion 1, behaviour 1–2)
2. **Make the package public** in GHCR, then re-run the run. It must succeed, and the check from outside must pass. (Criterion 1 and 3, behaviour 9)
3. **Stage rollback:** after the next delivery to `main`, re-run the earlier "Deploy to stage" run. Its build job must skip the build because the image exists, `deploy.log` must show the older SHA, and `/v1/features` must answer. (Criterion 4, behaviour 12)
4. **Release 0.1.0 and 0.1.1** with `gh tt semver bump` and `git push origin --tags`. Each "Release to prod" run must succeed, and `https://mtogo.saldic.dev/v1/features` must answer. No stage push deploys prod. (Criterion 2 and 3, behaviour 4)
5. **Prod rollback:** start "Release to prod" by hand with `0.1.0`. `deploy.log` must show `prod backend 0.1.0 ok`. Also start it once with an invalid version, such as `1.2`, and once from a branch other than `main`. Both must fail before deploying. (Criterion 4, behaviour 6–7)

The issue closes automatically when the delivery lands on `main`, so these checks happen after it closes. The results (links to the runs and the lines from `deploy.log`) are written as a comment on #20 and in the implementation note. A check that fails becomes a new bug issue, which doesn't need approval from two people but must still meet the Definition of Done.

## Out of scope

- The frontend's workflows (Kanban4sem/MTOGO-Frontend#13).
- Database migrations that can't be rolled back. A rollback only switches the image, not the database.
- Making the GHCR package public and installing `deploy.sh` on the server are manual steps, not automated.

## Further notes

- ADR-003 must be `Accepted` by the group before deliver.
- Stage rollback needs a second commit on `main` built by the workflow, so step 3 waits for the next delivery.
- Prod rollback needs at least two released versions. 0.1.0 and 0.1.1 can point at the same commit, which proves the mechanism but not a change of code.

## Implementation notes

<!-- Written by /mtogo-implement and /mtogo-review. -->

### Spec 1.0 - 2026-11-13 - #20

- **Built:** "Deploy to stage" has two jobs. Build runs for every commit on `main` outside any concurrency group, and pushes the image unless one already exists for the commit, so a re-run deploys the existing image. Deploy runs one at a time in `deploy-stage`, and GitHub may skip waiting deployments in favour of the newest. "Release to prod" runs on a version tag or by hand from `main` with a version: it refuses a manual start from any other branch before checkout, checks the version, tags or finds the image and deploys it without a rebuild. A shared deploy action holds the SSH call to `deploy.sh` and the check from outside, so both workflows deploy the same way. It removes the deploy key right after the SSH step, before the check from outside. `deploy.sh` puts the previous tag back and logs `failed, could not pull image` when the pull fails. The README describes deploying, releasing, rolling back and making a new package public.
- **Deviations:** Additions the spec didn't ask for: each workflow's GitHub environment links to its public URL, the version rule also runs on tag pushes, and `actionlint` and `shellcheck` were added to the repository dictionary. The workflow files follow the existing `on_<trigger>` naming.
- **Tests:** No Java changes. actionlint 1.7.7 passes on the workflows after the review fixes and shellcheck on `deploy.sh`. `deploy.sh` was run in a Linux container with a fake `docker`: a failed pull exits 1, keeps the previous tag in `.env` and logs `failed, could not pull image`, and a successful pull logs `ok`. The version rule accepts `1.2.3`, `v1.2.3` and `0.1.0`, and rejects `1.2`, `1.2.3.4`, `V1.2.3`, `1.2.3-rc1`, an empty value and `1.2.3;x`. The manual checks on the server and after delivery under Testing decisions are still to do, and their results go here and in a comment on #20.
- **Follow-up:** None.

## Changelog

| Version | Date       | Change        | Issue |
| ------- | ---------- | ------------- | ----- |
| 1.0     | 2026-11-13 | First version | #20   |
