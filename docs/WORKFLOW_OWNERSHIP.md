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

## Workflow matrix

| Workflow | Trigger / scope | Primary responsibility | Deployment / publish authority |
| --- | --- | --- | --- |
| `.github/workflows/android-build.yml` | PR/push when `android/**` or the workflow changes; manual dispatch | Build/test Android, enforce packaged Hero budget, produce Debug APK | **No** production publish |
| `.github/workflows/android-release.yml` | Manual `workflow_dispatch` only | Build/sign/verify Production APK, version it inside the release run, publish release/update metadata | **Yes — Android Production Release** |
| `.github/workflows/hero-admin.yml` | PR/push for `hero-admin/**`, `vehicle-catalog/**`; manual dispatch | Validate resource-admin browser assets, Python/container runtime and catalog/brand lifecycle contracts | **No** production deploy |
| `.github/workflows/hero-admin-deploy.yml` | PR/push for admin/assets/catalog/deploy script; manual dispatch | Validate deployment bundle; on `main` push deploy resource admin to production | **Yes — resource admin deployment** |
| `.github/workflows/hero-assets-publish.yml` | PR/push for `hero-assets/**`; manual dispatch | Validate Hero manifest/package, remote WebP existence/size/URL contract | **No direct deploy**; validates repository-hosted Hero package |
| `.github/workflows/admin-batch-image-upload.yml` | PR/push for batch-upload browser files/docs | Verify filename matching, duplicate handling and Brand Logo contrast/batch-planning contract | **No** deployment |
| `.github/workflows/admin-prompt-library.yml` | PR/push for prompt-library/index; manual dispatch | Verify Logo/Hero prompt/copy-center browser contract | **No** deployment |
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

## Resource Admin Validation

Workflow: `hero-admin.yml`

This is the broad **resource-admin application validation** workflow.

It checks:

- browser/admin assets;
- Python syntax/runtime;
- container build;
- isolated admin startup;
- served browser assets;
- managed brand / Brand Logo / vehicle catalog lifecycle contracts.

It may overlap path triggers with narrower browser-contract workflows because it validates the integrated admin application, while the narrow workflows give faster focused evidence.

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

## Focused Admin Browser Contracts

### Batch Image Upload

`admin-batch-image-upload.yml` validates:

- filename normalization;
- longest-prefix Hero matching;
- duplicate/unmatched planning;
- Brand Logo visual/contrast classification;
- Light/Dark Brand Logo batch planning.

### Prompt Library

`admin-prompt-library.yml` validates:

- prompt/copy-center presence in the admin;
- Logo and Hero standard markers;
- copy/generation affordances;
- current key image-standard constants exposed to users.

### Vehicle Catalog Admin Tools

`vehicle-catalog-admin-tools.yml` validates:

- catalog JSON/CSV import/export UI contract;
- template/import controls;
- Brand Logo center controls;
- Hero-key selection/management hooks.

These focused workflows complement `hero-admin.yml`; they do not deploy production services.

## Governance rules

1. A workflow's filename is not enough to infer authority; use its actual trigger/jobs/actions.
2. Validation and deployment are separate states.
3. PR checks must correspond to the paths and responsibilities changed by that PR.
4. A Green narrow browser-contract workflow does not replace broad integrated admin validation when both apply.
5. A Green admin validation workflow does not prove production deployment.
6. A Green Android Build does not prove Production Release or physical-device acceptance.
7. A workflow whose responsibility changes must update this file and `CI_CD.md`/domain docs when relevant.
8. Avoid adding a new workflow when an existing workflow already owns the same contract; prefer adding a focused job only when responsibility remains clear.
9. When overlap is intentional, document why the overlap exists rather than deleting checks solely to reduce workflow count.

## Current conclusion

The eight current workflows are not simple duplicates. Their main governance problem was **missing ownership documentation**, not necessarily excessive workflow count.

Future simplification should be evidence-driven: consolidate only when two workflows truly test/deploy the same contract and the merge does not weaken path-specific feedback or authority clarity.
