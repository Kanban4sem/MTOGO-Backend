# Deployment

Stage (`https://stage.mtogo.saldic.dev`) and prod (`https://mtogo.saldic.dev`) run on one droplet. The decisions behind the pipeline are in [ADR-003](adr/003-deployment-pipeline.md), and the server side is in `deploy/`.

## Stage

Every commit that reaches `main` is deployed to stage by the **Deploy to stage** workflow. It builds the image, pushes it to GHCR as `ghcr.io/kanban4sem/mtogo-backend:<commit SHA>` and deploys it. Nothing has to be done by hand. Every commit gets its image, but only one stage deployment runs at a time. When several commits land while one is deploying, GitHub skips the ones in between and deploys the newest.

## Prod

Prod is deployed only when someone releases a version:

```bash
gh tt semver bump --patch   # or --minor, --major
git push origin --tags
```

The **Release to prod** workflow adds the version as a tag on the image `main` already built for that commit and deploys it. It doesn't rebuild. A tag on a commit that `main` hasn't built yet fails, so wait until **Deploy to stage** has finished for that commit.

## Health check

`deploy.sh` waits for the container's own health check and rolls back to the previous version on its own when it fails. When the new image can't be pulled, the previous version keeps running. After each deployment the workflow also calls `GET /v1/features` on the public host, and the run fails if it doesn't answer.

## Roll back

- **Stage:** open an earlier **Deploy to stage** run in the Actions tab and choose **Re-run all jobs**. The run deploys the image already built for that commit instead of building it again. GitHub keeps runs for 30 days.
- **Prod:** start **Release to prod** by hand (**Run workflow**) from `main` with the version to go back to, such as `1.2.3`. A manual start from any other branch fails before it deploys.

A rollback only switches the image. It doesn't undo database migrations.

## First deployment of a new package

GHCR creates a package as private on its first push, and the droplet can't pull a private image. Make the package public once under the organization's **Packages** → **Package settings** → **Change visibility**, then re-run the failed run.
