# EV Charge Book GitHub Actions Workflow Ownership

Updated: 2026-08-31
Status: Repository workflow authority

## Purpose

The repository currently has multiple focused workflows across Android, Hero assets, resource admin and vehicle-catalog tooling. Several intentionally watch overlapping paths, but they do **not** own the same responsibility.

Use this file to answer:

- which workflow validates which surface;
- which workflow is allowed to deploy/publish;
- which checks are relevant to a PR;
- which workflow must not be mistaken for another acceptance layer.

## Current workflow matrix

At baseline `main@e5149a6474a17c7a5ce0a987f04fc3ab38a143b0`, the repository contains **nine** workflow files.

| Workflow | Trigger / scope | Primary responsibility | Deployment / publish authority |
| --- | --- | --- | --- |
| `.github/workflows/android-build.yml` | PR/push when `android/**` or the workflow changes; manual dispatch | Build/test Android, enforce packaged Hero budget, produce Debug APK | **No** production publish |
| `.github/workflows/android-release.yml` | Manual `workflow_dispatch` only | Build/sign/verify Production APK, version it inside the release run, publish release/update metadata | **Yes — Android Production Release** |
| `.github/workflows/hero-admin.yml` | PR/push for `hero-admin/**`, `vehicle-catalog/**`; manual dispatch | Validate integrated resource-admin browser assets, Python/container runtime and catalog/brand lifecycle contracts | **No** production deploy |
| `.github/workflows/hero-admin-deploy.yml` | PR/push for admin/assets/catalog/deploy script; manual dispatch | Validate deployment bundle; on qualifying `main` push deploy resource admin to production | **Yes — resource admin deployment** |
| `.github/workflows/hero-assets-publish.yml` | PR/push for `hero-assets/**`; manual dispatch | Validate Hero manifest/package, remote WebP existence/size/URL contract | **No direct deploy**; validates repository-hosted Hero package |
| `.github/workflows/admin-resource-workbench.yml` | PR/push for unified workbench/full-bundle prompt/docs | Validate coordinated one-vehicle resource bundle workbench, matching, variants, review/confirmation and bundle contract | **No** deployment |
| `.github/workflows/admin-batch-image-upload.yml` | PR/push for legacy/focused batch-upload browser files/docs | Retained focused regression checks for filename matching, duplicate handling and Brand Logo contrast/batch-planning helpers | **No** deployment; not preferred new-vehicle onboarding authority |
| `.github/workflows/admin-prompt-library.yml` | PR/push for single-item + full-bundle prompt surfaces; manual dispatch | Verify single-item prompt library plus full asset-bundle prompt/index contract | **No** deployment |
| `.github/workflows/vehicle-catalog-admin-tools.yml` | PR/push for catalog-tools browser files/docs | Verify catalog import/export, Hero-key and Brand Logo admin browser contract | **No** deployment |

## Android Build

Workflow: `android-build.yml`

Authority:

- workflow name: `Android Build`;
- job/check name: `Android CI`;
- validates Android repository baseline;
- runs JVM/unit tests and `assembleDebug`;
- uploads a short-retention Debug APK artifact;
- enforces that oversized finished Hero WebP assets are not packaged into the APK drawable directory.

This workflow proves an automated Android build/test baseline only.

It does **not** prove:

- physical-device behavior;
- production signing;
- old-production -> new-production APK upgrade;
- production server/update-manifest publication.

Repository protection target is tracked by #75: applicable runtime PRs should require current-head `Android CI` before merge.

## Android Release

Workflow: `android-release.yml`

Authority:

- manual production gate only;
- resolves the requested release ref;
- creates release-only `versionCode/versionName` from the release run;
- restores production signing credentials;
- builds and verifies a signed release APK;
- verifies the public update-discovery endpoint before release;
- prepares SHA-256 and atomic publication artifacts;
- runs in the `production` Environment.

Normal PR/Debug CI must never be documented as having produced a Production Release.

Production Release success still does not replace #102's real old-APK -> new-APK in-place upgrade acceptance.

## Integrated Resource Admin Validation

Workflow: `hero-admin.yml`

This is the broad **resource-admin application validation** workflow.

It checks integrated behavior such as:

- browser/admin assets;
- Python syntax/runtime;
- container build;
- isolated admin startup;
- served browser assets;
- managed brand / Brand Logo / vehicle catalog lifecycle contracts.

It may overlap path triggers with narrower browser-contract workflows because it validates the integrated admin application, while focused workflows give faster domain-specific evidence.

It is **not** the production deploy authority.

## Resource Admin Production Deployment

Workflow: `hero-admin-deploy.yml`

This workflow owns the resource-admin deployment bundle and production deployment path.

Behavior:

- validates deploy script and seed manifests;
- builds the admin image;
- on qualifying `main` push, deploy job runs with `environment: production`;
- prepares remote staging and safe disk-space reclamation;
- uploads the image/seeds/deploy script to the production host.

Do not treat `hero-admin.yml` Green as proof that production deployment occurred; deployment authority belongs here.

## Hero Asset Package Validation

Workflow: `hero-assets-publish.yml`

Despite the filename, the workflow name is `Hero Assets Validate` and its present responsibility is **validation**, not a separate production deployment service.

It checks:

- Hero manifest schema;
- non-empty artwork set;
- repository-hosted remote WebP existence;
- 2.5 MB hard safety ceiling;
- expected first-party raw GitHub URL contract.

Do not document this workflow as publishing a server release unless the workflow is later changed to perform such a publish action.

## Unified Vehicle Resource Workbench

Workflow: `admin-resource-workbench.yml`

Merged PR #264 introduced this as the current focused contract check for coordinated vehicle onboarding.

It validates:

- `车型资源工作台` browser surface;
- one-vehicle resource-bundle JSON contract;
- coordinated Brand Logo + Hero image queue;
- automatic matching as a suggestion rather than hidden authority;
- manual target/variant correction paths;
- base Hero semantic key + `-dark` / `-light` variant rules;
- catalog/brand diff review and explicit confirmation semantics;
- server-standard next-version filename preview logic;
- full asset-bundle prompt contract.

Product/admin authority: `RESOURCE_BUNDLE_WORKFLOW.md`, owner #244.

The Resource Workbench is the **preferred path for new vehicle resource onboarding**. Single-item tools remain valid for replacing one Logo, correcting one catalog field or publishing one Hero revision.

This workflow validates the browser/workbench contract only. It does not prove production admin deployment or physical Android rendering.

## Focused Admin Browser Contracts

### Batch Image Upload — legacy/focused compatibility check

`admin-batch-image-upload.yml` remains present and validates helper behavior around:

- filename normalization;
- longest-prefix Hero matching;
- duplicate/unmatched planning;
- Brand Logo visual/contrast classification;
- Light/Dark Brand Logo batch planning.

PR #264 retired the old split batch Logo/Hero panels from the main admin UI in favor of the unified Resource Workbench. Therefore this workflow must **not** be documented as the preferred new-model onboarding flow merely because the file still exists.

If its underlying batch helper files are later removed, this workflow should be retired or narrowed in the same PR rather than becoming an orphan check.

### Prompt Library

`admin-prompt-library.yml` now validates both prompt surfaces:

- single-item Logo/Hero prompt library;
- full asset-bundle prompt;
- index links that distinguish `全量资产包 Prompt` from `单项规范 / Prompt`;
- current Logo/Hero standard markers and machine-readable bundle expectations.

The full bundle prompt dynamically reuses single-item prompt authority rather than copying independent design rules.

### Vehicle Catalog Admin Tools

`vehicle-catalog-admin-tools.yml` validates:

- catalog JSON/CSV import/export UI contract;
- template/import controls;
- Brand Logo center controls;
- Hero-key selection/management hooks.

Bulk spreadsheet/catalog maintenance remains separate from the one-vehicle Resource Workbench bundle contract.

These focused workflows complement `hero-admin.yml`; they do not deploy production services.

## Governance rules

1. A workflow's filename is not enough to infer authority; use its actual trigger/jobs/actions.
2. Validation and deployment are separate states.
3. PR checks must correspond to the paths and responsibilities changed by that PR.
4. A Green narrow browser-contract workflow does not replace broad integrated admin validation when both apply.
5. A Green admin validation workflow does not prove production deployment.
6. A Green Android Build does not prove Production Release or physical-device acceptance.
7. A workflow whose responsibility changes must update this file and `CI_CD.md`/domain docs when relevant.
8. Avoid adding a new workflow when an existing workflow already owns the same contract; add a focused workflow/job only when the responsibility is genuinely distinct.
9. When overlap is intentional, document why the overlap exists rather than deleting checks solely to reduce workflow count.
10. When a product surface is superseded, decide explicitly whether its focused workflow remains a regression/compatibility check or should be retired; do not leave ambiguous orphan CI.

## Current conclusion

The nine current workflow files are not simple duplicates. Their main governance risk is **authority drift as admin surfaces evolve**, especially when a newer unified workbench supersedes older UI entry points while old helper checks remain useful.

Future simplification should be evidence-driven: consolidate or retire only when two workflows truly validate/deploy the same contract or when a superseded surface/helper is actually removed.