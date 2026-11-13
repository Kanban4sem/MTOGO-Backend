# ADR-003: Deployment Pipeline

## Status

Accepted in #20

## Context

Stage and prod run on one droplet behind Caddy (#11). The droplet only accepts a deployment through `deploy.sh`, which takes a commit SHA for stage and a version number for prod. We need to decide where images live, what reaches stage and prod, and how a deployment is rolled back. Both repositories are public.

## Decision

- This decision applies to both the backend and the frontend. The frontend links here instead of keeping its own copy.
- Images are pushed to GitHub Container Registry (GHCR) as `ghcr.io/kanban4sem/mtogo-backend` and `ghcr.io/kanban4sem/mtogo-frontend`. Both packages are public, so the droplet pulls without a registry login.
- Every commit on `main` is built once, tagged with its commit SHA and deployed to stage automatically.
- Prod is deployed only when someone pushes a semantic version tag. The release does not rebuild: it adds the version as a tag to the image already built for that commit and deploys that version.
- Rollback: `deploy.sh` rolls back on its own when the health check fails. To go back to an earlier version on purpose, someone starts the prod release manually with that version, or re-runs an earlier stage deployment.

## Alternatives

- **Private GHCR packages:** The droplet would need a token with read access to packages, and the code is public anyway.
- **Docker Hub:** One more account and secret, with pull limits, and no link to the repository's permissions.
- **Rebuild for prod:** Prod would run an image that was never tested on stage.
- **Deploy prod automatically from `main`:** The issue requires that prod is deployed only when someone starts it.

## Consequences

- Anyone can pull the images, so they must never contain secrets. Configuration comes from the environment files on the droplet.
- A version tag can only be released when `main` has built that commit.
- Each GHCR package is created private on its first push and must be made public once by hand.
- Prod can only be rolled back once at least two versions have been released. Before that there is nothing to go back to.
- Re-running a workflow is only possible for 30 days, so prod rollback uses a manual start with a version instead.
