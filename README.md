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

After about a minute `docker ps` shows the container as `healthy`. Docker checks `GET /actuator/health`, the only endpoint that is open without authentication.

## How we work

- [CONTRIBUTING](CONTRIBUTING.md): issues, workflow, commit messages, quality gates and ADRs
- [Coding standards](docs/coding-standards.md) and [Definition of Done](docs/definition-of-done.md)
- [AI workflow](docs/ai-workflow.md) and [`AGENTS.md`](AGENTS.md): how we work with AI agents
- [Minimum viable product](docs/minimum-viable-product.md): what the first release contains and the order status lifecycle
