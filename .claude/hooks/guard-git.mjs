// PreToolUse hook: the developer owns every commit, so the agent saves work only
// through `gh tt wrapup` (see AGENTS.md). Exit code 2 blocks the command and
// shows the message to the agent.
import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";

const input = JSON.parse(readFileSync(0, "utf8"));
const command = input.tool_input?.command ?? "";

// git options that may come before the subcommand, such as `git -C dir` or `git -c key=value`
const GIT = String.raw`\bgit(?:\s+(?:-[Cc]\s+\S+|--?[\w-]+(?:=\S+)?))*\s+`;

const alwaysBlocked = [
  [
    new RegExp(GIT + String.raw`(commit|push)\b`),
    'Save work with `gh tt wrapup -m "<message>"`, which commits as the developer.',
  ],
  [
    /\bgh\s+(?:tt\s+)?deliver\b/,
    "`gh tt deliver` is run by the developer after checking the Definition of Done.",
  ],
  [
    new RegExp(GIT + String.raw`.*--author\b`),
    "Commits are authored by the developer, never with `--author`.",
  ],
  [
    new RegExp(
      GIT +
        String.raw`(branch\s+(-f|-D|--force)\s+main|update-ref\s+refs/heads/main)\b`,
    ),
    "`main` is changed only by the ready flow.",
  ],
];

const blockedOnMain = new RegExp(
  GIT + String.raw`(reset|rebase|merge|cherry-pick|revert|am)\b`,
);

function currentBranch() {
  try {
    return execSync("git rev-parse --abbrev-ref HEAD", {
      cwd: input.cwd,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "ignore"],
    }).trim();
  } catch {
    return "";
  }
}

for (const [pattern, reason] of alwaysBlocked) {
  if (pattern.test(command)) {
    console.error(`Blocked by .claude/hooks/guard-git.mjs: ${reason}`);
    process.exit(2);
  }
}

if (blockedOnMain.test(command) && currentBranch() === "main") {
  console.error(
    "Blocked by .claude/hooks/guard-git.mjs: `main` is changed only by the ready flow. Work on the issue branch from `gh tt workon`.",
  );
  process.exit(2);
}
