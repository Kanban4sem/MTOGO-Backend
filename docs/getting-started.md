# Getting Started

How to set up your machine and the dev container to work on MTOGO-Backend. How we pick up issues, commit and deliver is described in [CONTRIBUTING](../CONTRIBUTING.md#workflow).

## 1. Install on your own machine (once)

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows: with WSL2)
- [VS Code](https://code.visualstudio.com/) with the **Dev Containers** extension
- [GitHub CLI](https://cli.github.com/) (`brew install gh` on Mac, `winget install GitHub.cli` on Windows)

## 2. Log in to GitHub with project access (once)

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

## 3. Open the repository in the dev container

1. Clone the repository and open the folder in VS Code.
2. Press `Cmd+Shift+P` (Windows: `Ctrl+Shift+P`) and choose **Dev Containers: Reopen in Container**.
3. The first build takes a few minutes. It installs Java 25, Node, the GitHub CLI, TakT, InSitu, our linters, and Claude Code and Codex as extensions and on the command line. It also checks `.claude/skills` out as a symlink so Claude Code finds the repository's skills, also in a clone made on Windows. If your dev container already exists, rebuild it.
4. Log in to the AI agents once: run `claude` and `codex` in the terminal and follow the prompts. Your login lives in the Docker volumes `mtogo-claude` and `mtogo-codex`, so it survives rebuilds and also works in the MTOGO-Frontend dev container. The first rebuild that adds these volumes starts logged out.

   Both agents are installed by default. If you don't use one, set `MTOGO_SKIP_CLAUDE=1` or `MTOGO_SKIP_CODEX=1` as an environment variable on your own machine, restart VS Code and rebuild the dev container. The container then skips that command-line install. The VS Code extensions stay installed. Disable them for yourself in VS Code.

Before VS Code connects, the dev container installs the linting tools and puts the Danish cSpell dictionary in the repository's `node_modules`. GitHub Actions installs the same dictionary from `package-lock.json`.

All commands in the documentation are run in the terminal **inside** the dev container.

## 4. Check that everything works

```bash
gh auth status                # must show the 'project' scope
gh insitu run trunk-worthy    # all checks must be green
```

If git asks who you are, set your name and email once:

```bash
git config --global user.name "Your Name"
git config --global user.email "you@example.com"
```

## Next steps

- [CONTRIBUTING](../CONTRIBUTING.md): issues, the TakT workflow (`workon`, `wrapup`, `deliver`), commit messages and quality gates
- [Local development](local-development.md): configure and run the backend against your own database
- [Build and test](build-and-test.md): run the tests and the code quality analysis
