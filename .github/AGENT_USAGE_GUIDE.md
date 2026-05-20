# Multi-Agent Workflow Guide

> **Version:** 2.1 — May 2026
> All agent files are at `.github/agents/` and versioned at `2.1`.

This document explains how to use the real-newsletter multi-agent system for managing feature development, testing, code review, and deployment.

---

## ⚡ Quick Start — Run the Full Pipeline in One Command

The fastest way to ship a feature is to hand the issue number to the **Pipeline agent** and let it drive every stage automatically:

```
@pipeline Run pipeline for issue #42
```

That single prompt runs: **Planner → Coder → Reviewer → Test (conditional) → DevOps** — no manual hand-offs required.

### What the Pipeline Agent Does
1. **Plans** — calls Planner to decompose the issue into implementation issues *(or skips if the issue is already focused)*
2. **Codes** — calls Coder for each implementation issue; Coder runs `mvn clean test jacoco:report` locally and reports coverage %
3. **Reviews** — calls Reviewer immediately after the Coder; loops back to Coder up to **3 times** if changes are requested
4. **Tests (conditional)** — calls Test agent **only when line coverage < 30%** after Reviewer approval; skipped otherwise
5. **Deploys** — calls DevOps to build, release, and close issues in **milestone batches** (default: every 5 merged PRs, or at end-of-run)
6. **Reports** — posts progress comments on the original GitHub issue at every stage, including ⏱️ duration per agent

### Milestone Deployment Batching
The Pipeline does **not** run DevOps after every individual issue. Merged PRs accumulate in a batch and DevOps fires when a **milestone** is reached:

- **Default milestone size:** 5 issues (override with `--milestone-size N`)
- DevOps runs when the batch is full **or** at end-of-run (final flush)
- One batch → one version bump → one GitHub release → all issues in the batch closed together

```
@pipeline Run pipeline for issues #43 #44 #45
@pipeline Run pipeline for issues #43 #44 #45 --milestone-size 3
```

### Triggering via GitHub Actions
You can also trigger the pipeline without opening the IDE:

| Method | How |
|--------|-----|
| **Label** | Add the `run-pipeline` label to any GitHub issue |
| **Manual dispatch** | Go to **Actions → Agent Pipeline → Run workflow**, enter the issue number |

Both methods post a pipeline-started comment on the issue and validate your secrets before any agent work begins.

---

## Prerequisites

### Environment Setup

Before invoking any agent, ensure the following are installed and configured:

| Tool | Minimum Version | Check |
|------|----------------|-------|
| `git` | 2.0 | `git --version` |
| `gh` (GitHub CLI) | 2.20 | `gh --version` |
| `java` | 21 (LTS) | `java -version` |
| `maven` | 3.8 | `mvn --version` |
| `GITHUB_TOKEN` | — | `echo $GITHUB_TOKEN` |

### Load Environment Variables

Before invoking any custom agent, load the repository `.env` values into your current PowerShell session:

```powershell
.\scripts\load-env.ps1
```

This ensures `GITHUB_TOKEN` is set for all agents and automatically mapped to GitHub MCP auth (`GITHUB_PERSONAL_ACCESS_TOKEN`).

### Required Token Scopes

`GITHUB_TOKEN` must have the following scopes:

| Scope | Used by |
|-------|---------|
| `repo` (read + write) | All agents (read issues, create PRs, push branches) |
| `workflow` | Pipeline (trigger GitHub Actions) |

Verify scopes with: `gh auth status --show-token`

---

## Fully Autonomous Execution

All agents are configured with `autonomousExecution: true` in their frontmatter. This means **no confirmation prompts of any kind** — agents run from start to finish without pausing.

| Action type | Behaviour |
|---|---|
| File edits and saves | Executed immediately — no "Allow this change?" dialog |
| Terminal / shell commands (`git`, `mvn`, `gh`, `docker`, etc.) | Executed immediately — no "Run this command?" dialog |
| MCP API calls (GitHub create issue, merge PR, etc.) | Executed immediately — no "Allow this tool call?" dialog |
| Long-context continuation | Continues automatically — no "Continue?" prompt |

> ⚠️ **Every agent runs a prerequisite verification step (Step 0 / Stage −1) before doing any work.** It checks that `gh` is authenticated, `GITHUB_TOKEN` is set, Maven is available, and the target issue/PR is in the expected state. If any check fails the agent halts immediately with a structured Error Report before making any changes.

> ⚠️ Because agents act without asking, make sure `GITHUB_TOKEN` has the correct scopes **before** invoking any agent. An agent with broad permissions and no confirmation gate will take real, irreversible actions (push commits, merge PRs, create releases).

---

## Pipeline Architecture

```
Issue Number(s)
      │
      ▼
 Stage −1  Prerequisite verification (all agents run this first)
      │
      ▼
 [1] Planner  ───→ Creates implementation issues  ⏱️ plannerDuration
      │  (skip if issue is already focused)
      ▼
 [2] Coder    ───→ feature branch + code + tests + PR  ⏱️ coderDuration
      │  (idempotency: resumes existing branch/PR if re-triggered)
      ▼
 [3] Reviewer ───→ APPROVE or REQUEST_CHANGES (max 3 cycles)  ⏱️ reviewerDuration
      │  ├─ draft PR guard — will not review draft PRs
      │  ├─ CI checks must pass before approval
      │  └─ CHANGES_REQUESTED → back to [2] Coder
      ▼
 [4] Test     ───→ CONDITIONAL — runs ONLY when coverage < 30%  ⏱️ testDuration
      │  (adds tests to raise coverage to ≥ 70%, max 3 rounds)
      ▼
 [★] Batch Gate ─→ Accumulate in pendingDeployment
      │  IF batch < milestoneBatchSize AND more issues remain → next issue → [2]
      │  IF batch full OR final flush → [5]
      ▼
 [5] DevOps   ───→ one build → one version bump → one release → close all issues  ⏱️ devopsDuration
      ▼
     Done ✅

Branch strategy:  feature/* → develop  (automated, via this pipeline)
                  develop   → main      (manual, production promotion gate)
```

> **Integration branch:** The pipeline targets `develop` by default. If `develop` does not exist, it falls back to `main`. All agent work is performed against the integration branch — only manual production promotion touches `main`.

Each agent records precise start/end timestamps. The pipeline tracks and reports **⏱️ duration per agent and per issue** in a summary table at the end.

---

## 1. Planner Agent

**Purpose:** Decompose GitHub issues into actionable implementation work, create implementation issues, and identify the next issue ready to be worked on.

**When to Use:** Start here whenever you have a new feature request, enhancement, or bug fix — or when you want to know what to work on next from the existing backlog.

### How to Invoke

1. Create a GitHub issue with a clear title, description, and labels (`feature`, `bug`, `backend`, etc.)

2. Ask the Planner agent to plan the work:
   ```
   @planner Please plan issue #42
   ```

3. Or, to identify the next issue from the existing backlog without planning a new one:
   ```
   @planner What should we work on next?
   ```

### What the Planner Does

1. **Prerequisite check (Step 0)** — verifies `gh auth`, `GITHUB_TOKEN`, and that the issue is open
2. **In-progress check** — runs `gh pr list --search "closes #{N}" --state open` before decomposing; if an open PR already references this issue it reports that work is already underway and does **not** create duplicate sub-issues
3. Reads the full GitHub issue (title, body, comments, labels)
4. Checks for existing sub-issues to avoid duplication
5. Creates focused implementation issues, each with:
   - Summary, Rationale, Scope & Tasks checklist, Acceptance Criteria
   - Labels including `implementation` (auto-created if missing)
   - Assigned to the active milestone (if one exists)
6. **Identifies the next issue to work on** by analysing the full backlog dependency graph:
   - Scans every open issue body for dependency patterns (`depends on #N`, `blocked by #N`, `requires #N`, `after #N`)
   - Classifies each open issue as 🟢 **Ready**, 🔴 **Blocked**, or ⚙️ **In Progress** (has an open PR)
   - Ranks Ready issues by label priority (`bug` > `feature` > `infra` > `docs`) then milestone proximity then issue number
   - Posts a 🎯 comment on the recommended issue and hands off to the Coder

### Scope Ambiguity Handling

If the issue scope is too ambiguous to decompose, the Planner will:
1. Post a comment listing exactly which parts are unclear and what information is needed
2. Add the `needs-clarification` label to the issue (created automatically if missing)
3. **Halt with an Error Report** — it will not guess at scope or create poorly-scoped sub-issues

### Example Output

```markdown
## Summary
Issue #42 requests real-time news ingestion from RSS feeds.

## New Issues Created
- #43 [Add RSS feed ingestion service](url) — backend parsing and storage
- #44 [Add /api/feeds REST endpoint](url) — paginated REST endpoint
- #45 [Add database schema for news articles](url) — JPA entity and migration

## Dependencies
- #45 must be completed before #43 and #44
- #43 and #44 can be worked on in parallel after #45

## Next Issue
| # | Title | Status | Blocking |
|---|-------|--------|----------|
| #45 | Add database schema | 🟢 Ready | — |
| #43 | Add RSS ingestion service | 🔴 Blocked | #45 |
| #44 | Add /api/feeds endpoint | 🔴 Blocked | #45 |

🎯 **Next up: #45 — Add database schema for news articles**
```

---

## 2. Coder Agent

**Purpose:** Implement code changes based on implementation issues created by the Planner.

**When to Use:** After the Planner creates an implementation issue.

### How to Invoke

```
@coder Please implement issue #43
```

### What the Coder Does

1. **Prerequisite check (Step 0)** — verifies `gh auth`, `GITHUB_TOKEN`, Maven, and confirms the issue state is `OPEN`
2. Reads the implementation issue (title, scope & tasks, acceptance criteria)
3. Analyses the existing codebase structure
4. **Idempotency check** — checks if `feature/issue-{N}` already exists remotely and if an open PR already exists; resumes from the existing branch/PR rather than recreating them (safe to re-trigger after a partial failure)
5. Creates feature branch from `develop`: `git checkout develop && git pull origin develop && git checkout -b feature/issue-{N}`
6. Implements production-quality code following Spring Boot conventions
7. Validates Dockerfile and `docker-compose.yml` consistency if container config changes
8. Runs `mvn clean test jacoco:report` — **build and all tests must pass before PR is created**
9. Extracts line coverage % from JaCoCo XML (supports both Linux `awk` and Windows PowerShell)
10. **Rebases onto `develop`** before pushing: `git fetch origin && git rebase origin/develop` (keeps history linear, prevents merge conflicts)
11. Commits with conventional commit format: `feat: description (closes #{N})`
12. Pushes and creates a PR with `--label "implementation"` and build summary in the description

### Code Standards

| Concern | Standard |
|---------|---------|
| **Package structure** | `com.realnewsletter.{model\|repository\|service\|controller}` |
| **Dependency injection** | Constructor-based only — no `@Autowired` field injection |
| **Controllers** | Thin — delegate all business logic to service classes |
| **Services** | Business logic, `@Transactional` on write methods |
| **Repositories** | Spring Data JPA `JpaRepository` extensions |
| **Error handling** | `@ControllerAdvice`, custom exceptions |
| **Logging** | SLF4J — appropriate levels (`DEBUG`/`INFO`/`WARN`/`ERROR`) |
| **Commit format** | `feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:` — no `WIP` commits |
| **Branch naming** | `feature/issue-{N}` |
| **Tests** | Unit + integration tests required |

### Coverage Reporting

After `mvn clean test jacoco:report`, the Coder extracts coverage automatically:

```bash
# Linux/macOS
awk -F'"' '/<counter type="LINE"/{missed=$2; covered=$4; total=missed+covered;
  printf "%.1f%%\n", (total>0 ? covered/total*100 : 0)}' \
  target/site/jacoco/jacoco.xml | tail -1

# Windows PowerShell
$xml = [xml](Get-Content target/site/jacoco/jacoco.xml)
$line = $xml.report.counter | Where-Object { $_.type -eq 'LINE' }
"{0:F1}%" -f ([int]$line.covered / ([int]$line.missed + [int]$line.covered) * 100)
```

The `coveragePct` value is included in the PR description and passed in the handoff to the Reviewer.

### Example Output

```markdown
## Git Workflow
- Feature branch: `feature/issue-43`
- Base branch: `develop` (rebased before push)
- Commits: `feat: add RSS feed ingestion service (closes #43)`

## Implementation Summary
Implemented RssFeedService that fetches and parses RSS 2.0/Atom feeds and stores
articles via NewsArticleRepository. Includes error handling and SLF4J logging.

## Changes Made
- `src/main/java/com/realnewsletter/model/NewsArticle.java` — JPA entity
- `src/main/java/com/realnewsletter/repository/NewsArticleRepository.java` — data access
- `src/main/java/com/realnewsletter/service/RssFeedService.java` — business logic
- `src/test/java/com/realnewsletter/service/RssFeedServiceTest.java` — unit tests

## Build & Test Summary
- Maven build: SUCCESS
- Unit tests: 12 passed / 0 failed
- Coverage (line): **72%** ✅ ≥ 30% — Test agent will be skipped after review

## Pull Request
- #51 [Add RSS feed ingestion service](url) — closes #43

## ⏱️ Time
- Duration: **3m 12s**

## Handoff
Implementation complete. Build passes, coverage 72%. Ready for Reviewer agent.
```

---

## 3. Reviewer Agent

**Purpose:** Conduct code review, validate architecture, enforce quality standards, and merge approved pull requests.

**When to Use:** Automatically invoked by the pipeline after the Coder. Or manually:

```
@reviewer Please review PR #51
```

### What the Reviewer Does

1. **Prerequisite check (Step 0)** — verifies `gh auth`, `GITHUB_TOKEN`, and confirms the PR is **not a draft**
   > ⛔ **Draft PRs are never reviewed.** If the PR is in draft state, the agent posts a comment asking the author to mark it ready, then halts.
2. **Verifies CI status** — runs `gh pr checks {N}` and confirms all required status checks pass (waits up to 5 minutes for in-progress checks). Does not approve PRs with failing checks.
3. **Verifies PR base branch** — confirms the PR targets the correct integration branch (`develop`); requests retarget if wrong
4. Checks the PR description contains the Coder's build summary (Maven SUCCESS + test counts + coverage %)
5. Reviews the diff for:
   - Spring Boot architectural alignment (controller → service → repository)
   - Coding standards, naming conventions, package structure under `com.realnewsletter.*`
   - Security: input validation, SQL injection prevention, no exposed stack traces, no committed secrets
   - Performance: N+1 queries, missing indexes, unbounded result sets
   - REST API contracts: HTTP methods, status codes, DTOs, OpenAPI annotations
   - Database schema changes: entity mappings, migration scripts, backward compatibility
   - PR quality: issue linkage, conventional commit messages, no unresolved TODOs or commented-out code
6. Verifies every **acceptance criterion** from the linked issue is addressed
7. Submits GitHub review: **APPROVE** or **REQUEST_CHANGES**

### Merge Decision After Approval

| Condition | Action |
|-----------|--------|
| Coverage **≥ 30%** | Merge immediately into `develop` via squash-and-merge → hand off to Batch Gate |
| Coverage **< 30%** | Do **NOT** merge — route to Test agent first; merge after Test raises coverage to ≥ 70% |
| Post-Test handoff | Merge immediately (no re-review) → hand off to Batch Gate |

**Squash merge command:**
```bash
gh pr merge {N} --squash --delete-branch \
  --subject "feat: {title} (#{pr_number})"
```

### Review Cycle Limit

The Reviewer → Coder → Reviewer loop is capped at **3 cycles per PR**:
- Cycles 1–3: request changes, hand off to Coder with full fix list and cycle number (`"Review cycle 2 of 3"`)
- Cycle > 3: post an Error Report on the PR and issue, add `pipeline-blocked` label, escalate to planner — **do not hand off to Coder again**

### Example Output

```markdown
## Review Summary
✅ APPROVED

## Architecture & Design
- Layered architecture: OK
- Dependency injection: OK (constructor-based)
- Separation of concerns: OK

## Code Quality
- Naming conventions: OK
- Error handling: OK (custom exceptions, proper HTTP codes)
- Documentation: OK (Javadoc on service methods)

## Security & Performance
- Input validation: OK
- Query efficiency: OK (no N+1 patterns)

## Acceptance Criteria
- [x] RSS 2.0 and Atom feeds parsed correctly
- [x] Articles stored with metadata (title, url, date, source)

## Merge
- PR #51 merged into `develop` via squash-and-merge ✅ (commit: `abc1234`)

## Review Cycle
- Current cycle: 1 of 3

## ⏱️ Time
- Duration: **5m 45s**

## Handoff
PR merged (commit: `abc1234`). Coverage 72% ≥ 30% — routing to Batch Gate / DevOps.
```

---

## 4. Test Agent *(Conditional)*

**Purpose:** Raise line coverage to ≥ 70% when it falls below 30% after Reviewer approval.

**When to Use:** The pipeline invokes the Test agent automatically — **only when the Coder's reported line coverage is below 30%**. At ≥ 30% the Test stage is skipped entirely and the pipeline proceeds directly to the Batch Gate.

To manually trigger:
```
@test Please verify PR #51
```

> ℹ️ The Reviewer has already approved the PR before this agent runs. Do not use this agent to re-validate code quality or architecture — its only job is coverage.

### What the Test Agent Does

1. **Prerequisite check (Step 0)** — verifies `gh auth`, `GITHUB_TOKEN`, Maven, and confirms the feature branch exists
2. Checks out the feature branch
3. Runs `mvn clean test jacoco:report` to establish a coverage baseline
4. Identifies under-covered classes/methods added in the PR (cross-references `gh pr diff` to focus on new code only)
5. Writes targeted tests to reach ≥ 70% (**max 3 total rounds**: 1 initial + 2 extra)
6. Commits and pushes the new tests to the feature branch
7. Posts a coverage before/after summary as a PR comment
8. Hands back to the **Reviewer** to merge the PR and route to DevOps

### Testing Standards

| Type | Annotation | When to use |
|------|-----------|------------|
| Unit tests | `@ExtendWith(MockitoExtension.class)` | Service/component logic |
| Repository slice tests | `@DataJpaTest` + `@Transactional` | New query methods — `@Transactional` auto-rolls back test data |
| Controller slice tests | `@WebMvcTest` | New REST endpoints |

**Rules:**
- **Test isolation**: each test must be fully independent — no shared mutable state, no execution-order dependency
- **Setup/teardown**: use `@BeforeEach` / `@AfterEach`; never rely on test execution order
- **Naming**: `should{ExpectedBehaviour}_when{Condition}()` (e.g., `shouldReturnArticles_whenFeedIsValid`)
- **Mocking**: use `@MockitoBean` / `@MockBean` — never call real external systems
- **No framework tests**: do not write tests that only exercise getter/setter or framework code with no application logic
- **Never modify production code** — if a test failure is caused by a logic error in production code, hand back to the Coder

### Max Rounds

- Round 1: initial test-writing pass
- Rounds 2–3: additional passes if coverage still below 70%
- After 3 rounds: post Error Report and hand back to Coder if remaining uncovered code cannot be tested without modifying production logic

### Example Output

```markdown
## Test Agent Summary
✅ Coverage raised to 72% — threshold met

## Coverage Report
| Metric        | Before | After | Target |
|---------------|--------|-------|--------|
| Line coverage |  18%   |  72%  |  70%   |

## Tests Added
| Test Class | Method | What it covers |
|---|---|---|
| `RssFeedServiceTest` | `shouldParseFeed_whenFeedIsValid` | Happy path — RSS 2.0 parsing |
| `RssFeedServiceTest` | `shouldThrow_whenFeedIsMalformed` | Error case — malformed XML |
| `NewsControllerTest` | `shouldReturn200_whenArticleFound` | GET /api/news/{id} happy path |

## Build Result
- Maven build: SUCCESS
- Tests: 15 passed / 0 failed

## ⏱️ Time
- Duration: **6m 22s**

## Handoff
Coverage is now 72% (≥ 70%). All tests pass. Handing back to Reviewer to merge the PR.
```

---

## 5. DevOps Agent

**Purpose:** Build, deploy, release, and close issues in milestone batches. The PR is already merged into `develop` by the Reviewer before this agent runs.

**When to Use:** Automatically invoked by the pipeline at each milestone boundary (every 5 merged PRs, or at end-of-run). Or manually:

```
@devops Please deploy batch: issue #43 PR #51, issue #44 PR #52
```

### Batch Handoff Format

The Pipeline passes a batch — not a single PR:
```json
[
  { "issue": 43, "pr": 51, "mergeCommitSha": "abc1234", "coverage": "72%" },
  { "issue": 44, "pr": 52, "mergeCommitSha": "def5678", "coverage": "63%" }
]
```

### What the DevOps Agent Does

1. **Prerequisite check (Step 0)** — verifies `gh auth`, `GITHUB_TOKEN`, Maven, and pulls latest `develop`
2. **Verifies all merge commit SHAs** are reachable from `develop`; halts if any are missing
3. **Determines the version bump** using semantic versioning (see table below)
4. Runs the production Maven build: `./mvnw clean package -DskipTests`
5. Builds and verifies the Docker image: `docker build ... && docker image inspect ...`
6. Deploys to **staging** — runs health checks; does **not** proceed to production if staging fails
7. Applies Flyway database migrations if included
8. Deploys to **production** — runs health checks; triggers rollback if they fail
9. **Bumps `pom.xml` version** and commits it to `develop`: `mvn versions:set -DnewVersion=... && git commit -m "chore: bump version to {N} [skip ci]"`
10. **Creates a git tag**: `git tag -a "v{N}" -m "Release v{N}" && git push origin "v{N}"`
11. **Creates one GitHub release** (idempotency: checks for existing tag first with `gh release view`)
12. **Closes all issues** in the batch with deployment summary comments
13. **Deletes feature branches** for all items in the batch

### Semantic Versioning Rules

The DevOps agent determines the version bump once for the entire batch:

| Change type | Bump | Example |
|-------------|------|---------|
| Breaking API change, incompatible schema migration | **MAJOR** | `1.2.3` → `2.0.0` |
| New feature, new endpoint, new non-breaking behaviour | **MINOR** | `1.2.3` → `1.3.0` |
| Bug fix, patch, refactor, documentation only | **PATCH** | `1.2.3` → `1.2.4` |

Signals: scans PR titles and issue labels for `breaking`, `bug`, `feature`, `fix`, `refactor`. When in doubt, MINOR is preferred over PATCH for new features.

### Deployment Standards

- **Staging first** — never skip staging verification before production
- **Health checks mandatory** — `/actuator/health` must return HTTP 200 after each deployment stage
- **Rollback** — automatic on health check failure; also reverts the `pom.xml` version bump commit
- **Migrations** — Flyway applied to staging first; must be backward-compatible and include rollback scripts
- **Secrets** — never committed, never logged even partially

### Example Output

```markdown
## Deployment Summary
✅ Deployed successfully

**Milestone batch:** 2 issues — #43, #44

## Build & Deploy
- SHAs verified: abc1234 ✅, def5678 ✅
- Maven build: SUCCESS — `real-newsletter-1.3.0.jar`
- Docker image: `real-newsletter:v1.3.0` + `latest`
- Staging deployment: SUCCESS
- Production deployment: SUCCESS

## Health Check
- `/actuator/health`: ✅ UP
- Application logs: No errors detected

## Release
- [v1.3.0](https://github.com/ravidsun/real-newsletter/releases/tag/v1.3.0)
- Issues closed: #43 ✅, #44 ✅ (deployment summary comments posted)

## Branch Cleanup
- `feature/issue-43` deleted ✅
- `feature/issue-44` deleted ✅

## Status
Deployment complete. 2 issues in batch closed. Milestone 1 done.
```

---

## Complete Example: End-to-End Workflow

### The One-Command Way (Recommended)

```
@pipeline Run pipeline for issues #43 #44 #45
```

The pipeline handles everything automatically. Progress is posted as comments on the original GitHub issue.

---

### Stage-by-Stage Walkthrough

#### Step 1: Create a Feature Request
You create GitHub issue #42:
```
Title: Add real-time news ingestion from RSS feeds
Description: Fetch articles from multiple sources, store in DB, expose via REST.
Labels: feature, backend
```

#### Step 2: Planner Decomposes the Work
```
@planner Please plan issue #42
```
**Output:** Planner runs prerequisite check, checks for in-progress PRs (none), checks for existing sub-issues (none), then creates issues #43, #44, #45 — each with `implementation` label and assigned to the active milestone. Recommends #45 (no blockers) as the first issue.

#### Step 3: Coder Implements First Issue
```
@coder Please implement issue #43
```
**Output:**
- Prerequisite check passes; issue is OPEN
- Idempotency check: no existing branch or PR found
- Feature branch `feature/issue-43` created from `develop` (latest pulled first)
- Code implemented in `com.realnewsletter.*` packages with constructor injection
- `mvn clean test jacoco:report` passes — coverage: **72%**
- `git rebase origin/develop` before push (clean linear history)
- PR #51 created with `--label "implementation"` and build summary
- ⏱️ Duration: **3m 12s**

#### Step 4: Reviewer Approves and Merges
```
@reviewer Please review PR #51
```
**Output:**
- Prerequisite check: PR is open, **not** a draft ✅
- CI checks: all passing ✅
- PR targets `develop` ✅
- Architecture, security, performance reviewed — all OK
- All acceptance criteria verified
- Coverage 72% ≥ 30% → **PR #51 squash-merged into `develop`** (commit: `abc1234`)
- ⏱️ Duration: **5m 45s**

#### Step 5: Test Agent *(skipped — coverage ≥ 30%)*
Coverage is 72% which is above the 30% threshold — the pipeline logs `Test: skipped (coverage = 72% ≥ 30%)` and proceeds to the Batch Gate.

> If coverage had been below 30%, the Test agent would have run (max 3 rounds) to add tests to reach ≥ 70%, then handed back to the Reviewer to merge.

#### Step 6: Batch Gate
- 1 issue merged, batch size = 5 (default), more issues remain
- Pipeline logs: `⏳ Batch: 1/5 — queuing for milestone`
- Proceeds to implement #44 → #45 (same workflow repeats for each)

#### Step 7: DevOps Deploys (end-of-run final flush after all 3 issues)
- DevOps receives batch: `[{issue:43, pr:51, sha:"abc1234", coverage:"72%"}, ...]`
- All SHAs verified on `develop`
- Semantic versioning: new features → **MINOR** bump → `v1.3.0`
- `pom.xml` updated, committed to `develop`, git tag `v1.3.0` created and pushed
- Docker image built and verified
- Staging → health check ✅ → Production → health check ✅
- Release `v1.3.0` created (checked for existing tag first — idempotent)
- Issues #43, #44, #45 closed with deployment comments
- Feature branches deleted
- ⏱️ DevOps Duration: **2m 10s** | Total pipeline: **~32m**

---

## Common Workflows

### Scenario: Reviewer Requests Changes

1. Reviewer submits `REQUEST_CHANGES` with specific findings (file:line references and code suggestions)
2. Pipeline routes back to Coder with the full fix list and cycle number (`"Review cycle 2 of 3"`)
3. Coder makes changes, rebases onto `develop`, pushes to the same feature branch
4. Reviewer re-reviews (max 3 cycles total)
5. Once approved: Reviewer squash-merges → adds to `pendingDeployment` → DevOps deploys at next milestone

### Scenario: Re-triggering a Partial Pipeline

If the pipeline was interrupted mid-run (e.g., build failure, token expiry):

```
@pipeline Run pipeline for issue #43
```

The pipeline will:
1. Run prerequisite verification (Stage −1)
2. Check `gh pr list --search "closes #43" --state merged` — if the PR is already merged, skip Stages 1–4 and add it directly to `pendingDeployment`
3. Continue from the last incomplete stage

### Scenario: Coverage Below 30%

1. Coder produces PR with 18% coverage — included in PR description
2. Reviewer approves but does **not** merge — routes to Test agent with PR number, branch, coverage, cycle
3. Test agent runs up to 3 rounds; reaches 72%
4. Test agent hands back to Reviewer with updated coverage
5. Reviewer squash-merges (no re-review) → Batch Gate

### Scenario: Rollback After Production Failure

1. DevOps detects production health check failure after deployment
2. Immediately triggers emergency rollback — reverts all batch merge commits and `pom.xml` version bump commit
3. Redeploys previous known-good artifact
4. Posts high-priority Error Report on all issues in the batch
5. Creates a GitHub issue with label `incident`
6. Pipeline halts — user must resolve the root cause before re-deploying

### Scenario: Database Migration

1. Coder includes `Vn__description.sql` in changeset; verifies it applies cleanly with `mvn clean test`
2. DevOps applies migration to **staging first** and confirms schema version
3. Proceeds to production only if staging migration succeeds
4. If migration fails: halts immediately, runs rollback script, posts Error Report

### Scenario: Draft PR Guard

If a PR was accidentally left in draft state:
1. Reviewer (Step 0) detects draft status — posts: *"This PR is still in draft state. Please mark it as ready for review."*
2. Reviewer halts — does not review draft PRs
3. Author marks PR ready → re-invoke `@reviewer Please review PR #N`

---

## Best Practices

1. **Write clear acceptance criteria** — the Planner creates focused issues from them and the Reviewer validates each one
2. **Small, focused PRs** — ship faster, easier to review, less risky to deploy
3. **Never push WIP or fixup commits** — the Coder squashes/amends before pushing; commit history is the audit log
4. **Keep `GITHUB_TOKEN` scopes minimal** — `repo` + `workflow` is sufficient; no need for `admin` scope
5. **Let the pipeline batch** — avoid `--milestone-size 1`; batching keeps the release history meaningful
6. **Monitor the Batch Gate** — if the pipeline halts mid-batch, the `pendingDeployment` list is preserved in the Error Report; use it when resuming
7. **Check logs after deployment** — the DevOps agent monitors for the first few minutes but follow up in your observability platform
8. **Label issues correctly** — `bug` issues get prioritised over `feature` by the Planner's ranking algorithm

---

## Troubleshooting

### GITHUB_TOKEN not set

```powershell
.\scripts\load-env.ps1
# Verify:
echo $env:GITHUB_TOKEN
gh auth status
```

### Build Fails During Coding

The Coder posts a structured Error Report on the implementation issue with:
- Full Maven output (no truncation)
- Each failing test: class name, method, failure message, stack trace
- Recovery steps tried

Address the root cause and re-invoke:
```
@coder Please implement issue #43
```
The Coder will resume from the existing branch (idempotency check) rather than starting over.

### Reviewer Blocks at CI Checks

The Reviewer waits up to 5 minutes for in-progress CI checks. If checks are still failing:
1. Review the GitHub Actions logs for the PR
2. Fix the underlying check failure
3. Re-invoke: `@reviewer Please review PR #N`

### PR Targets the Wrong Branch

If the Coder accidentally targeted `main` instead of `develop`, the Reviewer will catch this and request a retarget before reviewing. Fix it:
```bash
gh pr edit {N} --base develop
```

### Coverage Never Reaches 70%

If the Test agent reaches its max 3 rounds and coverage is still below 70%:
1. The agent posts an Error Report listing all uncovered classes/methods
2. Routes back to Coder with details of which production code needs to be made more testable
3. Coder refactors, then the Coder → Reviewer → Test cycle repeats

### Feature Branch Closure

**Automatic cleanup** — when a PR is merged, the associated feature branch is deleted in two ways:

| Method | How |
|--------|-----|
| **GitHub Actions** (primary) | `.github/workflows/cleanup-feature-branches.yml` triggers on PR merge — auto-deletes `feature/*` branches |
| **DevOps agent** (fallback) | `git push origin --delete feature/issue-{N}` during deployment — non-blocking |

Protected branches (`main`, `develop`) are never deleted.

To disable automatic cleanup: **Actions → Cleanup Feature Branches → Disable workflow**.

### Pipeline Blocked (cycle limit exceeded)

If the Reviewer → Coder loop exceeds 3 cycles:
1. Reviewer posts a blocking Error Report on the PR listing all unresolved findings
2. `pipeline-blocked` label is added to the issue
3. Comment posted on the issue requesting Planner re-scoping

To unblock:
```
@planner Revise scope for issue #43 — implementation is too broad
@pipeline Run pipeline for issue #{new_number}
```

---

## Agent Files Reference

All agent definitions are in `.github/agents/` — each versioned at `2.1`:

| File | Agent | Version | Purpose |
|------|-------|---------|---------|
| `pipeline.agent.md` | **Pipeline** | 2.1 | Orchestrator — drives the full pipeline end-to-end; manages milestone batching |
| `planner.agent.md` | **Planner** | 2.1 | Issue decomposition, in-progress detection, next-issue identification |
| `coder.agent.md` | **Coder** | 2.1 | Code implementation + rebasing + `mvn test jacoco:report` + idempotent PR creation |
| `reviewer.agent.md` | **Reviewer** | 2.1 | Code review, CI verification, draft-PR guard, squash-merge into `develop` |
| `test.agent.md` | **Test** | 2.1 | Conditional coverage agent (only when coverage < 30%; max 3 test-writing rounds) |
| `devops.agent.md` | **DevOps** | 2.1 | Semantic-versioned build, git tag, GitHub release, issue closure per batch |

GitHub Actions workflows are in `.github/workflows/`:

| File | Purpose |
|------|---------|
| `agent-pipeline.yml` | Trigger via `run-pipeline` label or `workflow_dispatch` |
| `github-agent-pipeline.yml` | Full cloud pipeline (no IDE required); targets `develop` |
| `cleanup-feature-branches.yml` | Auto-deletes `feature/*` branches after PR merge |

### Branch Strategy

| Branch | Purpose | Who changes it |
|--------|---------|----------------|
| `feature/*` | Active development | Coder agent — branched from `develop`; merged into `develop` via reviewed PR |
| `develop` | Integration / staging | Agent pipeline — all automated merges target here |
| `main` | Production | **Manual only** — `develop` → `main` is a human gate; agents never touch `main` |

---

## Next Steps

### Option A — One-command pipeline *(recommended)*
1. Create a GitHub issue describing your feature/bug
2. In your IDE:
   ```
   @pipeline Run pipeline for issue #N
   ```
3. Monitor progress in the GitHub issue comments

### Option B — Label-triggered *(automated)*
1. Create a GitHub issue
2. Add the label **`run-pipeline`**
3. The GitHub Actions workflow validates your setup and posts a prompt to use in the IDE

### Option C — Stage by stage *(manual)*
1. Create a feature request issue
2. Invoke each agent in order:
   ```
   @planner Please plan issue #N
   @coder Please implement issue #M
   @reviewer Please review PR #P
   # Test agent invoked automatically if coverage < 30%
   @devops Please deploy batch: issue #M PR #P
   ```
3. Monitor progress in GitHub issue and PR comments
