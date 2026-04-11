# Multi-Agent Workflow Guide

This document explains how to use the real-news multi-agent system for managing feature development, testing, code review, and deployment.

---

## ⚡ Quick Start — Run the Full Pipeline in One Command

The fastest way to ship a feature is to hand the issue number to the **Pipeline agent** and let it drive every stage automatically:

```
@pipeline Run pipeline for issue #42
```

That single prompt runs: **Planner → Coder → Reviewer → Test (conditional) → DevOps** — no manual hand-offs required.

### What the Pipeline Agent Does
1. **Plans** — calls Planner to decompose the issue into implementation issues
2. **Codes** — calls Coder for each implementation issue; Coder runs `mvn clean test jacoco:report` locally and reports coverage %
3. **Reviews** — calls Reviewer immediately after the Coder; loops back to Coder up to 3 times if changes are requested
4. **Tests (conditional)** — calls Test agent **only when line coverage < 30%** after Reviewer approval; skipped otherwise
5. **Deploys** — calls DevOps to build, release, and close issues (PR is already merged by Reviewer)
6. **Reports** — posts progress comments on the original GitHub issue at every stage, including ⏱️ duration per agent

### Triggering Automatically via GitHub
You can also trigger the pipeline without opening the IDE:

| Method | How |
|--------|-----|
| **Label** | Add the `run-pipeline` label to any GitHub issue |
| **Manual dispatch** | Go to **Actions → Agent Pipeline → Run workflow**, enter the issue number |

Both methods post a pipeline-started comment on the issue and validate your secrets before any agent work begins.

---

## Load Environment Variables First

Before invoking any custom agent, load the repository `.env` values into your current PowerShell session:

```powershell
.\scripts\load-env.ps1
```

This ensures `GITHUB_TOKEN` is set for all agents and automatically mapped to GitHub MCP auth (`GITHUB_PERSONAL_ACCESS_TOKEN`).

---

## Fully Autonomous Execution

All agents are configured with `autonomousExecution: true` in their frontmatter. This means **no confirmation prompts of any kind** — agents run from start to finish without pausing.

| Action type | Behaviour |
|---|---|
| File edits and saves | Executed immediately — no "Allow this change?" dialog |
| Terminal / shell commands (`git`, `mvn`, `gh`, `docker`, etc.) | Executed immediately — no "Run this command?" dialog |
| MCP API calls (GitHub create issue, merge PR, etc.) | Executed immediately — no "Allow this tool call?" dialog |
| Long-context continuation | Continues automatically — no "Continue?" prompt |

> ⚠️ Because agents act without asking, make sure `GITHUB_TOKEN` has the correct scopes **before** invoking any agent. An agent with broad permissions and no confirmation gate will take real, irreversible actions (push commits, merge PRs, create releases).

---

The real-news project uses a **five-stage agent-driven workflow** for implementing features:

```
Planner ──→ Coder ──→ Reviewer ──────────────────────────→ DevOps ──→ Done
 (Plan)    (Build)  (Review+Merge*)   ↘ Test* ──→ Reviewer
                                       (Coverage) (Merge)

* Reviewer merges the PR directly when coverage ≥ 30%.
  When coverage < 30%, Reviewer defers merge: routes to Test agent first,
  then Test hands back to Reviewer for merge before DevOps.
```

Each agent records its start/end time. The pipeline tracks and reports **⏱️ duration per agent and per issue**.

Each agent is a specialized autonomous system that:
- Receives work from the previous stage
- Performs specific responsibilities
- Hands off to the next agent when conditions are met
- Reports blockers or requested changes that loop back

---

## 1. Planner Agent

**Purpose:** Decompose GitHub issues into actionable implementation work, create implementation issues, and identify the next issue ready to be worked on.

**When to Use:** Start here whenever you have a new feature request, enhancement, or bug fix — or when you want to know what to work on next from the existing backlog.

### How to Invoke

1. Create a GitHub issue in the `real-news` repository with:
   - Clear title describing the requirement
   - Detailed description of the problem/feature
   - Labels (e.g., `feature`, `bug`, `frontend`, `backend`)

2. Ask the Planner agent to plan the work:
   ```
   @planner Please plan issue #42
   ```

3. Or, to identify the next issue from the existing backlog without planning a new one:
   ```
   @planner What should we work on next?
   ```

### What the Planner Does

1. Reads the GitHub issue content
2. Breaks it into smaller, focused implementation issues (if needed)
3. Checks for overlaps with existing issues
4. Creates implementation issues with:
   - Clear scope and acceptance criteria
   - Assigned labels
   - Dependency relationships (if multiple issues)
5. **Identifies the next issue to work on** by analysing all open issues and their dependency graph:
   - Scans every open issue body for dependency patterns (`depends on #N`, `blocked by #N`, `requires #N`, `after #N`)
   - Classifies each open issue as 🟢 **Ready**, 🔴 **Blocked**, or ⚙️ **In Progress** (has an open PR)
   - Ranks Ready issues by label priority (`bug` > `feature` > `infra` > `docs`) then milestone proximity then issue number
   - Posts a 🎯 comment on the recommended issue and hands off to the Coder

### Example Output

```markdown
## Summary
Issue #42 requests real-time news ingestion from RSS feeds. This requires backend infrastructure changes and a new REST endpoint.

## Created Issues
- #43 [Add RSS feed ingestion service](https://github.com/ravidsun/real-news/issues/43) — Backend service to parse and store news from RSS feeds
- #44 [Add /api/feeds endpoint](https://github.com/ravidsun/real-news/issues/44) — REST API to retrieve stored news articles
- #45 [Add database schema for news articles](https://github.com/arkguru/real-news/issues/45) — New JPA entity and table

## Dependencies
- #45 (database schema) must be completed before #43 and #44
- #43 and #44 can be implemented in parallel after #45

## Next Issue
### Backlog Status
| # | Title | Status | Blocking |
|---|-------|--------|----------|
| #45 | Add database schema for news articles | 🟢 Ready | — |
| #43 | Add RSS feed ingestion service | 🔴 Blocked | #45 |
| #44 | Add /api/feeds endpoint | 🔴 Blocked | #45 |

### Recommendation
🎯 **Next up: #45 — Add database schema for news articles**
All dependencies resolved. Handing off to Coder.
```

### Example Scenario

**Original Issue #42:**
```
Title: Implement real-time news ingestion from multiple sources

Description:
We need to ingest news articles from multiple RSS feeds in real-time
and store them in the database. Users should be able to retrieve
articles via a REST endpoint.

Labels: feature, backend
```

**Planner Output:**
Creates three linked implementation issues with clear scope, acceptance criteria, and dependencies, then analyses the full open backlog and recommends **#45** (the schema issue with no blockers) as the next issue to work on.

---

## 2. Coder Agent

**Purpose:** Implement code changes based on implementation issues created by the Planner.

**When to Use:** After the Planner creates an implementation issue and assigns it to you.

### How to Invoke

1. Read the implementation issue (e.g., #43)
2. Ask the Coder agent to implement it:
   ```
   @coder Please implement issue #43
   ```

### What the Coder Does

1. Reads the implementation issue
2. Analyzes the existing codebase
3. Creates a feature branch `feature/issue-43`
4. Implements production-quality code:
   - Spring Boot entities, repositories, services
   - REST controllers with proper HTTP methods/status codes
   - Error handling and logging
   - Javadoc for complex logic
5. Runs `mvn clean test jacoco:report` locally — **build and all tests must pass before the PR is created**
6. Extracts line coverage % from the JaCoCo report and includes it in the PR description
7. Commits changes with issue references
8. Creates a pull request linking to the implementation issue

### Code Quality Standards

- **Package Structure:** `com.arkguru.springmultiagent.*`
- **Dependency Injection:** Constructor-based injection
- **Controllers:** Thin, delegate to services
- **Services:** Business logic, `@Transactional` boundaries
- **Repositories:** Spring Data JPA interfaces
- **Error Handling:** `@ControllerAdvice`, custom exceptions
- **Logging:** SLF4J with appropriate levels
- **Tests:** Unit and integration tests included

### Example Output

```markdown
## Implementation Summary
Implemented RSS feed ingestion service that fetches articles from configured feeds,
parses XML/Atom formats, and stores them in the PostgreSQL database. Includes
proper error handling and logging.

## Changes Made
- `src/main/java/com/arkguru/springmultiagent/entity/NewsArticle.java`
  — JPA entity mapping articles to database
- `src/main/java/com/arkguru/springmultiagent/repository/NewsArticleRepository.java`
  — Spring Data JPA repository with query methods
- `src/main/java/com/arkguru/springmultiagent/service/RssFeedService.java`
  — Service for fetching and parsing RSS feeds
- `src/main/java/com/arkguru/springmultiagent/exception/FeedParsingException.java`
  — Custom exception for feed parsing errors
- `src/test/java/.../RssFeedServiceTest.java`
  — Unit tests for feed parsing logic

## Build & Test Summary
- Maven build: SUCCESS
- Unit tests: 12 passed / 0 failed
- Integration tests: 8 passed / 0 failed
- Coverage (line): **72%** ✅ ≥ 30% — Test agent will be skipped after review

## Pull Request
- #51 [Add RSS feed ingestion service](https://github.com/arkguru/real-news/pull/51)
  — closes #43

## ⏱️ Time
- Started: 2026-03-18T10:30:00Z
- Completed: 2026-03-18T10:33:12Z
- Duration: **3m 12s**

## Handoff
Implementation complete. Local build and tests pass (coverage: 72%). Ready for Reviewer agent.
```

### Example Scenario

**Implementation Issue #43:**
```
Title: Add RSS feed ingestion service

Summary:
Create a service that fetches articles from configured RSS feeds,
parses the XML, and stores articles in the database.

Scope & Tasks:
- [ ] Create NewsArticle JPA entity
- [ ] Create NewsArticleRepository
- [ ] Implement RssFeedService with fetch/parse logic
- [ ] Add unit tests for parsing
- [ ] Add integration tests with test database

Acceptance Criteria:
- Service successfully parses RSS 2.0 and Atom feeds
- Articles stored in database with metadata (title, url, date, source)
- Handles malformed feeds gracefully
- Logs all operations at appropriate levels
```

**Coder Implements:**
- NewsArticle entity with @Entity, columns, and index annotations
- NewsArticleRepository with custom query methods
- RssFeedService with constructor injection of repository
- Exception handling for network failures and parsing errors
- Full unit and integration test coverage
- PR created and linked to issue

---

## 3. Test Agent *(Conditional)*

**Purpose:** Raise line coverage to ≥ 70% when it falls below 30% after Reviewer approval.

**When to Use:** The pipeline invokes the Test agent automatically — **only when the Coder's reported line coverage is below 30%**. At ≥ 30% the Test stage is skipped entirely and the pipeline proceeds directly to DevOps.

To manually trigger:
```
@test Please verify PR #51
```

> ℹ️ The Reviewer has already approved the PR before this agent runs. Do not use this agent to re-validate code quality or architecture — its only job is coverage.

### What the Test Agent Does

1. Checks out the feature branch
2. Runs `mvn clean test jacoco:report` to establish a coverage baseline
3. Identifies under-covered classes/methods added in the PR
4. Writes targeted unit, repository, and controller slice tests to reach ≥ 70%
5. Re-runs coverage report to confirm the threshold is met
6. Commits and pushes the new tests to the feature branch
7. Posts a coverage before/after summary as a PR comment
8. Hands back to the **Reviewer** to merge the PR and route to DevOps

### Testing Standards

- **Unit Tests:** `@ExtendWith(MockitoExtension.class)` for service/component logic
- **Repository Slice Tests:** `@DataJpaTest` for new query methods
- **Controller Slice Tests:** `@WebMvcTest` for new REST endpoints
- **Isolation:** Each test independent and deterministic
- **Naming:** Descriptive names (e.g., `shouldReturnArticles_whenFeedIsValid`)
- **Mocking:** External dependencies mocked (`@MockitoBean`)
- **Trigger threshold:** **30%** — Test agent is invoked when coverage drops below this
- **Target threshold:** **70%** — Test agent raises coverage to this level once invoked

### Example Output

```markdown
## Test Agent Summary
✅ Coverage raised to 72% — threshold met

## Coverage Report
| Metric        | Before | After | Target |
|---------------|--------|-------|--------|
| Line coverage |  18%   |  72%  |  70%   |

## Tests Added
| Test Class              | Method                             | What it covers                          |
|-------------------------|------------------------------------|-----------------------------------------|
| `RssFeedServiceTest`    | `shouldParseFeed_whenValid`        | Happy path for RssFeedService.fetchFeed |
| `RssFeedServiceTest`    | `shouldThrow_whenFeedMalformed`    | Error case — malformed XML              |
| `NewsControllerTest`    | `shouldReturn200_whenArticleFound` | GET /api/news/{id} happy path           |

## Build Result
- Maven build: SUCCESS
- Unit tests: 15 passed / 0 failed
- Integration tests: 8 passed / 0 failed

## ⏱️ Time
- Started: 2026-03-18T10:45:00Z
- Completed: 2026-03-18T10:51:22Z
- Duration: **6m 22s**

## Handoff
Coverage is now 72% (≥ 70%). All tests pass. Handing back to Reviewer to merge the PR and route to DevOps.
```

### Example Scenario

**Coverage was 18% — Test agent triggered:**
```java
// Tests added by the Test agent to close the coverage gap
@ExtendWith(MockitoExtension.class)
class RssFeedServiceTest {
    @Test
    void shouldParseFeed_whenFeedIsValid() { /* ... */ }

    @Test
    void shouldThrow_whenFeedIsMalformed() { /* ... */ }
}

@WebMvcTest(NewsController.class)
class NewsControllerTest {
    @Test
    void shouldReturn200_whenArticleFound() { /* ... */ }
}
```

---

## 4. Reviewer Agent

**Purpose:** Conduct code review, validate architecture, and approve or request changes.

**When to Use:** After the Coder creates a pull request. The Reviewer receives the PR directly from the Coder — no test agent run is required first.

### How to Invoke

The Reviewer agent is automatically invoked. Or manually:
```
@reviewer Please review PR #51
```

### What the Reviewer Does

1. Receives handoff from the Coder and reads the PR diff, commit history, linked issue, and the **Coder's build/test summary** (Maven result + test counts + coverage %) from the PR description
2. Validates architectural alignment with Spring Boot patterns
3. Checks compliance with coding standards
4. Evaluates security best practices
5. Identifies performance concerns
6. Validates API contracts and documentation
7. Reviews database schema changes
8. Verifies every acceptance criterion from the implementation issue is addressed
9. Submits GitHub review: APPROVE or REQUEST_CHANGES
10. **If APPROVED — merges or defers based on coverage:**
    - Coverage **≥ 30%**: merges PR into `main` via squash-and-merge → hands off to DevOps
    - Coverage **< 30%**: does NOT merge (feature branch stays active) → hands off to Test agent → after Test raises coverage, Reviewer merges → hands off to DevOps

### Review Criteria

- **Architecture:** Layered structure (controller → service → repository)
- **Code Quality:** Clean code, proper naming, appropriate abstractions
- **Security:** Input validation, SQL injection prevention, safe error responses
- **Performance:** No N+1 queries, appropriate indexes, efficient algorithms
- **Documentation:** Javadoc for public APIs, clear comments for complex logic
- **Standards:** Consistent style, naming conventions, package structure
- **Build summary:** PR description must include Maven SUCCESS + test counts + coverage %

### Example Output

```markdown
## Review Summary
✅ APPROVED

## Architecture & Design
- Layered architecture: OK
- Dependency injection: OK (constructor-based)
- Separation of concerns: OK (controller → service → repository)

## Code Quality
- Naming conventions: OK
- Error handling: OK (custom exceptions with proper HTTP status codes)
- Documentation: OK (Javadoc on service methods)
- Entity design: OK (proper @Entity, @Column, @Index annotations)

## Security & Performance
- Input validation: OK (feed URLs validated)
- Query efficiency: OK (repository uses proper queries, no N+1)
- Error responses: OK (no stack traces exposed)

## Acceptance Criteria
- [x] Criterion 1 — verified via code inspection + Coder build summary
- [x] Criterion 2 — verified via code inspection + Coder build summary

## Merge
- PR #51 merged into `main` via squash-and-merge ✅ (commit: `abc1234`)

## Review Cycle
- Current cycle: 1 of 3
- Remaining cycles: 2

## ⏱️ Time
- Started: 2026-03-18T10:33:12Z
- Completed: 2026-03-18T10:38:57Z
- Duration: **5m 45s**

## Handoff
PR merged (commit: `abc1234`). Coverage 72% ≥ 30% — routing to DevOps.
```

### Example Scenario

**Reviewer Checks:**
1. Code follows Spring Boot patterns
2. `@Service` class handles business logic
3. `@Repository` interface extends `JpaRepository`
4. No field injection used
5. Custom exception extends `RuntimeException`
6. Logging uses SLF4J
7. REST controller returns appropriate HTTP status codes
8. Database queries are optimized (no `findAll()` then filter)
9. All public methods documented
10. PR build summary present — coverage 72% ≥ 30% → merges PR and routes to DevOps directly

---

## 5. DevOps Agent

**Purpose:** Deploy to production, manage releases, and close issues. The PR is already merged into `main` by the Reviewer before this agent runs.

**When to Use:** After the Reviewer merges the pull request into `main`.

### How to Invoke

The DevOps agent is automatically invoked. Or manually:
```
@devops Please deploy PR #51
```

### What the DevOps Agent Does

1. Confirms the merge commit SHA is present in the handoff context
2. Runs production build: `./mvnw clean package -DskipTests`
3. Builds and tags Docker image (if Dockerfile present)
4. Deploys to staging environment
5. Verifies health checks pass
6. Deploys to production
7. Monitors logs for errors
8. Creates GitHub release with release notes
9. Closes implementation issue with deployment summary
10. Deletes the feature branch (if not already cleaned up by GitHub Actions)

### Deployment Standards

- **Build:** Production build must succeed before any deployment
- **Health Checks:** Must pass after staging and production deployments
- **Rollback:** Automated if health checks fail
- **Database:** Migrations applied safely with rollback capability
- **Release:** Semantic versioning (e.g., `v1.2.0`)

### Example Output

```markdown
## Deployment Summary
✅ Deployed successfully

## Build & Deploy
- Merge commit: `abc1234def567` (merged by Reviewer)
- Maven build: SUCCESS
- Docker image: `real-news:v1.2.0` pushed to registry
- Staging deployment: SUCCESS (health check: ✅ UP)
- Production deployment: SUCCESS (health check: ✅ UP)

## Health Check
- `/actuator/health`: ✅ UP
- Application logs: No errors in first 5 minutes

## Release
- [v1.2.0](https://github.com/ravidsun/real-news/releases/tag/v1.2.0)
  — RSS feed ingestion feature, database schema updates, performance improvements

## Status
- Implementation issue #43 closed with deployment summary
- All issues in this feature (#43, #44, #45) deployed to production

Deployment complete. Feature #42 is live.
```

### Example Scenario

**Deployment Steps:**
1. Confirm merge commit SHA from handoff context
2. Run `./mvnw clean package -DskipTests`
3. Build Docker image: `docker build -t real-news:v1.2.0 .`
4. Push to registry
5. Deploy to staging environment
6. Hit `/actuator/health` endpoint
7. If OK, deploy to production
8. Monitor logs for 5 minutes
9. Create GitHub release v1.2.0
10. Close issue #43 with link to release

---

## Complete Example: End-to-End Workflow

### Step 1: Create a Feature Request

You create GitHub issue #42:
```
Title: Add real-time news ingestion from RSS feeds

Description:
We need to ingest news articles from multiple RSS feeds in real-time
and store them in the database. Users should be able to retrieve
articles via a REST endpoint.

Labels: feature, backend, news-ingestion
```

### Step 2: Planner Decomposes the Work

```
@planner Please plan issue #42
```

**Output:** Planner creates issues #43, #44, #45 with dependencies.

### Step 3: Coder Implements First Issue

```
@coder Please implement issue #43
```

**Output:**
- Feature branch `feature/issue-43` created
- NewsArticle entity, repository, RssFeedService implemented
- `mvn clean test jacoco:report` passes — coverage: **72%**
- PR #51 created with build summary (72% coverage ✅ ≥ 30%)
- ⏱️ Duration: 3m 12s

### Step 4: Reviewer Approves and Merges

```
@reviewer Please review PR #51
```

**Output:**
- PR approved
- Architecture validated
- Security and performance checked
- Coverage 72% ≥ 30% → **PR #51 merged into `main`** (commit: `abc1234`)
- ⏱️ Duration: 5m 45s

### Step 5: Test Agent *(skipped — coverage ≥ 30%)*

Coverage is 72% which is above the 30% threshold — the pipeline skips the Test agent entirely and proceeds directly to DevOps.

> If coverage had been below 30%, the Test agent would have run here to add tests, then handed back to the Reviewer to merge.

### Step 6: DevOps Deploys

```
@devops Please deploy PR #51
```

**Output:**
- Merge commit `abc1234` confirmed (merged by Reviewer)
- v1.2.0 released
- Issue #43 closed with deployment link
- ⏱️ Duration: 2m 10s

### Step 7: Repeat for Issues #44 and #45

Same workflow repeats for the remaining issues.

---

## Common Workflows

### Scenario: Fix a Blocker During Review

1. Reviewer requests changes on PR #51
2. Coder makes changes, pushes to same feature branch
3. Reviewer re-reviews (Coder → Reviewer loop, up to 3 cycles)
4. Once approved, Reviewer merges the PR (coverage ≥ 30%) or routes to Test agent first
5. DevOps deploys from the merged `main`

### Scenario: Rollback After Deployment

1. DevOps detects health check failure
2. Automatically reverts merge commit
3. Reports failure with logs
4. Coder fixes issue and creates new PR

### Scenario: Database Migration

1. Coder includes migration script in changeset and verifies it applies cleanly (`mvn clean test`)
2. DevOps applies migration to staging first, then production
3. Includes rollback migration in case of issues

---

## Best Practices

1. **Clear Issue Descriptions:** Help Planner create focused implementation issues
2. **Acceptance Criteria:** Define testable criteria upfront
3. **Small PRs:** Prefer smaller implementations that can deploy quickly
4. **Test-Driven:** Write tests before or alongside code
5. **Documentation:** Include Javadoc, inline comments, and API docs
6. **Communication:** Reference issues in commits and PRs
7. **Monitoring:** Check logs after deployment

---

## Troubleshooting

### Test Failures

```
@test Verify PR #51 — tests are failing
```

Coder reviews test failures, fixes code, pushes changes to feature branch.

### Deployment Issues

```
@devops Deploy PR #51 — health checks failing
```

DevOps rolls back, notifies team. Coder fixes issue, new PR created.

### Feature Branch Closure

**Automatic cleanup is enabled** — when a PR is merged during the pipeline, the associated feature branch is automatically deleted. This happens in two ways:

1. **GitHub Actions Workflow** (Primary) — Automatically triggers when PR is merged
   - Deletes feature branch
   - Posts confirmation comment on PR
   - Protected branches (`main`, `develop`, `master`, `development`) are never deleted

2. **DevOps Agent** (Fallback) — Explicit branch deletion during deployment
   - Runs as part of Stage 5 (Deployment)
   - Used if automatic cleanup fails
   - Non-blocking (doesn't halt deployment)

**Result:** Feature branches are automatically cleaned up after successful pipeline completion. No manual branch deletion needed!

**To disable automatic cleanup** (if needed):
- Go to **Actions → Cleanup Feature Branches → ... → Disable workflow**
- Or delete `.github/workflows/cleanup-feature-branches.yml`

### Missing Acceptance Criteria

```
@planner Clarify issue #43 — acceptance criteria unclear
```

Planner updates issue with clearer criteria. Coder proceeds with updated requirements.

---

## Agent Files Location

All agent definitions are located in `.github/agents/`:
- `pipeline.agent.md` — **Orchestrator** — runs the full pipeline end-to-end from one prompt
- `planner.agent.md` — Decomposition and planning
- `coder.agent.md` — Code implementation; runs `mvn clean test jacoco:report` and reports coverage %
- `reviewer.agent.md` — Code review, approval, and **PR merge** (runs after Coder; merges when coverage ≥ 30%, or after Test agent when coverage < 30%)
- `test.agent.md` — Conditional coverage agent (runs **only when coverage < 30%** after Reviewer approval)
- `devops.agent.md` — Deployment and release

GitHub Actions workflows are located in `.github/workflows/`:
- `agent-pipeline.yml` — Automated pipeline trigger via `run-pipeline` label or `workflow_dispatch`
- `cleanup-feature-branches.yml` — **Automatic branch cleanup** — deletes feature branches after PR merge (see **Feature Branch Closure** section above)

Each file contains complete agent configuration, responsibilities, rules, failure handling, and output format.

---

## Next Steps

### Option A — One-command pipeline (recommended)
1. Create a GitHub issue describing your feature/bug
2. In your IDE, type:
   ```
   @pipeline Run pipeline for issue #N
   ```
3. Monitor progress in the GitHub issue comments

### Option B — Label-triggered (automated)
1. Create a GitHub issue
2. Add the label **`run-pipeline`**
3. The GitHub Actions workflow validates your setup and posts a prompt to use in the IDE

### Option C — Stage by stage (manual)
1. Create a feature request issue
2. Invoke each agent in order: `@planner` → `@coder` → `@reviewer` → `@test` *(if coverage < 30%)* → `@devops`
3. Monitor progress in GitHub issue and PR comments

