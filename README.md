# MTOGO-Backend

The REST API for MTOGO, a food delivery platform. Built with Spring Boot 4 and Java 25.

We work with Kanban and trunk-based development using [TakT](https://github.com/devx-cafe/gh-tt) and [InSitu](https://github.com/devx-cafe/gh-insitu). Every change starts as an issue on our [board](https://github.com/orgs/Kanban4sem/projects/1) and ends as exactly one commit on `main`.

## Getting started

### 1. Install on your own machine (once)

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows: with WSL2)
- [VS Code](https://code.visualstudio.com/) with the **Dev Containers** extension
- [GitHub CLI](https://cli.github.com/) (`brew install gh` on Mac, `winget install GitHub.cli` on Windows)

### 2. Log in to GitHub with project access (once)

```bash
gh auth login -h github.com -p https -s project -w
```

Then let the dev container inherit your login. On Mac add this line to `~/.zprofile` (on Linux/WSL: `~/.profile`). Use the path that `which gh` prints:

```bash
export GH_TOKEN=$(/opt/homebrew/bin/gh auth token)
```

On Windows, run this once in PowerShell instead:

```powershell
[Environment]::SetEnvironmentVariable("GH_TOKEN", (gh auth token), "User")
```

Restart your terminal and VS Code afterwards.

### 3. Open the repository in the dev container

1. Clone the repository and open the folder in VS Code.
2. Press `Cmd+Shift+P` (Windows: `Ctrl+Shift+P`) and choose **Dev Containers: Reopen in Container**.
3. The first build takes a few minutes. It installs Java 25, Node, the GitHub CLI, TakT, InSitu and our linters.

All commands below are run in the terminal **inside** the dev container.

### 4. Check that everything works

```bash
gh auth status                # must show the 'project' scope
gh insitu run trunk-worthy    # all checks must be green
```

If git asks who you are, set your name and email once:

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

## Daily workflow

| Step    | Command                                                  | What happens                                                                        |
| ------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| Start   | `gh tt workon -i <issue>` or `gh tt workon -t "<title>"` | Creates or reuses the issue branch and moves the issue to **In Progress**           |
| Save    | `gh tt wrapup -m "<message>"`                            | Runs the quality checks, commits and pushes. Use it as often as you like            |
| Sync    | `git pull --rebase origin main`                          | Needed before delivering if `main` has moved                                        |
| Deliver | `gh tt deliver`                                          | Squashes your work into one commit. CI verifies it and fast-forwards it onto `main` |

Rules:

- Never push directly to `main`.
- Fix formatting automatically with `gh insitu run fix-all`.
- Unknown but correct words for the spell checker go in `.dict/repo.dictionary`.

## Configuration

Configuration lives in `src/main/resources/application.properties` and contains no secrets. Copy `.env.example` to `.env` and fill in real values. The `.env` file is never committed.

## Build and test

```bash
./mvnw verify
```

`verify` also writes a JaCoCo coverage report to `target/site/jacoco/`. Open `index.html` in a browser to see which code the tests cover. SonarQube reads `jacoco.xml`.

The tests start PostgreSQL with Testcontainers, so Docker must be running.

## Docker image

Build the jar first, then the image:

```bash
./mvnw package
docker build -t mtogo-backend .
```

Run it against your local PostgreSQL. Inside a container, `localhost` is the container itself, so use `host.docker.internal` to reach the database on your machine:

```bash
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/mtogo \
  -e DB_USER=mtogo -e DB_PASSWORD=<your password> \
  mtogo-backend
```

After about a minute `docker ps` shows the container as `healthy`. Docker checks `GET /actuator/health`, which is open without authentication.

## Deployment

Stage (`https://stage.mtogo.saldic.dev`) and prod (`https://mtogo.saldic.dev`) run on one droplet. The decisions behind the pipeline are in [ADR-003](docs/adr/003-deployment-pipeline.md), and the server side is in `deploy/`.

### Stage

Every commit that reaches `main` is deployed to stage by the **Deploy to stage** workflow. It builds the image, pushes it to GHCR as `ghcr.io/kanban4sem/mtogo-backend:<commit SHA>` and deploys it. Nothing has to be done by hand. Every commit gets its image, but only one stage deployment runs at a time. When several commits land while one is deploying, GitHub skips the ones in between and deploys the newest.

### Prod

Prod is deployed only when someone releases a version:

```bash
gh tt semver bump --patch   # or --minor, --major
git push origin --tags
```

The **Release to prod** workflow adds the version as a tag on the image `main` already built for that commit and deploys it. It doesn't rebuild. A tag on a commit that `main` hasn't built yet fails, so wait until **Deploy to stage** has finished for that commit.

### Health check

`deploy.sh` waits for the container's own health check and rolls back to the previous version on its own when it fails. When the new image can't be pulled, the previous version keeps running. After each deployment the workflow also calls `GET /v1/features` on the public host, and the run fails if it doesn't answer.

### Roll back

- **Stage:** open an earlier **Deploy to stage** run in the Actions tab and choose **Re-run all jobs**. The run deploys the image already built for that commit instead of building it again. GitHub keeps runs for 30 days.
- **Prod:** start **Release to prod** by hand (**Run workflow**) from `main` with the version to go back to, such as `1.2.3`. A manual start from any other branch fails before it deploys.

A rollback only switches the image. It doesn't undo database migrations.

### First deployment of a new package

GHCR creates a package as private on its first push, and the droplet can't pull a private image. Make the package public once under the organization's **Packages** → **Package settings** → **Change visibility**, then re-run the failed run.

## API documentation

With the backend running, Swagger UI is at `/swagger-ui/index.html` and the OpenAPI specification at `/v3/api-docs`. Both are open without authentication, like `GET /actuator/health` and `GET /v1/features`. Every other endpoint requires a login.

## How we work

- [CONTRIBUTING](CONTRIBUTING.md): issues, workflow, commit messages, quality gates and ADRs
- [Coding standards](docs/coding-standards.md) and [Definition of Done](docs/definition-of-done.md)
- [AI workflow](docs/ai-workflow.md) and [`AGENTS.md`](AGENTS.md): how we work with AI agents
- [Minimum viable product](docs/minimum-viable-product.md): what the first release contains and the order status lifecycle
