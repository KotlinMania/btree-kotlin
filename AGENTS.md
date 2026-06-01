# Kotlinmania — workspace agent guide

This is the single sharp guide for agents (Claude, Codex, anyone else) doing
work in this workspace. It merges the old workspace-root `CLAUDE.md`, the
embedded hourly runbook, the per-repo `SWIFT_EXPORT_ROLLOUT.md` lessons, and
the long trail of recovered field-feedback into one place. **When this file
disagrees with anything you remember, this file wins.**

> **Source-of-truth.** The workspace-root `AGENTS.md` (at
> `/Volumes/stuff/Projects/kotlinmania/AGENTS.md`) is canonical.
> Workspace-root `CLAUDE.md` is a tiny redirect file pointing here
> (plain text — **never a symlink**: this workspace is Kotlin
> Multiplatform and Windows is a first-class target, and Windows git
> does not handle symlinks reliably). Inside each `*-kotlin/` repo
> there is no `CLAUDE.md` at all: per-repo `CLAUDE.md` files were
> removed via `git rm` on 2026-05-24 and must stay removed.
>
> **No symlinks anywhere in this workspace.** Not at the workspace
> root, not in any repo, not under any subdirectory. If you find one,
> replace it with the file it points to (real content) and commit the
> replacement. Symlinks check out as the literal symlink content on
> Windows runners — they break KMP builds in opaque ways.
>
> As of 2026-05-24 every `*-kotlin/AGENTS.md` is a copy of this file
> (blasted out so the next agent in any repo reads the same rules).
> Per-repo specifics live in the repo's `README.md` and any local
> status notes (`NEXT_ACTIONS.md`, `PORT_REPORT.md`, etc.). When a
> repo-local doc disagrees with this file on a workspace-wide rule
> (branch hygiene, Swift Export, build-gate, JS toolchain security,
> Android SDK, test parity, completion contract), this file wins.

## The scheduled-task agent job

The workspace runs scheduled-task automation. The job, briefly:

### Focused-repo priority backlog — GitHub-flagged "JavaScript" projects

These repos have so little Kotlin source ported from their upstream Rust
crates that GitHub still classifies them as JavaScript projects. They are
the highest-priority porting backlog. Pick the stalest one first (§3
"Rotate to the stalest repo") unless `port_priority.json` (§3 "Pick what
to port next") says otherwise.

**Page 1 (top 10):**
tree-sitter-bash-kotlin · tree-sitter-language-kotlin · nucleo-kotlin ·
image-kotlin · itertools-kotlin · indexmap-kotlin · globset-kotlin ·
futures-kotlin · deno-core-icudata-kotlin · tonic-prost-kotlin

**Page 2:** opentelemetry-sdk-kotlin · rama-core-kotlin · tungstenite-kotlin
· csv-kotlin · rama-tcp-kotlin · tokio-tungstenite-kotlin · async-io-kotlin
· vt100-kotlin · encoding-rs-kotlin · env-logger-kotlin ·
constant-time-eq-kotlin · clap-complete-kotlin · assert-cmd-kotlin ·
two-face-kotlin

**Page 3:** gazebo-kotlin · test-log-kotlin · ts-rs-kotlin · toml-kotlin ·
regex-kotlin · winres-kotlin · icu-decimal-kotlin · landlock-kotlin ·
rama-net-kotlin · async-trait-kotlin · icu-locale-core-kotlin

**Page 4:** uuid-kotlin · zstd-kotlin · arboard-kotlin ·
tracing-subscriber-kotlin · url-kotlin · textwrap-kotlin · pathdiff-kotlin
· uds-windows-kotlin · tokio-test-kotlin · tracing-opentelemetry-kotlin

**Page 5:** tonic-kotlin · tokio-stream-kotlin · toml-edit-kotlin ·
test-case-kotlin · wiremock-kotlin · windows-kotlin · quote-kotlin ·
bitflags-kotlin · zip-kotlin · tracing-appender-kotlin ·
ed25519-dalek-kotlin · syntect-kotlin · serde-with-kotlin · rmcp-kotlin

**Page 6:** sqlx-kotlin · socket2-kotlin · axum-kotlin · serde-json-kotlin
· allocative-kotlin · sse-stream-kotlin · windows-sys-kotlin ·
winapi-util-kotlin · tracing-test-kotlin · tracing-kotlin · rand-kotlin ·
rama-socks5-kotlin · tempfile-kotlin · strum-macros-kotlin ·
shared-library-kotlin · serde-yaml-kotlin

**Page 7:** serde-path-to-error-kotlin · sentry-kotlin · rustls-kotlin ·
reqwest-kotlin · rcgen-kotlin · rama-http-kotlin · quick-xml-kotlin ·
pulldown-cmark-kotlin · prost-kotlin · portable-pty-kotlin ·
pkg-config-kotlin · path-absolutize-kotlin · p256-kotlin · owo-colors-kotlin
· opentelemetry-semantic-conventions-kotlin · opentelemetry-kotlin ·
opentelemetry-appender-tracing-kotlin · openssl-sys-kotlin · oauth2-kotlin
· rama-http-backend-kotlin · insta-kotlin

**Page 8 (last):** once-cell-kotlin · notify-kotlin · gix-kotlin · v8-kotlin
· libc-kotlin · rama-tls-rustls-kotlin · tokio-util-kotlin · crypto-box-kotlin
· libwebrtc-kotlin · webbrowser-kotlin · winapi-kotlin

Several of these have memory-recorded blockers that move them down the
working order rather than out of scope — `clap-complete-kotlin` blocked on
`clap-kotlin` publish; `nucleo-kotlin` on `nucleo-matcher-kotlin`;
`indexmap-kotlin` on `hashbrown-kotlin` + `equivalent-kotlin`;
`tonic-prost-kotlin` on tonic + prost; `libwebrtc-kotlin` on `bytes-kotlin`
watchOS slices. See §3 "When you can't port a Rust dependency: check
kotlinmania siblings first" for the decision tree.

### Work order — every session, in this order

Do not treat an earlier item as permission to skip a later one. The §0
completion contract requires the whole chain, and §3 names source porting
as the default next action when infra is clean.

1. **Read this file** (you're in it). The §0 contract is non-negotiable.
   Read the focused repo's `README.md` and any `NEXT_ACTIONS.md` /
   `PORT_REPORT.md`. There is no per-repo `CLAUDE.md` anymore (removed
   2026-05-24); the per-repo `AGENTS.md` is a copy of this file.
2. **Audit + sync local main** (§11 step 1). `git fetch --prune`, inspect
   `git status --short --branch`, and if on `main` behind `origin/main`
   run `git pull --ff-only origin main`.
3. **Workflow-shape audit** (§7). Fix or report any `push`/
   `pull_request` regression on `ci.yml` / `codeql.yml` / `publish.yml` /
   platform workflows.
4. **Branch reconciliation** (§2). **PR-only** — never `git push origin
   main`, never local `git switch main && git merge --no-ff <branch>`.
   Reconcile every stranded `[gone]` branch via the documented PR flow.
   Carry dirty work forward (§1 — never stash, never orphan).
5. **Infra defect repair**: build, CI, CodeQL (§8 "CodeQL `java-kotlin`
   extraction"), Dependabot security (§9), JS-toolchain security
   patching (§9), Android SDK normalization (§8), publishing, or
   workflow defect. Local verification first; commit; push the branch;
   `gh pr create` + `gh pr merge --merge --delete-branch`.
6. **Swift Export sweep** (§4) if `swiftExport { … }` is configured. The
   five-class sweep, then `./gradlew test` which must include
   `swift test` locally (§6).
7. **Source porting** (§3) is the default next action when infra is
   clean. Use `ast_distance` to inventory gaps — the binary at
   `/Volumes/stuff/Projects/kotlinmania/bin/ast_distance` is **never a
   blocker**; if a repo's `tools/ast_distance` is missing, copy the
   workspace binary in (§3 gives the exact `cp` + `.gitignore` lines).
   Translate from `tmp/` per the translation rules (one Rust file → one
   Kotlin file, top-to-bottom, no stubs, no Rust syntax in
   Kotlin/KDoc/comments, common sense applies). Check kotlinmania
   siblings before declaring a dependency unportable.
8. **Publish** if there's releasable code: bump version in
   `build.gradle.kts` + README install snippets, commit, push, `gh
   release create v<X.Y.Z>` to fire the `release[released]` publish
   workflow.
9. **Session report** (§12) with workflow-shape audit, focused repo,
   branch, constructive outcome OR exact hard blocker, PRs and branches
   inspected/merged/closed, local commands and outcomes, remote workflow
   state, commits, unresolved blockers, and the PR list as full
   clickable GitHub URLs at the very end.

### Hard constraints to remember while doing the above

- **`allWarningsAsErrors = true`** is the template default. Every warning
  is a build failure. Treat every warning as a real defect — never scope
  the flag down to silence Swift Export gate noise (§4 forbids it
  explicitly).
- **All configured targets build** (§0 condition 3 + §5
  `fullTargetBuildTaskNames`). Never shrink the gate to dodge a CI
  failure. The exceptions are the documented retired targets:
  `watchosArm32` (§5.5.1, retired 2026-05-24 — Apple Watch armv7k EOL +
  Mach-O 24-bit scattered relocation limit) and the JetBrains-deprecated
  x86_64 simulator targets `tvosX64` / `watchosX64` / `macosX64`
  (§5.5.2). These are template-law retirements, not per-repo shrinks.
- **Swift test passes locally** (§0 condition 4). Don't wait for GitHub
  to find Swift Export bugs — by then you've burned a CI run.
- **Test parity with `tmp/` Rust** (§6). Every upstream `#[test]` /
  `tests/*.rs` / `#[cfg(test)]` module gets an answer — port it, or
  leave a one-line honest comment naming the specific semantic that
  doesn't translate. Never write a fake simulation.
- **`port-lint: ignore` is not a real directive** (§3). Strip it when
  you see it. Only `port-lint: source <path>` and `port-lint: tests
  <path>` are recognized.
- **Workspace artifacts go under**
  `/Volumes/stuff/Projects/kotlinmania/automation-artifacts/`. No
  `/tmp` scratch (§1).

The yarn-resolution security block, the Android SDK installer pattern,
the build-gate task list, the canonical Swift Export rollout recipe, the
de-generification policy, the PR-only reconciliation flow, and every
other detailed recipe are in the sections numbered below — open the
relevant section before acting on a summary line above.

---

## 0. The completion contract — non-negotiable

A session is not done until every focused repo satisfies all of these,
or the run reports the exact hard blocker that makes a condition physically
impossible:

1. **The repo matches its local `CLAUDE.md` contract.** If the repo has no
   `CLAUDE.md`, the workspace `CLAUDE.md` + this file are the contract; say so
   explicitly.
2. **Real source advance.** Port or materially advance Kotlin source from
   upstream Rust in `tmp/`. CI plumbing, branch cleanup, dependency bumps,
   workflow edits, dry-runs, and status reports are *additive*, never a
   substitute for the source-porting requirement.
3. **All configured targets build.** "All targets" = the full target surface
   declared in `build.gradle.kts` and the repo-local contract — not whichever
   tasks happen to be cheap on the current host. The `fullTargetBuildTaskNames`
   wiring (§5) makes this enforceable on `./gradlew build`.
4. **Swift test passes locally.** If the repo configures `swiftExport { … }`,
   `./gradlew test` (or `check`) must invoke `swift test` against the
   `embedSwiftExportForXcode`-produced SPM package and treat a non-zero exit
   as a build failure. Do not wait for GitHub to find Swift Export bugs (§4).
5. **Test parity with upstream Rust.** Every `#[test]`, `#[cfg(test)] mod tests
   { … }`, `tests/*.rs`, and `benches/*.rs` under `tmp/` has either (a) a
   faithful Kotlin equivalent in `commonTest`/per-target test sources, or (b)
   an honest one-line comment explaining the *specific* semantic that can't
   port (mem::forget, pin-poll-once, custom Drop). No silent gaps. No fake
   simulations that test a different invariant (§6).
6. **Host-specific targets validated on a host that can run them.** A macOS
   local build is not Windows validation. Windows = the repo's Windows GitHub
   Actions job or another real Windows runner. Same for any other configured
   target the local host can't link.
7. **Clean branch/PR state.** Every dirty file produced in a focused repo is
   committed. Every PR or branch the run touched is either merged through the
   reconciliation flow (§2) or has a documented hard blocker. Stale `[gone]`
   branches with unmerged commits are **undeleted and reconciled** before the
   session ends.
8. **Workflow-shape audit reported.** The session report names every workflow
   file checked, whether `push` / `pull_request` triggers remain, and any
   remote workflow that was disabled/re-enabled (§7).

Things that are **not** a sufficient session outcome on their own:

- "No open PRs, nothing to merge."
- "Remote Actions are disabled / queued / never appeared."
- "Local macOS build passed" while iOS/Android/Windows/Swift/JS targets are
  unverified.
- "I fixed the workflow YAML" with no source advance.
- "Tests passed against unchanged code."
- "I bumped a dependency."

If a session genuinely cannot make a constructive change, the report must say
the session is **blocked**, not complete, and name the exact blocker
(user-owned dirty files in the focused range, missing upstream Rust source,
missing Maven credentials, unpublished dependency, platform runner
unavailable, gate failure with root cause outside the focused repo). A
no-change report without that level of blocker is a failed session.

> **`ast_distance` is never a blocker.** The workspace ships a runnable
> binary at `/Volumes/stuff/Projects/kotlinmania/bin/ast_distance`
> (Mach-O arm64). If a repo requires it and does not ship its own under
> `tools/ast_distance/`, copy the workspace binary into the repo's
> `tools/` folder and add the path to `.gitignore` (it's a local
> developer artifact, not a tracked dependency). Never report "no
> runnable ast_distance" — the runnable is one `cp` away.

---

## 1. Git hygiene — the bright lines

These are non-negotiable. Violating any of them requires immediate repair.

- **No worktrees.** The global pre-commit hook at
  `~/.config/git/hooks/pre-commit` refuses commits from linked worktrees.
  Do not bypass with `--no-verify`. Work in the repo's main checkout.
- **No `/tmp` scratch.** Reports, logs, helper outputs go under the repo's
  own `tmp/` or under
  `/Volumes/stuff/Projects/kotlinmania/automation-artifacts/`. Future agents
  need the raw data, not summaries.
- **No force-push.** Ever. Not to feature branches, not to `main`, not "just
  to fix the commit message."
- **`git pull` is fine — but only with `--ff-only` on `main`, and only
  with `--no-ff` on a feature branch.** Two legitimate shapes, no
  others:
  - **On `main`, after a remote PR merged on GitHub:**
    `git pull --ff-only origin main`. There must be no local commits on
    `main` (PR-only rule §2 prevents them). `--ff-only` makes the
    command fail loud if for some reason local `main` *has* diverged,
    so you can't accidentally create a merge commit on `main` that
    didn't go through a PR.
  - **On a feature branch, to bring `main` in before opening/updating
    a PR:** `git pull --no-ff origin main`. This creates the explicit
    merge commit on the feature branch (clean PR diff, no rebase, no
    history rewrite).
  - **Bare `git pull` (default behavior) is forbidden.** Its behavior
    depends on `pull.ff` / `pull.rebase` config and branch divergence;
    it can silently rewrite history or create unintended merge
    commits. Always pass `--ff-only` or `--no-ff` explicitly so the
    intent is in the command, not in the config.
- **No destructive git to unstick yourself.** Never `reset --hard`,
  `branch -D` on unmerged work, `push --force`, `checkout -- .`,
  `restore .`, or `clean -f` to escape a stash/no-ff/branch flow the user
  asked for. Report the stuck state and wait.
- **Branch-only, never push to main. PR-merge only.** Push a feature
  branch, open a PR with `gh pr create`, merge the PR with `gh pr merge`.
  **Never `git push origin main`** — even for reconcile merges. **Never
  `git switch main && git merge --no-ff <branch>` to land work on main
  locally.** The "merge happens" event must always be `gh pr merge`, never
  a local commit pushed to `main`. The only local merge that's allowed is
  `git merge --no-ff origin/main` *on a feature branch* (bringing main
  into the branch so the PR is clean) — that doesn't change `main`.
- **No `port/*` branch prefix.** It trips guardrails. Use one neutral
  integration branch per session (e.g. `automation/<short-topic>`,
  `session/<date>-<topic>`); land every commit in the session onto it as
  separate commits; ship as one PR.
- **No broad recursive `find`.** Do not walk
  `/Volumes/stuff/Projects/kotlinmania`, all `*-kotlin/` repos, or `find .`
  from a repo root without prunes. It monopolizes the workspace and disables
  required tooling. Use `rg --files` with explicit `-g` patterns, or scope
  to small named subtrees (`src/`, `.github/`, `tools/`). If a recursive
  `find` is unavoidable, prune `.git`, `.gradle`, `.android-sdk`,
  `node_modules`, `build`, and any vendored upstream directories first.
- **Inspect `git status --short --branch` (including untracked) before any
  fetch/merge/switch.** Never discard or revert changes you did not make.
- **Commit ALL dirty. Never stash. Never orphan.** This rule exists
  because Sydney has lost an *insane* amount of code to (a) `git stash`
  entries that get forgotten across sessions and (b) commits ending up
  on orphaned refs after a branch switch. The procedure is mandatory:
  - **If dirty AND on a feature branch** → commit it on that branch.
    Push the branch. Open a PR. Merge via `gh pr merge`. Do not switch
    branches first; the dirty work belongs where it was written.
  - **If dirty AND on `main`** → this is the *only* legitimate case for
    switching branches with a dirty tree. Create a new feature branch
    from that dirty state (`git switch -c automation/<topic>`), commit
    it there, push, PR, merge.
  - **Never `git stash`** to "park" work for later. Stashes get
    forgotten; orphaned stashes are dropped silently by garbage
    collection.
  - **Never move dirty work from one feature branch to another.** That
    creates the orphan-ref pattern that has eaten work in this
    workspace. If a feature branch has work that should be elsewhere,
    commit it where it is FIRST, push, then cherry-pick the commit
    onto the other branch and push that too.
  - **Never call it "another agent's WIP" and walk away.** Once dirty
    files are in this tree or this stash, they are yours to land.
  - **End of session = no dirty files anywhere.** `git status --short`
    must be empty before the session report. If a file is genuinely
    not yours to commit (user explicitly told you to leave it alone),
    name it in the session report under "Unresolved blockers."
  - **The branch-must-exist requirement is why we use PR-only merges
    (§2).** Pushing the branch + merging via remote PR is what makes
    the commit recoverable. A local-only branch with un-pushed
    commits is one accidental reset away from the same code loss.

---

## 2. Branch reconciliation — never delete without merging, PR-only

**The rule.** Every branch in every focused repo is yours to manage. Branch
ownership is irrelevant: a stale Dependabot branch, a prior automation
branch, a human-made branch, a `[gone]` upstream tracking ref — all of them.

**Never delete a branch without one of:**

1. **Proving ancestry.** `git merge-base --is-ancestor <branch> main`
   returns true → the branch is already in `main` → safe to delete the
   remote (and local) ref via `gh` or `git push origin --delete`.
2. **Merging through the PR-only reconciliation flow.** For any branch
   with commits not in `main`:

   ```bash
   git fetch --prune
   git switch <branch>
   git merge --no-ff origin/main           # bring main INTO the feature
                                           # branch only — never the reverse
   # Equivalent one-liner: git pull --no-ff origin main
   # resolve conflicts, run local gates (./gradlew test, swift test, etc.)

   # If the original remote ref is gone, push as a fresh ref:
   git push -u origin HEAD:reconcile/<branch>

   gh pr create --base main --head reconcile/<branch> \
       --title "Reconcile stranded <branch>" \
       --body  "Restores remote-deleted branch and merges it into main per workspace rule."

   # Once PR checks are green (or known to be on a manual-CI repo):
   gh pr merge <PR-number> --merge --delete-branch
                                           # or --squash / --rebase per repo style
   ```

3. **NEVER `git switch main && git merge --no-ff <branch> && git push
   origin main`.** That is the forbidden local-merge-to-main flow. Even
   reconcile merges go through a PR. The "main now contains X" event must
   be a `gh pr merge` event, never a local push.

If you find a previously-deleted branch that was **not** an ancestor of
`main` at deletion time (e.g. a `[gone]` local branch pointing at commits
not in `main`'s history): **undelete and reconcile.** Push the branch back
to origin under a fresh name (or its original name if `gh` allows), then
run the PR-only flow above.

For Dependabot PRs, this is normal integration work — `gh pr merge` the
update into `main` as part of the session, preserving the repo's required
lockfile workflow.

### Why PR-only (no local main merges)

- The repo's required CI gates (CodeQL, dependabot, target builds, Swift
  Export, publish dry-run) only fire on the PR event. A local push to
  `main` bypasses them.
- The branch-protection rules on `KotlinMania/*` reject direct pushes to
  `main`. A local `git merge --no-ff <branch> && git push origin main`
  will fail anyway — but worse, it leaves the local `main` ahead of
  `origin/main`, which then either gets force-pushed (forbidden) or has
  to be manually reset (error-prone).
- Audit trail. Every change to `main` corresponds to a PR with a number,
  description, reviewer (even if Claude), and the merge commit message.
  Local merges erase that audit trail.

---

## 3. Porting discipline

These defaults apply unless a repo's own docs explicitly say otherwise.

### Translation rules

- **Kotlin stays Kotlin.** `PascalCase` types, `camelCase` functions/locals,
  lowercase packages. Never `snake_case` Kotlin identifiers to match Rust.
- **No Rust in source.** No Rust syntax in `.kt`, no Rust syntax in KDoc, no
  Rust syntax in inline comments. Translate `Vec<T>` → `List<T>`,
  `Option<&str>` → `String?`, `Self::foo()` → `foo()`, drop lifetimes, lift
  `cfg(...)` / `#[derive(...)]` into prose.
- **Comments are translated upstream content.** Not a place for porting
  notes, "Rust vs Kotlin" rationale, ast_distance strategy, or workaround
  explanations. Those go in commit messages, `NEXT_ACTIONS.md`, or review
  notes. **No deferred language** in any tracked file: no "deferred,"
  "next pass," "out of scope," "blocked on X" written into `CLAUDE.md` /
  `README.md` / `NEXT_ACTIONS.md` / PR descriptions. Finish the work; don't
  inventory what isn't done.
- **No stubs.** No `TODO()`, no placeholder bodies, no `@Suppress(...)`. If
  something is missing, port the dependency. If a Rust test depends on
  semantics Kotlin can't reproduce (mem::forget, pin-poll-once, Drop),
  leave it unported with an honest one-line comment naming the specific
  semantic; never write a "simulation" that tests a different invariant.
- **Translation happens in the main loop.** No subagent-driven `.kt` edits.
- **One Rust file → one Kotlin file.** Translate top-to-bottom in upstream
  order. Read the whole upstream file before editing the Kotlin file. Two
  approved exceptions:
  - An upstream `mod.rs` with real implementation may be parceled into
    focused Kotlin files; the Kotlin `Mod.kt` stays a module-tracking
    ledger. Each derived `.kt` carries
    `// port-lint: source <that mod.rs path>` so `ast_distance` knows
    what is tied to what.
  - An upstream `lib.rs` with real implementation (not just re-exports)
    follows the same parceling rule: per-symbol `.kt` files, `Mod.kt` as
    a ledger only, never a typealias bridge.
- **`mod.rs` / `lib.rs` re-exports → caller migration, not central
  typealias.** When an upstream `mod.rs` only re-exports a symbol that
  lives elsewhere, do **not** mint a central Kotlin `typealias` for it.
  Rewrite callers to the original symbol; use `import <upstream-fq> as
  <Name>` if the caller still needs the re-exported spelling. `Mod.kt`
  records each migrated caller under a `// Callers migrated:` ledger
  (append-only). Reference example: `serde-kotlin/.../private/Mod.kt`
  vs `serde-kotlin/tmp/serde/serde_core/src/private/mod.rs`.

### Drift measurement — `ast_distance` is how we measure "how close to done"

There is **no "parity mode"** on/off. Every `*-kotlin/` repo with upstream
Rust under `tmp/` uses `ast_distance` as the way to answer "what's left to
port?" — that's the whole point of the tool, and it applies to every
repo whether or not it ships its own `tools/ast_distance/` binary.

- Prefer the repo-local `tools/ast_distance` binary/script when present.
- If the repo does not ship one, copy the workspace binary into the
  repo's `tools/` folder and add the path to `.gitignore`:
  ```bash
  mkdir -p tools
  cp /Volumes/stuff/Projects/kotlinmania/bin/ast_distance tools/
  grep -qxF 'tools/ast_distance' .gitignore 2>/dev/null \
    || echo 'tools/ast_distance' >> .gitignore
  ```
  The binary is a developer artifact, not a tracked dependency.
- **A missing or unrunnable `ast_distance` is NEVER a blocker.**
  Continue transliterating from `tmp/` per the translation rules above
  (read the whole upstream file, one Rust file → one Kotlin file,
  top-to-bottom order, Kotlin-facing comments, no stubs). The measurement
  is for "what gap to attack next" — useful, but absence of measurement
  is not absence of work. Translate. Then run the tool when you can.
- When `ast_distance` *is* runnable, measure before choosing work and
  after completed file/phase boundaries. Don't chase similarity scores
  while half-translating; don't Rustify Kotlin to appease the tool.
- Port-lint provenance headers are required on derived files:
  ```kotlin
  // port-lint: source <path-relative-to-upstream-root>
  package <repo package>
  ```
  The only valid `port-lint:` directives are `source <path>` (for
  ports of Rust source files) and `tests <path>` (for ports of Rust
  test files). **There is no `port-lint: ignore` directive.** Past
  agents have written `// port-lint: ignore — ...` as if it were one;
  it isn't recognized by `ast_distance` or any tooling. Strip those
  lines when you see them; they are noise, not configuration. The
  only legitimate reason a Kotlin file lacks a `port-lint: source`
  header is that it has no upstream Rust counterpart (Kotlin-side
  glue, repo-specific helpers, or new APIs), in which case it simply
  has no header — not a fake "ignore" directive.

A repo that no longer has upstream Rust under `tmp/` and has no
`.ast_distance_config.json` is a mature Kotlin-first repo: it's
optimizing for idiomatic Kotlin, not against an upstream Rust oracle.
That's a state, not a "mode."

### When you can't port a Rust dependency: check kotlinmania siblings first

Before declaring a Rust dependency unportable, **check whether
kotlinmania already ships it.** The workspace has 224 `*-kotlin/`
repos, many of which are Maven Central publications. A "missing"
dependency is often a `*-kotlin/` sibling away. Order of attack:

1. **Look for a `<crate>-kotlin/` sibling repo** in the workspace.
   ```bash
   ls -d /Volumes/stuff/Projects/kotlinmania/<crate>-kotlin 2>/dev/null
   ```
2. **Check if that sibling publishes to Maven Central.** Look at its
   `build.gradle.kts` for `publishAndReleaseToMavenCentral` wiring and
   confirm a recent release tag (`gh release list -R
   KotlinMania/<crate>-kotlin`). If yes, declare the Gradle dep:
   ```kotlin
   commonMain.dependencies {
       implementation("io.github.kotlinmania:<crate>-kotlin:<version>")
   }
   ```
3. **If the sibling exists but isn't on Maven Central**, that's the
   real blocker — port progress depends on publishing the sibling
   first. Memory has prior examples: `clap-complete-kotlin` blocked on
   `clap-kotlin` publish, `nucleo-kotlin` blocked on `nucleo-matcher`,
   `indexmap-kotlin` blocked on `hashbrown-kotlin` +
   `equivalent-kotlin`, `tonic-prost-kotlin` blocked on tonic + prost.
4. **If no sibling exists**, you have two options: port it in a new
   `*-kotlin/` repo, or note the genuine impossibility in the current
   repo's session report. Don't stub. Don't write a Kotlin
   "simulation" that tests a different invariant.

### Common sense applies

If something genuinely can't be ported exactly to the letter (mem::forget
semantics, pin-poll-once + Pin behavior `kotlinx.coroutines` can't
reproduce, custom `Drop`, raw pointer layout, `unsafe` UB checks), you
don't port it. Translate what *is* portable; leave a one-line honest
comment in the Kotlin position where the unportable code would have
gone, naming the specific Rust semantic that doesn't translate. Then
move on to the next item.

That's not the same as "I'll skip this." It's: translate as much as is
faithfully translatable, document the exact remainder, ship the rest.
The forbidden pattern is hiding the gap (stubbing, fake simulation,
`@Suppress`, deleting the test). Acknowledging the gap honestly is the
right answer.

### Rotate to the stalest repo

Don't keep gnawing the same handful of projects (regex-syntax-kotlin,
starlark-syntax-kotlin, codex-kotlin). Every `*-kotlin/` repo is in scope.

```bash
for d in /Volumes/stuff/Projects/kotlinmania/*-kotlin /Volumes/stuff/Projects/kotlinmania/klang; do
    [ -d "$d/.git" ] || continue
    ts=$(git -C "$d" log -1 --format=%ct 2>/dev/null) || continue
    printf '%s\t%s\t%s\n' "$ts" "$(date -r "$ts" +%Y-%m-%d)" "$(basename "$d")"
done | sort -n | head -10
```

Top of the list = where the rotation pointer goes. Skip a candidate only
for a *concrete* reason (dirty tree, hard external blocker, infra issue),
and note the reason in the run report. Don't silently fall back to the
same big repo as last time. Same rule per-file: if you've touched the
same file three runs in a row, pick a different file (or repo) first.

### Pick what to port next — Jira `ordered_priority` artifacts

Two artifacts at the workspace root answer "what should I port next?"
without anyone needing to hit Jira directly:

- **[`PORT_PRIORITY.md`](./PORT_PRIORITY.md)** — human-readable
  prioritized roadmap.
- **[`port_priority.json`](./port_priority.json)** — machine-readable
  dump of the same data. Schema below.

Both are auto-generated. **Do not hand-edit them.** If they look
stale, regenerate. If they look wrong, fix the generator
(`scaffold/analysis/generate_port_priority.py`) or the upstream Jira
data — never patch the artifacts in place.

**The headline field: `ordered_priority`** — every Initiative carries
a single integer:

- **`0`** — do not port. No callers, not needed (test-only crates,
  build-script-only crates, unreferenced utilities). Don't port unless
  something downstream changes.
- **`1`** — port first. Highest-leverage item.
- **`2..N`** — ordered descending by impact. Lower integer = port
  sooner.

`tier == preexisting` items (already-shipped `*-kotlin` repos) never
get `0`; they're maintenance-mode regardless of who depends on them.

The integer also lives in Jira as the **`Ordered Priority`** custom
field (`customfield_10153` on this site), so JQL works:

```
"Ordered Priority" = 1                                  # the single most important port
"Ordered Priority" <= 10                                # Wave 1
"Ordered Priority" > 0 ORDER BY "Ordered Priority" ASC  # full work order
"Ordered Priority" = 0                                  # the skip-list
```

**Regenerate.** Both idempotent; re-running over an unchanged graph
produces byte-identical outputs:

```bash
python scaffold/analysis/generate_port_priority.py  # graph → port_priority.{json,md}
python scaffold/jira/apply_ordered_priority.py      # JSON ranks → Jira field
```

Auth via `scaffold/_auth.py` (chmod 600). The generator hits the live
Jira graph (project `COD`, issuetype `Initiative`); the apply script
reads `ordered_priority` straight from `port_priority.json` and pushes
it to Jira. Optional generator flags:

- `--use-cache` — reuse `scaffold/graph_cache.json` if it's <5 min old
  (skips the Jira fetch)
- `--cache-age N` — change cache freshness threshold (seconds)
- `--top N` — number of rows in the top-N table (default 30)
- `--out-dir PATH` — write outputs somewhere other than the kotlinmania root

The field-ID cache for the apply script lives at
`scaffold/.ordered_priority_field_id`. Delete it if you ever migrate
to a different Jira site.

**What the generator computes** — for every Initiative:

| Field | Meaning |
|---|---|
| `ordered_priority` | The pick-one-integer field above. **0 = skip**, **1 = port first**, higher = lower priority. |
| `layer` | Topological depth from leaves (Kahn-style BFS). L0 = no prerequisites. |
| `direct_dependent_count` | Number of Initiatives this one directly blocks (out-degree on the Blocks graph). |
| `transitive_dependent_count` | Total Initiatives reachable downstream via Blocks edges. The headline impact metric. |
| `prerequisite_count` | In-degree on the Blocks graph — how many things must finish first. |
| `cluster` | Weakly-connected component ID (treats Blocks ∪ Relates as undirected). |

The "top 3" highlight = filter to `layer == 0`, `status != "Done"`,
`ordered_priority > 0`; sort ascending by `ordered_priority`; take
three.

**`port_priority.json` schema** (stable contracts: `schema_version`
bumps if any field is renamed/removed; key references use the Jira
issue key `COD-NNN` verbatim; `blocks_edges` is directed
`[blocker, blocked]`; `relates_edges` is symmetric with `a < b`; every
list-valued node field is sorted for diff stability; `tier` derives
from labels — `tier-direct` = codex-rs depends on this external
crate, `tier-internal` = a sub-module of codex-rs itself,
`tier-preexisting` = `*-kotlin` already exists and needs maintenance,
`unknown` = no tier label):

```jsonc
{
  "schema_version": 1,
  "nodes": {
    "COD-128": {
      "key": "COD-128",
      "summary": "Port the serde crate",
      "status": "To Do",
      "labels": ["codex-port", "crate-serde", "tier-direct"],
      "tier": "direct",                       // direct | internal | preexisting | unknown
      "layer": 0,
      "ordered_priority": 1,                  // 0 = skip; 1+ = rank, lower = port sooner
      "direct_dependents": ["COD-26", ...],   // sorted list of keys
      "direct_dependent_count": 101,
      "transitive_dependent_count": 187,
      "prerequisites": [],                    // sorted list of keys
      "prerequisite_count": 0,
      "cluster": "COD-128"                    // anchor key of the WCC this node lives in
    }
    // ... one entry per Initiative
  },
  "blocks_edges":  [["COD-128", "COD-26"], ...],   // [blocker, blocked]
  "relates_edges": [["COD-3",   "COD-99"], ...],   // sorted alphabetically
  "summary": {
    "node_count": 322,
    "blocks_edge_count": 1919,
    "relates_edge_count": 544,
    "max_layer": 8,
    "status_distribution": {"To Do": 322},
    "tier_distribution":   {"direct": 191, "internal": 107, "preexisting": 22, "unknown": 2},
    "cluster_count": 17,
    "singleton_cluster_count": 16,
    "largest_cluster_size": 306
  }
}
```

**When the priority artifacts are the wrong tool.** The graph
captures *what blocks what* at the crate level. It does **not**
capture:

- Function-level call graphs inside a crate (per-Initiative Stories +
  Tasks live under each Initiative — query Jira directly).
- The state of any individual `*-kotlin/` repo's port. **Each repo's
  `AGENTS.md` / `CLAUDE.md` / `README.md` tells that story.**

Use `port_priority.json` for "where do I start" decisions. For
everything else, defer to per-repo docs.

**Scaffold script index** — Jira API helpers live under
`scaffold/jira/`; `scaffold/jira/_jira_http.py` provides throttled +
retrying API calls (default 6 req/s, exponential backoff on 429/5xx).
`scaffold/build_surface.py` intentionally remains at the scaffold
root.

| Script | Purpose |
|---|---|
| `analysis/generate_port_priority.py` | Graph fetch + analysis → `port_priority.{json,md}`. |
| `jira/apply_ordered_priority.py` | Pushes `ordered_priority` from JSON onto every Initiative as the `Ordered Priority` custom field. Self-wires to COD screens on first run. |
| `analysis/graph_analyze.py` | Lower-level analysis library + ad-hoc CLI (`--layer N`, `--cluster KEY`, etc.). |
| `analysis/audit_codex_internal.py` | Parse the codex-rs Cargo workspace into `codex_internal_deps.json` (107 internal crates). |
| `analysis/audit_initiative_deps.py` | Parse all 215 external crates' Cargo.tomls into `initiative_deps.json`. |
| `jira/create_internal_initiatives.py` | One-shot to seed the 107 internal Initiatives in Jira. |
| `jira/apply_initiative_deps.py` | Apply Blocks/Relates links between external Initiatives based on the audit. |
| `jira/apply_internal_links.py` | Two-wave linker for internal→internal and internal→external. Idempotent. |
| `jira/link_codex_kotlin_assembly.py` | Link the COD-2 codex-kotlin assembly Initiative to all 107 internal sub-modules. |
| `jira/activate_internal_crate.py` / `jira/activate_all_internal.py` | Per-crate Story+Task creator + bulk driver. |
| `jira/markdown_to_adf.py` | Markdown → ADF (Atlassian Document Format) converter. Used when posting Jira descriptions / comments. |

**Description hygiene.** Atlassian Intelligence (Rovo) curates
Initiative descriptions. **Never `PUT` / `PATCH` an existing
description** — append via `POST /comment` instead. The scaffold
scripts respect this; if you write new ones, do too.

---

## 4. Swift Export rollout — the recipe and the four hazard classes

Every `*-kotlin` repo with `swiftExport { … }` carries a per-repo
`SWIFT_EXPORT_ROLLOUT.md` mirrored from the canonical
**[`anstyle-kotlin/SWIFT_EXPORT_ROLLOUT.md`](anstyle-kotlin/SWIFT_EXPORT_ROLLOUT.md)**.
Read it before touching Swift Export wiring. The five canonical changes
(both iOS Simulator slices static, `swift.yml`, `swift-test-harness/`,
the `embedSwiftExportForXcode` task, the build gate that runs `swift test`)
are described there.

### The mandatory infrastructure pins

- **`swift.yml` runs on `runs-on: macos-26`** — not `macos-latest`. The
  Kotlin/Native 2.3.x SDK cache requires it.
- **Never add `maxim-lobanov/setup-xcode@v1`** or any other third-party
  Xcode setup action. Third-party actions cause `startup_failure` on the
  KotlinMania allowlist.
- **`swift test` is wired into the Kotlin `test` task.** A Swift Export
  failure must surface on `./gradlew test` locally. Do not push and let
  remote CI find it — by that point you've burned a CI run.
- **iOS Simulator XCFramework fat-stage rule.** Both `iosSimulatorArm64`
  AND `iosX64` must be `isStatic = true`. Mixing one static / one dynamic
  fails `assembleDebugIosSimulatorFatFramework<Name>` with:
  ```
  Cannot create a fat framework from:
    <Name> - arm64 - static
    <Name> - x64 - dynamic
    All input frameworks must be either static or dynamic
  ```
  This is **gap #7** in the rollout. Earlier rollouts flipped only
  `iosSimulatorArm64`; if a repo still has the asymmetric shape, fix
  `iosX64` too in the same commit.

### The four hazard classes (what makes Swift Export fail under `-Werror`)

Swift Export's generated bridge fails `allWarningsAsErrors=true` in four
distinct ways. **Do not scope `allWarningsAsErrors=false` to
`compileSwiftExportMain*` to silence them — that's symptom suppression
and a forbidden fix.** Apply the structural remedy below.

**Class A — Unchecked cast `Any?` → `Foo<T>`.** Public generic types with
unconstrained type parameters force the bridge to erase to `Any?` and cast
back. Fix: wrap the generic with an `internal` implementation + a
non-generic façade for Swift, or use a stdlib type the bridge already
handles.

**Class B — `Cannot infer T`.** Public generic functions Swift can't
project a single Swift type for. Fix: `internal` factory that fixes the
type parameter at the boundary; the public Kotlin function stays generic
for Kotlin callers.

**Class C — `Array`/`List`/`Iterator` mismatch.** Public collection
surfaces that Swift would bridge to non-equivalent types. Fix: non-generic
collection at the boundary, or wrap in a thin Swift-friendly type.

**Class Stdlib (the fourth class) — warnings in *auto-generated*
`KotlinStdlib.kt`.** Public `kotlin.Result<X>`, classes extending
`RuntimeException` / `Exception` / any `Throwable` subclass, public
`MutableList<X>`, public `MutableMap<K, V>` each drag the
`Throwable.getStackTrace()` → `Array` bridge or the
`MutableList`/`MutableMap` bridge into the generated `KotlinStdlib.kt`,
which then fails Unchecked-cast under `-Werror`. Fixes:

- **Public `kotlin.Result<T>`** → sealed `Outcome.Ok(value: T) | Outcome.Err(error: SomeError)`,
  where `SomeError` does NOT extend `Throwable`.
- **`class FooError : RuntimeException(...)`** (or `Exception`) →
  data class / sealed class that does NOT extend any `Throwable`. Kotlin
  callers that need to throw it wrap in their own throw site.
- **Public `MutableList<X>` / `MutableMap<K, V>`** → internal-backed
  `List<X>` / a custom read-only Map wrapper; internal helpers do
  copy-and-replace to keep the read-only public surface.
- **Public `Pair<A, B>`** → named record class.
- **Public `() -> X` / `(A) -> B`** function types → `fun interface` SAM
  type. Swift Export can't bridge raw Kotlin function types.

### Tactical escape hatch — `@HiddenFromObjC` (NOT the long-term answer)

`@HiddenFromObjC` is the **stopgap** that gets a repo's Swift Export
gate green without breaking Kotlin callers. Use it when you need the
gate green *today* and reshaping the API safely will take more than the
current session can give:

```kotlin
@file:OptIn(ExperimentalObjCRefinement)

import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class FooBar<T : Any>(…)
```

Reference: arc-swap-kotlin PR #20, itertools-kotlin PR #37,
schemars-kotlin PR #11 (the first non-trivial repo — 37 `.kt` files —
fully green with gap #7 + gap #8 + iosX64 all aligned). Diff against
schemars-kotlin when applying the rollout to another repo.

**Fallback** (when faithfulness isn't a constraint): mark `internal` +
expose a factory returning the public stdlib interface.

### Project goal: strongly-typed public APIs — generics only where design requires

After a `@HiddenFromObjC` sweep lands and a repo's Swift Export gate is
green, the **follow-up pass is to de-generify**. The project goal across
the workspace is **strongly-typed public APIs**: no unconstrained
generics, no `<T : Any>` exposed to Swift, no `<T, E>` Result-style
wrappers — unless the design genuinely requires the type parameter
(parser combinators, typed builders, container types where the element
type carries real meaning).

Why this is a real rule and not a style preference:

- `@HiddenFromObjC` *hides* the Swift bridge problem; it doesn't *solve*
  it. The Swift side sees no API for that surface at all — which is
  worse than a non-generic Swift API. Swift callers either can't use
  the type or have to drop into Objective-C interop.
- The Class A "unchecked cast `Any?` → `Foo<T>`" failure is generated
  by the same erased-bridge code path no matter what you do; hiding it
  means the next type the bridge tries to express through that path
  hits the same wall.
- Strongly-typed public APIs survive Kotlin/Native compiler upgrades.
  The generic-bridge handling in Swift Export is the most volatile
  surface of the K/N toolchain and keeps changing between 2.3.x
  patches; non-generic APIs don't care.

**The de-generify checklist** (run as the *next* session after a
`@HiddenFromObjC` gate-green pass):

1. List every `@HiddenFromObjC` declaration in commonMain. Each one is
   a TODO for de-generification.
2. For each: is the generic parameter actually doing structural work
   for callers? If **no** → replace with the concrete type (or a small
   set of concrete subtypes — `IntFoo`, `StringFoo`, `DoubleFoo`)
   and delete `@HiddenFromObjC`.
3. If the parameter *is* doing structural work but the only public use
   ever instantiates it at one or two concrete types: hoist those
   instantiations into named non-generic subclasses (`class IntCell :
   Cell<Int>()`), expose those publicly, mark the generic base
   `internal`, drop `@HiddenFromObjC`.
4. If the parameter is genuinely required by design (e.g. a typed
   builder DSL, a generic container that callers really instantiate
   at arbitrary types), leave the generic — but document *why* in a
   one-line `// generic by design:` comment so the next sweep doesn't
   mistake it for unfinished work.
5. `Pair<A, B>` → named record class. Always. There is no design
   justification for leaking `kotlin.Pair` into a public API.
6. `Result<T, E>` / generic Outcome wrappers → per-error-domain sealed
   classes. `AddConstraintOutcome` not `Outcome<Unit, AddConstraintError>`.
7. Function-type surfaces (`(A) -> B`) → `fun interface` SAM with a
   meaningful name.

**Forbidden phrasing** in commits and PR descriptions:

- "Hide `Foo` from Swift" as the *only* change for that API.
  → Acceptable as a tactical stopgap PR; not acceptable as the
  permanent design.
- "Generic API kept for flexibility." → Specify *which caller* needs
  the flexibility, or de-generify.

Memory hook: `feedback_swift_export_three_patterns.md`,
`feedback_swift_export_throwable_result_array.md`, and
`feedback_swift_export_gap8_internal_generics.md` all predate this
goal. They describe how to *paper over* generic-bridge failures with
`@HiddenFromObjC`. The de-generify pass is what comes *after* those
fixes land, not instead of them.

### The 5-class sweep (per-repo, run before every Swift release)

Inspect commonMain for **all** of these, not just whatever last tripped CI:

1. `runs-on:` on `swift.yml` is `macos-26`; no `setup-xcode` action.
2. Public mutable collection surfaces (`MutableList`, `MutableMap`,
   `MutableSet`) → read-only Kotlin types + internal copy-and-replace
   helpers.
3. Public generic types / SAMs / function-type surfaces → **first
   choice: de-generify** (concrete named subtypes, `fun interface` SAM
   with a real name, internal generic base). `@HiddenFromObjC` is the
   tactical stopgap only when the de-generify pass can't land safely
   in the current session. See "Project goal: strongly-typed public
   APIs" above.
4. Public `kotlin.Result<X>` / `Throwable` subclasses → sealed Outcome
   types + non-Throwable error classes.
5. Public `Pair<A, B>` helpers → named record class.

Run the gate after every repair, not just at the end:

```bash
./gradlew embedSwiftExportForXcode --no-daemon
./gradlew test --no-daemon          # must include swift test (§5)
```

### Annotation hygiene

When you reach for `@HiddenFromObjC`, also add the file-level opt-in:

```kotlin
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)
```

at the top of the file (before `package`). Otherwise compilation fails
with "this declaration is experimental and its usage must be marked."

---

## 5. The build-gate — `build` must compile every configured target

Default Kotlin Multiplatform `./gradlew build` does **not** link every
native test binary or assemble every XCFramework. That makes `build` a
fake-green gate in many repos. The 2026-05-19 async-channel-kotlin
incident proved this workspace-wide. **Every repo with the broad target
matrix needs the `fullTargetBuildTaskNames` wiring**:

```kotlin
val fullTargetBuildTaskNames = setOf(
    // Android KMP
    "compileAndroidMain", "compileAndroidHostTest", "compileAndroidDeviceTest",
    "assembleAndroidMain", "assembleUnitTest", "assembleAndroidTest",
    // JVM
    "jvmMainClasses", "jvmTestClasses",
    // JS / Wasm
    "jsMainClasses", "jsTestClasses",
    "wasmJsMainClasses", "wasmJsTestClasses",
    "wasmWasiMainClasses", "wasmWasiTestClasses",
    // Native binaries + test binaries (all configured native targets)
    "androidNativeArm32Binaries", "androidNativeArm32TestBinaries",
    "androidNativeArm64Binaries", "androidNativeArm64TestBinaries",
    "androidNativeX64Binaries",   "androidNativeX64TestBinaries",
    "androidNativeX86Binaries",   "androidNativeX86TestBinaries",
    "iosArm64Binaries",           "iosArm64TestBinaries",
    "iosSimulatorArm64Binaries",  "iosSimulatorArm64TestBinaries",
    "iosX64Binaries",             "iosX64TestBinaries",
    "linuxArm64Binaries",         "linuxArm64TestBinaries",
    "linuxX64Binaries",           "linuxX64TestBinaries",
    "macosArm64Binaries",         "macosArm64TestBinaries",
    "mingwX64Binaries",           "mingwX64TestBinaries",
    "tvosArm64Binaries",          "tvosArm64TestBinaries",
    "tvosSimulatorArm64Binaries", "tvosSimulatorArm64TestBinaries",
    // watchosArm32 is retired workspace-wide as of 2026-05-24 — see §5.5.
    "watchosArm64Binaries",       "watchosArm64TestBinaries",
    "watchosDeviceArm64Binaries", "watchosDeviceArm64TestBinaries",
    "watchosSimulatorArm64Binaries", "watchosSimulatorArm64TestBinaries",
    // Swift Export bridge + XCFramework (substitute the repo's framework name)
    "embedSwiftExportForXcode",
    "assemble<Name>XCFramework",
)

tasks.named("build") {
    dependsOn(fullTargetBuildTaskNames)
}

afterEvaluate {
    tasks.named("build") {
        dependsOn(
            tasks.matching {
                name.endsWith("MainClasses") ||
                    name.endsWith("TestClasses") ||
                    name.endsWith("Binaries") ||
                    name.endsWith("XCFramework")
            },
        )
    }
}
```

The explicit set is the **audit contract**. The `afterEvaluate` matcher
is a safety net for future generated tasks but is not enough alone —
dynamic-only attempts dropped native test-binary links in the dry-run
graph during the original investigation.

### Rules around the build gate

- **Never shrink the target gate.** If `fullTargetBuildTaskNames` fails
  on a missing task, **ADD the target to `kotlin { … }`**; never filter
  the gate to match the smaller surface. The gate is the contract; the
  target block is what the gate forces you to declare.
- **Real build, not `--dry-run`.** For all-target gate verification, run
  a real `./gradlew build`. `--dry-run` is fine as an *intermediate*
  check but never counts as the gate pass; failures escape to remote CI
  that way.
- **Local build before push.** Run the focused repo's narrowest local
  Gradle gate before pushing or PR-ing — even when the fix is identical
  to one verified elsewhere. "Identical shape" is not "verified here."
- **Acceptance commands:**
  ```bash
  ./gradlew build --dry-run --console=plain --no-daemon
  ./gradlew clean build --no-daemon
  ./gradlew build --no-daemon
  find build/bin -maxdepth 4 -type f \( -name 'test.kexe' -o -name 'test.exe' \) -print | sort
  find build/XCFrameworks -maxdepth 5 -type d -name '*.xcframework' -print | sort
  ```
  Expected `test.kexe` set covers every configured Android Native, iOS,
  Linux, macOS, MinGW, tvOS, watchOS target; XCFramework outputs cover
  both `debug/` and `release/`.

For long target surfaces, a single clean `build` may run long. Do not
count a killed or truncated session as a pass — split only to isolate
expensive pieces, then run a final
`./gradlew build --no-daemon --console=plain --no-configuration-cache`
that reaches `BUILD SUCCESSFUL`.

### 5.5 Retired targets — `watchosArm32` (armv7k)

`watchosArm32` is **retired workspace-wide as of 2026-05-24**. It is no
longer part of the standard target surface, the workflow surface, the
build-gate, or the source-set layout. Every existing repo carries it
as a legacy declaration that must be scrubbed; every new repo
generated from the template inherits the corrected (no-armv7k)
surface.

**Why retired.**

- *Apple hardware reality.* armv7k is the Apple `Series 3` and earlier
  chip family (S1, S1P, S2, S3). watchOS 9 (2022) dropped Series 3;
  current watchOS releases only run on Series 4+ (64-bit). No
  currently-supported Apple Watch is 32-bit.
- *Apple submission reality.* Xcode 15+ defaults new watchOS projects
  to `arm64` only; armv7k is an opt-in legacy slice with no current
  hardware to target.
- *Mach-O technical landmine.* armv7k uses Mach-O **scattered
  relocations** with **24-bit offset fields** (~16 MB). Any Kotlin
  source that embeds binary data (ICU tables, V8 snapshots, language
  grammars, Unicode property tables, certificate bundles) fails to
  link with
  `ld: cannot compile inline asm: can not encode offset '0x…' in
  resulting scattered relocation`. This is a Mach-O format
  limitation, not a Kotlin/Native bug; no compiler flag fixes it.
  Reference incident: deno-core-icudata-kotlin (~32 MB ICU table,
  https://github.com/KotlinMania/deno-core-icudata-kotlin/actions/runs/26303690097).

**Scrub checklist — three places per repo, all required.**

1. **`build.gradle.kts` target block** — delete the `watchosArm32 { … }`
   stanza entirely. Keep `watchosArm64`, `watchosDeviceArm64`,
   `watchosSimulatorArm64`.

   ```kotlin
   // delete this whole block:
   watchosArm32 {
       binaries.framework { baseName = "<Name>"; xcf.add(this) }
   }
   ```

2. **`.github/workflows/watchos.yml`** — delete the
   `compileKotlinWatchosArm32` line from the build step. Keep the
   three 64-bit `compileKotlinWatchos*` lines and any
   `watchosSimulatorArm64Test`.

3. **Source sets with `actual` declarations** — delete
   `src/watchosArm32Main/` and `src/watchosArm32Test/` if either
   exists. If a repo used per-target `actual` (rather than a shared
   `watchosMain` actual), those files become orphan dead code after
   the target is removed. Inventory and delete:

   ```bash
   find src -maxdepth 3 -type d -name 'watchosArm32*' 2>/dev/null
   # Each hit is a directory tree to delete after confirming the
   # actuals are mirrored by the watchosMain/appleMain shared source set
   # or by per-target actuals on the surviving watchOS variants.
   ```

   If an `expect` declaration in `commonMain` previously had its only
   `actual` in `watchosArm32Main`, you must add the matching `actual`
   to `watchosArm64Main` / `watchosDeviceArm64Main` /
   `watchosSimulatorArm64Main` (or hoist it to a shared `watchosMain`
   intermediate source set) before deleting the armv7k actual. The
   compiler will tell you with `expected declaration has no actual`
   if you skip this — never `@Suppress` it.

**Verification after scrub.**

```bash
git grep -i 'watchosarm32'
# Expect: zero hits outside automation-artifacts/ and historical
# memory/feedback files.

./gradlew build --dry-run --no-daemon --console=plain --no-configuration-cache
# Expect: no *WatchosArm32* tasks in the graph.

./gradlew build --no-daemon
# Expect: BUILD SUCCESSFUL; watchosArm64 / Device / Simulator tasks
# still present.
```

**`fullTargetBuildTaskNames` already drops the two lines** (§5
example block above). Removing the target without removing the
build-gate lines causes `tasks.named("build") { dependsOn(…) }` to
fail with "Task 'watchosArm32Binaries' not found in root project."

**Not a gate-shrink violation.** Memory rule
`feedback_never_shrink_target_gate.md` forbids ad-hoc per-repo gate
filtering to dodge an individual CI failure. This is the opposite: a
documented, principled, workspace-wide template change with stated
Apple-policy + Mach-O-format justification, applied uniformly to the
template and every repo. The rule still holds for everything else.

**Rollout details + per-repo PR shape** are in
`automation-artifacts/2026-05-24-drop-watchos-arm32/PROPOSAL.md`.

### 5.5.2 Deprecated x86_64 Apple simulator targets — `tvosX64`, `watchosX64`, `macosX64`

JetBrains marked these three Apple x86_64 simulator/native targets
**deprecated as of Kotlin 2.3.20**. They have working hardware (Intel
Macs still exist) but the toolchain is on a removal trajectory and
Apple no longer ships new Intel Mac hardware. Workspace policy: drop
them from any repo that declares them.

| Target | Kotlin/Native classification | What it covers | Status |
|---|---|---|---|
| `tvosX64` | Deprecated (Kotlin 2.3.20) | tvOS simulator on Intel Macs | Drop |
| `watchosX64` | Deprecated (Kotlin 2.3.20) | watchOS simulator on Intel Macs | Drop |
| `macosX64` | Deprecated (Kotlin 2.3.20) | macOS native + simulator on Intel | Drop |

**Why** (separate from §5.5.1's Apple-hardware reasoning):

- Kotlin 2.3.20 marks all three as deprecated. Compilation against
  them now emits deprecation warnings, which fail
  `allWarningsAsErrors=true` (the starlark template default).
- Intel Mac production ended in 2023; current Xcode 16+ requires
  Apple Silicon. The hardware that runs these targets natively is
  EOL.
- The Apple-Silicon-Mac simulator targets (`tvosSimulatorArm64`,
  `watchosSimulatorArm64`) cover the same testing role on supported
  developer hardware.

**`tvosArm64` and `tvosSimulatorArm64` are KEPT.** All Apple TV
hardware has always been 64-bit ARM (A8 chip onwards, 2015). The
standard tvOS target shape (`tvosArm64` + `tvosSimulatorArm64`, no
`tvosX64`) is already what most repos ship. This section only affects
repos that explicitly declare the deprecated x86_64 simulator targets.

**`iosX64` is KEPT** (for now). It is Tier 3 in Kotlin/Native but
NOT deprecated. Apple still publishes x86_64 iOS simulator runtimes.
If JetBrains deprecates it in a future release, revisit then.

**Workspace audit on 2026-05-24** — only one repo declares the
deprecated targets:

| Repo | Targets to drop | Other cleanup |
|---|---|---|
| `ZLib.kotlin` | `tvosX64()`, `watchosX64()` (lines 53, 58 of `build.gradle.kts`) | Manual source-set wiring at lines 145–164 needs the matching `tvosX64Main`/`tvosX64Test`/`watchosX64Main`/`watchosX64Test` blocks deleted; `println` lines 254–256 reference `tvosX64Test` and `watchosX64Test` and need updating to the Arm64 simulator variants |

The scrub procedure is identical to §5.5.1 — three places to clean
(`build.gradle.kts`, `.github/workflows/<platform>.yml`, source-set
directories under `src/<target>Main` and `src/<target>Test`), plus the
`fullTargetBuildTaskNames` set if those entries were present.
Verification is the same `git grep` + dry-run + build pattern.

**`fullTargetBuildTaskNames` in the §5 example block already omits**
`tvosX64Binaries` / `watchosX64Binaries` / `macosX64Binaries` — they
were never in the standard template, so the build-gate gets no diff.
Only `ZLib.kotlin`'s repo-local equivalent (if any) needs updating.

**For new ports**: never add the three deprecated targets to a new
`build.gradle.kts`. If you're aligning a repo with the canonical
template, this is one of the things to scrub during alignment.

---

## 6. Test parity vs upstream Rust tests under `tmp/`

This is a translation workspace. **Every** upstream test gets an answer:

For each `tmp/**/tests/*.rs`, `tmp/**/benches/*.rs`, and `#[cfg(test)] mod
tests { … }` block:

- **If the test exercises behavior the Kotlin port can faithfully match,
  port it** to `src/commonTest/.../<File>Test.kt` (or per-target test
  source where commonTest can't express it). Use the upstream Rust file's
  organization; preserve test names translated to camelCase.
- **If the test depends on a Rust-specific semantic the Kotlin
  port can't reproduce** (`mem::forget`, pin-poll-once + `Pin` semantics
  that `kotlinx.coroutines` can't reproduce, custom `Drop`, raw pointer
  layout, `unsafe` UB checks), leave it unported with a one-line honest
  comment naming the specific semantic, **in the Kotlin test file at the
  position the port would go**, not in a separate document.
- **Never write a Kotlin "simulation" that tests a different invariant.**
  That's worse than skipping — it passes green while the real behavior
  isn't tested.

Wire `swift test` into the Kotlin `test` task so the Swift Export
boundary is covered by the same gate. Pattern (already in
schemars-kotlin / itertools-kotlin / kasuari-kotlin):

```kotlin
val swiftTest by tasks.registering(Exec::class) {
    dependsOn("embedSwiftExportForXcode")
    workingDir = file("swift-test-harness")
    commandLine("swift", "test", "--package-path", ".")
    // Treat any non-zero exit as a build failure.
    isIgnoreExitValue = false
}

tasks.named("test") {
    dependsOn(swiftTest)
}
```

If `swift-test-harness/` isn't present yet, the per-repo
`SWIFT_EXPORT_ROLLOUT.md` documents how to scaffold it — it's part of the
five canonical rollout changes.

---

## 7. Workflow-shape audit (paid-CI guard)

GitHub Actions across this org runs manual-only after the May 2026 cost
incident. Audit every focused repo's `.github/workflows/*.yml` at the
**start** of every session — even if another agent claims they already
fixed it.

The historical bad shape that must not regress:

```yaml
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

The quiet patterns to enforce:

| File | Triggers |
|---|---|
| `ci.yml` | `workflow_dispatch:` only |
| `codeql.yml` | weekly `schedule` + `workflow_dispatch:` |
| `publish.yml` | `release` `types: [released]` + `workflow_dispatch:` |
| `swift.yml`, `ios.yml`, `linux.yml`, `windows.yml`, `js.yml`, `macos.yml`, `tvos.yml`, `watchos.yml`, `android.yml`, `android-native.yml`, `wasm.yml`, `wasm-wasi.yml` | `workflow_call:` only |

When a workflow still has the old paid trigger AND is enabled remotely,
avoid spending a full CI run on the fix push: use
`gh workflow disable <workflow>`, push the trigger change, then
`gh workflow enable <workflow>`. If `gh workflow disable` is blocked,
report the exact blocker and still make the YAML change locally.

**Every session report must include a workflow-shape audit line per
focused repo:** which files were checked, whether any `push`/
`pull_request` triggers remain, whether any remote workflows were
disabled/re-enabled, and the commit that changed them.

---

## 8. Android SDK normalization (Gradle-backed, no shell)

When a repo still has `setup-android-sdk.sh` / `setup-android-sdk.bat`,
normalize it to the Gradle-backed pattern. Reference implementations:

- `serial-test-kotlin` `dc29a78` — "Move Android SDK setup into Gradle"
- `anyhow-kotlin` `0fd90ee` — "Move Android SDK setup into Gradle"

The full pattern (not just the task name):

1. Delete tracked `setup-android-sdk.sh` / `setup-android-sdk.bat`.
2. Add imports to `build.gradle.kts`: `ByteArrayInputStream`, `URI`,
   `Files`, `StandardCopyOption`, `ZipInputStream`, `GradleException`.
3. Pin SDK versions exactly:
   ```kotlin
   val androidCommandLineToolsRevision = "14742923"
   val projectCompileSdk = "34"
   val projectAndroidBuildTools = "36.0.0"
   ```
4. Keep the SDK project-local under `.android-sdk/`; write/refresh
   `local.properties` with `sdk.dir=<repo>/.android-sdk` on every run.
5. Host OS detection covers macOS, Linux, Windows (with `sdkmanager.bat`).
6. Add an `.install-complete` marker **plus** a real package-presence
   predicate:
   ```kotlin
   val requiredAndroidSdkPackageDirs = listOf(
       projectAndroidSdkDir.resolve("platform-tools"),
       projectAndroidSdkDir.resolve("platforms/android-$projectCompileSdk"),
       projectAndroidSdkDir.resolve("build-tools/$projectAndroidBuildTools"),
   )
   fun isProjectAndroidSdkInstalled(): Boolean =
       androidSdkInstallMarker.exists() &&
           androidSdkManager.exists() &&
           requiredAndroidSdkPackageDirs.all { it.exists() }
   ```
   A marker alone is not enough — if a package directory is missing,
   reinstall.
7. `sdkManagerCommand(...)` helper (direct on Unix, `cmd /c` on Windows).
8. Download command-line tools via `URI(...).toURL().openStream()` (no
   `curl`).
9. Extract with `ZipInputStream`; guard against zip-slip by resolving
   entries against canonical `cmdline-tools/latest/`.
10. Preserve executable permissions on non-Windows SDK binaries.
11. Accept licenses non-interactively from Gradle via a finite
    `ByteArrayInputStream` of `y\n` answers.
12. Install `platform-tools`, `platforms;android-34`,
    `build-tools;36.0.0`.
13. Preserve SDK manager output in `.android-sdk/sdkmanager-install.log`.
14. Fail with clear `GradleException` including log contents.
15. Call `installProjectAndroidSdk(...)` at configuration time **before**
    `kotlin { android { … } }` — the Android Gradle plugin resolves SDK
    location during configuration.
16. `tasks.register<Exec>("setupAndroidSdk")` → Kotlin-backed task that
    calls `installProjectAndroidSdk(...)`; do **not** shell out.

Workflow side: every workflow command that touches Android must invoke
`setupAndroidSdk` first:

- `android.yml`: before `compileAndroidMain`, `assembleUnitTest`,
  `assembleAndroidTest`.
- `codeql.yml`: before `compileAndroidMain`, `compileKotlinJs`,
  `compileKotlinWasmJs`.
- `publish.yml`: before the dry-run `compileAndroidMain
  androidSourcesJar` and before `publishAndReleaseToMavenCentral`.
- `windows.yml`: even for `mingwX64Test`, run `setupAndroidSdk` first —
  it's the Windows-runner proof that `setupAndroidSdk` is callable
  through Gradle alone. No WSL, no bash, no batch, no PowerShell
  duplicate installer.

Verify:

```bash
rg -n "setup-android-sdk\.sh|setup-android-sdk\.bat|androidSdkSetupCommand|bash.*setup-android-sdk|cmd.*setup-android-sdk" \
  . -g '!build/**' -g '!.gradle/**' -g '!.android-sdk/**'
rg -n "setupAndroidSdk|compileAndroidMain|assembleUnitTest|androidSourcesJar|assembleAndroidTest|publishAndReleaseToMavenCentral" \
  .github/workflows -g '*.yml'
./gradlew setupAndroidSdk --no-daemon --console=plain --no-configuration-cache
./gradlew setupAndroidSdk --no-daemon --console=plain --no-configuration-cache  # must hit cached path
./gradlew compileAndroidMain --no-daemon --console=plain --no-configuration-cache
./gradlew build --dry-run --no-daemon --console=plain --no-configuration-cache
./gradlew test --no-daemon --console=plain --no-configuration-cache
git diff --check
```

### CodeQL `java-kotlin` extraction

KMP repos with an `android` target use `compileAndroidMain` as the CodeQL
extraction command (the Android target emits real `.class` files the
LD_PRELOAD tracer can hook). `compileKotlinMetadata` extracts zero TRAP.
The full pattern + the required `--rerun-tasks --no-build-cache
-Pkotlin.compiler.execution.strategy=out-of-process --no-configuration-cache
--no-daemon` flags lives in §"CodeQL `java-kotlin`" of the previous
revision of this file; preserve it when porting workflow YAML.

`codeqlAndroidAar` extraction must use the dedicated configuration
pattern from `syn-kotlin`, not mix AARs into `codeqlSourceClasspath`. If
no real published Android AAR dependency exists yet, do not add fake
coordinates — note in the PR/run report that no published AAR
dependency exists.

---

## 9. JS test-toolchain security patching

Security warnings from Kotlin/JS npm tooling (`glob@7`, `inflight`,
`rimraf@3`, old `karma-webpack`, stale `karma` / `mocha` / `webpack` /
`webpack-cli`) are real supply-chain findings. Patch via the vendored
`karma-webpack` + yarn-resolution pattern. The full block (with all
current pins) is below — copy it verbatim into `build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec

rootProject.extensions.configure<NodeJsEnvSpec>("kotlinNodeJsSpec")     { version.set("22.22.2") }
rootProject.extensions.configure<WasmNodeJsEnvSpec>("kotlinWasmNodeJsSpec") { version.set("22.22.2") }
rootProject.extensions.configure<YarnRootEnvSpec>("kotlinYarnSpec")     { version.set("1.22.22") }
rootProject.extensions.configure<WasmYarnRootEnvSpec>("kotlinWasmYarnSpec") { version.set("1.22.22") }

rootProject.extensions.configure<YarnRootExtension>("kotlinYarn") {
    resolution("diff", "8.0.3")
    resolution("**/diff", "8.0.3")
    resolution("fast-uri", "3.1.2")
    resolution("**/fast-uri", "3.1.2")
    resolution("serialize-javascript", "7.0.5")
    resolution("**/serialize-javascript", "7.0.5")
    resolution("webpack", "5.106.2")
    resolution("**/webpack", "5.106.2")
    resolution("follow-redirects", "1.16.0")
    resolution("**/follow-redirects", "1.16.0")
    resolution("lodash", "4.18.1")
    resolution("**/lodash", "4.18.1")
    resolution("ajv", "8.20.0")
    resolution("**/ajv", "8.20.0")
    resolution("brace-expansion", "5.0.6")
    resolution("**/brace-expansion", "5.0.6")
    resolution("flatted", "3.4.2")
    resolution("**/flatted", "3.4.2")
    resolution("minimatch", "10.2.5")
    resolution("**/minimatch", "10.2.5")
    resolution("picomatch", "4.0.4")
    resolution("**/picomatch", "4.0.4")
    resolution("qs", "6.15.2")
    resolution("**/qs", "6.15.2")
    resolution("socket.io-parser", "4.2.6")
    resolution("**/socket.io-parser", "4.2.6")
    resolution("ws", "8.20.1")
    resolution("**/ws", "8.20.1")
}

val patchedKarmaWebpackPackage =
    rootProject.layout.projectDirectory.dir("gradle/npm/karma-webpack").asFile.absolutePath.replace("\\", "/")

rootProject.extensions.configure<NodeJsRootExtension>("kotlinNodeJs") {
    versions.webpack.version = "5.106.2"
    versions.webpackCli.version = "7.0.2"
    versions.karma.version = "npm:karma-maintained@6.4.7"
    versions.karmaWebpack.version = "file:$patchedKarmaWebpackPackage"
    versions.mocha.version = "12.0.0-beta-10"
    versions.kotlinWebHelpers.version = "3.1.0"
}
```

Vendored `karma-webpack` package shape (under
`gradle/npm/karma-webpack/package.json`) is in the per-repo
`SWIFT_EXPORT_ROLLOUT.md` / canonical anstyle-kotlin. Regenerate
lockfiles via:

```bash
./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock --rerun-tasks
```

Never hand-edit `kotlin-js-store/yarn.lock` or
`kotlin-js-store/wasm/yarn.lock`. If `kotlinStoreYarnLock` reports the
lock file changed, run the upgrade task — that failure is the guardrail.

Prove:

```bash
rg -n '"karma-webpack"' build/js/packages -g package.json
rg -n "glob@\^7|rimraf@\^3|inflight@|karma-webpack@5\.0\.1|karma@github:Kotlin/karma|mocha@11|webpack@5\.101|webpack-cli@6" \
  kotlin-js-store build/js/yarn.lock build/wasm/yarn.lock -g yarn.lock
./gradlew jsNodeTest wasmJsNodeTest macosArm64Test
./gradlew test
```

### Dependabot push-time warnings = act immediately

If `git push` prints a `remote: GitHub found N vulnerability` banner,
open a `security/<pkg>-<patched>` branch + PR in the same session. Bump
the `kotlinYarn` resolution, regenerate locks via
`kotlinUpgradeYarnLock` / `kotlinWasmUpgradeYarnLock --rerun-tasks`,
verify with `jsNodeTest` / `wasmJsNodeTest`. Don't footnote it.

---

## 10. Kotlin/Native + Kotlin/JS gotchas

### `kotlin.native.parallelThreads=0` in `gradle.properties`

Every native compile/link step on `macos-latest` Apple-Silicon free tier
emits `w: The number of threads 4 is more than the number of processors
3`. With `allWarningsAsErrors=true` (which the starlark template sets),
this is a hard failure waiting on the next runner SKU bump.

```properties
kotlin.native.parallelThreads=0
```

`0` = "one thread per processor core" — adapts automatically; no number
to maintain. **Do not** scope `-Xbackend-threads=0` via `freeCompilerArgs`
on native targets — the Kotlin Gradle plugin appends its own
`-Xbackend-threads=4` to link tasks *after* user `freeCompilerArgs`, so
"last wins" gives 4 and the warning comes back.

### `js` / `wasmJs` source-set split

`browserMain` / `nodeMain` intermediate source sets are the structural
split that keeps Node-only code out of the webpack browser bundle.
Without it, `jsMain` is shared across both runtimes and webpack can't
resolve `fs`.

### `require('fs')` via `(new Function('return require'))()`

For `jsMain` / `wasmJsMain` `actual`s that call Node-only APIs through
`js("…")`, hide `require('fs')` from webpack's static scanner. **The
intuitive `eval('require')` form is WRONG** — webpack's `eval-source-map`
devtool mangles it at runtime with `SyntaxError: Unexpected token ':'`.

```kotlin
// jsMain — dynamic + IIFE return
private fun jsReadFile(path: String): dynamic = js(
    "(function(){ try { var rq = (new Function('return typeof require === \"function\" ? require : null'))(); if (!rq) return undefined; return rq('fs').readFileSync(path, 'utf-8'); } catch (e) { return undefined; } })()",
)

// wasmJsMain — typed return, body wrapped in { } because js(…) compiles to (args) => BODY
private fun jsReadFile(path: String): String? = js(
    "{ try { var rq = (new Function('return typeof require === \"function\" ? require : null'))(); if (!rq) return null; return rq('fs').readFileSync(path, 'utf-8'); } catch (e) { return null; } }",
)
```

Add an inline comment at the call site documenting the trick — it's easy
to re-trip in a "cleanup" commit.

### Parenthesised `DefinePlugin` substitutions in `karma.config.d/*.js`

Unparenthesised `{…}` at statement-start position parses as a block and
explodes `jsBrowserTest` with `Uncaught SyntaxError: Unexpected token ':'`.
Always wrap: `({ KEY: "value" })`.

### `posixMain` — avoid `size_t` in shared signatures

`fread` / similar break `posixMain` metadata compilation because
`size_t` width differs between the 32-bit native targets
(`androidNativeArm32`, `androidNativeX86` — `size_t = UInt`) and the
64-bit targets (`size_t = ULong`). Use `fgetc` / `Int`-returning
helpers or push the call to per-leaf-target Platform files.

*Historical note.* This used to bite `watchosArm32` too — armv7k is
also 32-bit. After the 2026-05-24 retirement of `watchosArm32` (§5.5),
the surviving 32-bit native targets are all Android Native. The rule
is the same; only the offending target list shrank.

### `*-sys-kotlin` ports — port real FFI, don't punt

Wire Kotlin/Native `cinterop` and (where applicable) Node N-API in the
same session. Never label FFI work "next pass."

---

## 11. Branch + PR ops — the session shape

A normal session is:

1. **Audit + sync local main.** `git fetch --prune`. List local + remote
   branches, open PRs, `git status --short --branch`. If on `main` and
   behind `origin/main` (the common case after Sydney's manual PR sweeps
   landed merges remotely), sync local main:
   ```bash
   git switch main
   git pull --ff-only origin main
   ```
   `--ff-only` makes the command fail loud if local `main` has somehow
   diverged — which it never should under PR-only rules. Inspect every
   focused repo's workflow shape (§7).
2. **Reconcile every non-main branch** through §2: merge to main via
   no-ff, close PRs, delete remote refs proven ancestors of `main`,
   **undelete and reconcile** any `[gone]` branches with unmerged commits.
3. **Pick the focused repo.** Stalest first (§3). Read its `AGENTS.md`,
   `CLAUDE.md`, `README.md`, `SWIFT_EXPORT_ROLLOUT.md` if present.
4. **Workflow-shape, JS-toolchain security, Android SDK, build-gate
   audits** — repair any defect found locally, commit, push.
5. **Swift Export sweep** if `swiftExport { … }` is configured. Run
   `embedSwiftExportForXcode` + `swift test` locally; fix every hazard
   class from §4 in the same session.
6. **Test parity audit** (§6). Port what's missing in this session.
7. **Source-porting advance** per the repo's `CLAUDE.md`. Real Kotlin
   implementation/test changes, not workflow churn.
8. **Local gate.** `./gradlew build` (full) + `./gradlew test` (which
   includes `swift test` after wiring). Fix every failure.
9. **Commit ALL dirty (§1).** `git status --short` must be empty by
   end of session. Never `git stash`. Never orphan work on a switched
   branch. If dirty on `main`, branch first; if dirty on a feature
   branch, commit there.
10. **PR + merge via `gh`, never locally.** Push the branch, open PR
    with `gh pr create` using the canonical body, merge with
    `gh pr merge --merge --delete-branch <PR>` once checks are green.
    Add a one-sentence `gh pr comment` describing what was verified
    locally (build target, swift test, security gate). Never
    `git switch main && git merge` to land work.
11. **Version + release** if there's user-visible advance to publish:
    bump `version` in `build.gradle.kts` + every install snippet in
    `README.md`, commit, push, `gh release create v<X.Y.Z>` to fire
    the `release[released]` publish workflow.
12. **Report** (§12).

---

## 12. Session report — always at the end

Every session ends with a report that includes:

- **Workflow-shape audit** per focused repo (which files checked, what
  triggers remain, anything disabled/re-enabled).
- **Focused repo(s).**
- **Branch used.**
- **Constructive outcome** OR the **exact hard blocker** that prevented
  one (named at the level of §0).
- **PRs and branches** inspected, merged, or closed.
- **Local commands run and outcomes.**
- **Remote workflow state** if checked (run URL + conclusion for any
  platform job that's the only proof for a target the local host can't
  validate).
- **Commits made.**
- **Unresolved blockers.**
- **PR list at the very end** as full clickable GitHub URLs (not bare
  `#N`). If zero PRs, say `PRs: none this session` explicitly.

---

## 13. Quick reference — directories and tools

- Workspace root: `/Volumes/stuff/Projects/kotlinmania`
- Per-repo: `/Volumes/stuff/Projects/kotlinmania/<repo>-kotlin/`
- Automation artifacts:
  `/Volumes/stuff/Projects/kotlinmania/automation-artifacts/`
- Canonical Swift Export rollout:
  `automation-artifacts/swift-export-rollout/` (apply.sh, blast.sh,
  PR_BODY.md, SWIFT_EXPORT_ROLLOUT.md, triage docs)
- Canonical CI/build template repo: `http-kotlin`
- Canonical Swift Export reference: `schemars-kotlin` (first non-trivial
  repo fully green) and `anstyle-kotlin` (canonical SWIFT_EXPORT_ROLLOUT.md)
- Canonical Android SDK normalization: `serial-test-kotlin` `dc29a78`,
  `anyhow-kotlin` `0fd90ee`
- Port priority: `PORT_PRIORITY.md` / `port_priority.json` (auto-generated;
  don't hand-edit)
- Generator: `python scaffold/analysis/generate_port_priority.py`
- Apply to Jira: `python scaffold/jira/apply_ordered_priority.py`
- Local CodeQL: `codeql` 2.25.4 at `/Volumes/stuff/tools/codeql`,
  symlinked to `/opt/homebrew/bin/codeql`
- Local Dependabot: `dependabot` 1.86.0 via Homebrew; needs
  `LOCAL_GITHUB_ACCESS_TOKEN=$(gh auth token)` env var

### Repo-state blockers carried forward (from memory)

- `clap-complete-kotlin` blocked on `clap-kotlin` publish (no Maven Central
  artifact yet at 0.1.0/0.1.1/0.1.2).
- `nucleo-kotlin` blocked on `nucleo-matcher-kotlin` (every `src/*.rs`
  imports from sibling crate; infra-only work still valid).
- `tonic-prost-kotlin` blocked on tonic-kotlin + prost-kotlin (both
  effectively empty).
- `indexmap-kotlin` needs `hashbrown-kotlin` + `equivalent-kotlin` (neither
  exists yet).
- `itertools-kotlin` needs `adaptors/{mod,coalesce,map}.rs` + `free.rs` +
  `lib.rs` ported before downstream files (`diff`, `cons_tuples`,
  `multipeek`); smallest leaf files all have unported deps.
- `tonic-prost-kotlin` codec needs Codec/Encoder/Decoder/EncodeBuf/
  DecodeBuf/BufferSettings/Status from tonic-kotlin + Message from
  prost-kotlin.
- `libwebrtc-kotlin` all-target build blocked on `bytes-kotlin` watchOS
  slices (no watchOS publication yet); use `macosArm64Test` as local
  proof until bytes-kotlin publishes watchOS.
- `constant-time-eq-kotlin` `src/lib.rs` is fully ported in `Lib.kt`;
  `tests/count_instructions.rs` and `benches/bench.rs` aren't portable.
  Don't retranslate.
- `env-logger-kotlin` writer module ported 2026-05-22; remaining work
  is `src/lib.rs`, `src/logger.rs`, `fmt/{mod,humantime,kv}.rs`.

### Authority files in this workspace (read order)

1. `/Volumes/stuff/Projects/kotlinmania/AGENTS.md` (this file).
2. `/Volumes/stuff/Projects/kotlinmania/CLAUDE.md` (legacy; this file
   is the merged authority).
3. `/Volumes/stuff/Projects/kotlinmania/BUILD_TEMPLATE_HANDOFF.md`
   (template diffing target).
4. Per-repo `AGENTS.md`, `CLAUDE.md`, `README.md`,
   `SWIFT_EXPORT_ROLLOUT.md`.
5. `automation-artifacts/swift-export-rollout/` for the rollout script
   bundle.
6. `automation-artifacts/2026-05-19-kotlinmania-ci-hourly-roster/RUNBOOK.md`
   for the persisted hourly runbook.
