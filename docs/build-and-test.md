# Build and Test

How to build the backend, run the tests and run the code quality analysis. How tests are written is described in the [coding standards](coding-standards.md#testing).

## Build and test

```bash
./mvnw verify
```

`verify` also writes a JaCoCo coverage report to `target/site/jacoco/`. Open `index.html` in a browser to see which code the tests cover. SonarQube reads `jacoco.xml`.

The tests start PostgreSQL with Testcontainers, so Docker must be running.

To run one test class:

```bash
./mvnw -q test -Dtest=<TestClass>
```

## Test JVMs

By default the tests run in one JVM, which starts one PostgreSQL container. Each extra JVM starts its own Spring contexts and its own PostgreSQL container, so more JVMs finish faster but need more memory and cores. A higher value pays off on a machine with cores and memory to spare: try 2, then 4, and keep the value that makes `./mvnw verify` faster without slowing the machine down. Stay at 1 on a machine with little memory or a small Docker Desktop VM.

Set the number with the `MTOGO_TEST_FORKS` environment variable:

- **For every run, kept across rebuilds:** set it on your own machine, restart VS Code and rebuild the dev container.
  - Windows (PowerShell): `setx MTOGO_TEST_FORKS 4`
  - macOS (zsh): add `export MTOGO_TEST_FORKS=4` to `~/.zshrc`
- **Until the container restarts, without a rebuild:** run `export MTOGO_TEST_FORKS=4` in the dev container terminal.

For a single run, `./mvnw verify -Dsurefire.forkCount=4` overrides the variable. The ready flow on GitHub always uses 4.

## Code quality

[SonarQube Cloud](https://sonarcloud.io/project/overview?id=Kanban4sem_MTOGO-Backend) analyses the code with the JaCoCo coverage from the tests:

- The [ready flow](../CONTRIBUTING.md#quality-gates) analyses each delivery after `trunk-worthy` and waits for the quality gate. It measures the delivery's new code against `main` with `sonar.newCode.referenceBranch=main`. A delivery whose new code fails the "Sonar way" gate doesn't reach `main`.
- Each commit on `main` is built, tested and analysed beside the stage deployment, so `main` stays the reference for new code. The analysis never holds back a deployment.

The SonarQube Cloud project is set up by hand:

- Automatic analysis is off, since it can't read the coverage and it blocks the CI analysis.
- The quality gate is the built-in "Sonar way".
- New code stays at the project default "Previous version". SonarQube Cloud has no reference branch option at project level, so the ready flow sets it for each delivery instead.
- The workflows read a token from the repository secret `SONAR_TOKEN`, created in SonarQube Cloud with permission to analyse the project.

The pre-commit hook and `trunk-worthy` don't run the analysis, so they work without a token. To run it yourself on your issue branch, create a personal token under My Account → Access tokens in SonarQube Cloud and run:

```bash
printf 'Sonar token: '; read -rs SONAR_TOKEN; echo
export SONAR_TOKEN="$(printf '%s' "$SONAR_TOKEN" | tr -d '[:space:]')"
./mvnw -B clean verify sonar:sonar -Dsonar.branch.name=$(git branch --show-current) -Dsonar.newCode.referenceBranch=main
unset SONAR_TOKEN
```

- `read -rs` keeps the token off the screen and out of your shell history.
- `tr` strips stray whitespace from a pasted token. A leading space makes the scanner fail with "invalid header value".
- Without `sonar.branch.name`, a local run is registered as `main`. The scanner detects the branch by itself only in GitHub Actions.
- `clean` keeps stale files in `target/` out of the analysis.
- The tests run in one JVM by default. With [`MTOGO_TEST_FORKS`](#test-jvms) above 1, each JVM starts its own PostgreSQL container, and Docker Desktop on your own machine needs about 6 GB of memory. With less, the build is killed with exit code 137.
- Use your personal token and never share it. CI uses its own token from the `SONAR_TOKEN` secret.

The analysis shows up in SonarQube Cloud under the branch you are on.
