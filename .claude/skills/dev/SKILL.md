---
name: dev
description: Execute a project development specification from docs/tasks/<feature-name>.md using the Team Lead workflow. Use when the user asks to implement, develop, proceed with, or complete a named feature or development task.
argument-hint: "[feature-name]"
arguments:
  - feature
---

# Development Workflow

Target specification:

`docs/tasks/$feature.md`

## 1. Read and analyze the specification

1. Read `docs/tasks/$feature.md` completely before modifying code.
2. Treat the document as the source of truth for the requested work.
3. Identify the implementation scope, constraints, dependencies, and completion criteria.
4. Do not expand the scope beyond what is defined in the specification unless required to make the requested feature function correctly.
5. If the specification conflicts with the existing project architecture or code, prefer the specification but report the conflict clearly.

## 2. Delegate work

The Team Lead must analyze the specification and delegate applicable work to:

- `backend-developer`
- `frontend-developer`
- `tester`

Each delegated agent must independently read:

`docs/tasks/$feature.md`

before starting its work.

Do not rely only on a summarized requirement passed by the Team Lead when the original specification is available.

## 3. Parallel execution

Run independent work in parallel whenever possible.

Typical responsibility split:

- `backend-developer`: Spring Boot, Java, REST API, service, repository, DTO, database, backend validation, backend tests
- `frontend-developer`: Vue, TypeScript/JavaScript, components, views, API clients, state management, frontend validation, frontend tests
- `tester`: test analysis, test scenarios, regression risks, automated tests, integration verification

Before parallel implementation:

1. Identify shared contracts such as API paths, request/response DTOs, field names, and validation rules.
2. Define those contracts clearly when the specification does not already define them.
3. Prevent multiple agents from modifying the same production files at the same time.

The tester may prepare test scenarios while implementation agents are working.

## 4. Agent completion report

Require each implementation agent to report:

- files changed
- implementation summary
- tests performed
- build/test result
- unresolved issues
- commit hash, if a commit was created

## 5. Integration

After delegated work is complete:

1. Collect all agent results.
2. Integrate the changes into the main working branch or working tree.
3. Resolve integration conflicts within the requested scope.
4. Review the complete diff.
5. Verify that the implementation matches `docs/tasks/$feature.md`.

## 6. Build and test

Run all relevant verification available in the project.

At minimum, when applicable:

1. Run the backend build.
2. Run backend tests.
3. Run the frontend build.
4. Run frontend tests or lint checks.
5. Run relevant integration or regression tests.
6. Have the tester verify the integrated result when useful.

Do not declare the development complete when required builds or tests are failing unless the failure is unrelated to the requested work. Clearly report unrelated pre-existing failures.

## 7. Scope control

- Do not perform unrelated refactoring.
- Do not introduce unnecessary dependencies.
- Preserve existing project conventions and architecture unless the specification explicitly requires a change.
- Prefer focused changes over broad rewrites.
- Do not silently change requirements.

## 8. Final report

Return a concise completion report containing:

- feature name
- specification used
- backend result
- frontend result
- test result
- changed files
- build status
- remaining issues or risks

If implementation is incomplete, state exactly what remains.
