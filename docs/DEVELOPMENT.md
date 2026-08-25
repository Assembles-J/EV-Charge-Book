# Development Workflow

Version: v1.1.0

## Standard Delivery Route

Every meaningful change follows one short path:

```text
Issue -> feature/<issue>-short-name -> implementation -> PR -> CI -> squash merge -> main
```

Rules:

- `main` is the stable branch.
- One Issue should describe one independently verifiable outcome.
- Feature branches use `feature/<issue-number>-<short-name>`; fixes use `fix/<issue-number>-<short-name>`.
- PR descriptions must link the Issue and state acceptance evidence.
- Do not require every PR to edit every document. Update only documents whose product, architecture, data, UI, CI, or roadmap contract actually changes.
- Keep infrastructure shared through `Assembles-J/.github` when the behavior is genuinely common.
- Project-specific product logic stays inside this repository.

## New Project Bootstrap Order

1. Define `PROJECT_MASTER.md`: product goal, MVP boundary, non-goals.
2. Create the first runnable-app Issue.
3. Build the smallest locally runnable client before backend/cloud work.
4. Add CI that produces a downloadable debug artifact.
5. Complete local persistence and the core CRUD loop.
6. Add statistics only after the underlying data model is real.
7. Add backend, sync, signing, deployment secrets, and AI only when a concrete feature requires them.

## EV Charge Book MVP Development Order

### Step 1 - Runnable Android shell

Compose app, Dashboard, navigation shell, repeatable Debug APK build.

### Step 2 - Local database

Room, Vehicle, ChargingRecord, DAO and repository.

### Step 3 - Charging record CRUD

Create, edit, delete and history list.

### Step 4 - Dashboard statistics

Total energy, total cost, average electricity price and later cost-per-distance when mileage data exists.

### Step 5 - Release

Generate the Gradle wrapper, signed release APK and GitHub Release only after the MVP loop is stable.

## Coding Principles

- Simple first
- Local First for v0.1
- Maintainable architecture
- Avoid premature abstraction
- Real user scenario driven
- Do not introduce server infrastructure before the local product loop needs it
