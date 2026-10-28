# SakuraPatcher — Next Version Master Roadmap

> **Status:** Planned. This is the agreed scope for the next major SakuraPatcher implementation pass, not a list of completed features.
>
> **Source of design truth:** the user's uploaded Google AI Studio / React UI prototype. Recreate it **natively in Kotlin, Jetpack Compose, and Material 3**, never by substituting simulated web components for actual APK patching.
>
> **Target branch:** `main`. Preserve Morphe's current functional patch engine, bundle and patch-source handling, app selection, saved selections, signing and installation, batch patching, progress/logs, settings, backup, and onboarding.

## 1. Pixel-for-pixel Sakura Dark / Sakura Light port

Use screenshot references from the supplied prototype to compare at identical viewport sizes, display density, system insets, theme, and UI state. Match geometry, component composition, alignment, iconography, corner radii, text sizing/weight/line-height, colors, borders, spacing and button states. A brand palette alone is not sufficient.

- [ ] Establish one shared design-token system for dark, light and system modes; include accessible contrast and predictable Android dynamic-color behavior.
- [ ] Match prototype typography: Plus Jakarta Sans for UI, JetBrains Mono for developer diagnostics/logs, with appropriate font licensing and fallback handling.
- [ ] Match layouts at **360–430 dp** and test larger displays, landscape, edge-to-edge and font scaling.
- [ ] **P0 — APK Patcher:** complete searchable patch list, selection/filter chips, compatibility badges, source choice, options, sticky primary Patch APK button and real APK picker; retain actual patch pipeline.
- [ ] **P0 — Progress/results:** actual pipeline stages, progress timeline, collapsible logs, cancel/retry, accurate failure and success states, real install/export actions; no fake percentages.
- [ ] **P1 — all bottom sheets and dialogs:** APK selection, patch options, add/edit source, installer flow, saved profile, warnings, confirmation, and error states. Keep native callbacks and lifecycle behavior.
- [ ] **P1 — Home:** exact dashboard hierarchy, app cards, badges, search, quick actions and both empty/populated states.
- [ ] **P1 — Sources:** exact source cards, filters, add/update/disable controls, status badges and dialogs; no loss of source functionality.
- [ ] **P1 — Sakura PatchLab:** reproduce the four-tab prototype layout, but populate with measured diagnostics rather than mock fingerprints or invented confidence scores.
- [ ] **P2 — Profiles and Sakura Diff:** dedicated fully integrated screens and navigation matching the prototype.
- [ ] **P2 — polish:** tablet/landscape layouts, back navigation, search and keyboard handling, accessibility, error/loading/empty states, animations only where they communicate state, and screenshot regression tests for dialogs and secondary screens.

## 2. New differentiating functionality — actual implementations

### Sakura PatchLab Pro
- [ ] Parse/inspect actual DEX methods and bytecode fingerprints locally using appropriately scoped parsing libraries.
- [ ] Detect mismatches and missing methods/fingerprints; explain *why* a patch may fail, with verifiable evidence.
- [ ] Identify patch interaction/conflicts where the underlying patch format exposes sufficient information.
- [ ] Rank possible alternative fingerprints/methods as **review suggestions**, not guaranteed repairs; include uncertainty and supporting diagnostics.
- [ ] Implement real four-tab PatchLab UI and exportable, privacy-aware diagnostics.
- [ ] Preserve the existing opt-in **Allow patches for other apps** switch, and distinguish metadata compatibility from runtime verification.

### Patch Profiles
- [ ] Named, per-application, per-version/per-source profiles with selected patches and patch options.
- [ ] Create, duplicate, rename, edit, apply, export/import JSON; verify schema and package/version/source match.
- [ ] Validate profiles when patch bundles update; never silently apply incompatible or unsafe selections.

### Sakura Diff 2.0
- [ ] Extend existing ZIP-entry/CRC and permission comparison with manifest and component/permission deltas.
- [ ] Add decoded Android resource diffs and method-level DEX change summaries where feasible.
- [ ] Searchable original-versus-modified APK view; make limits and unsupported comparisons explicit.

### Smart Patch Recovery
- [ ] Capture real failure context, identify suspected failing patches and conflicting combinations.
- [ ] Offer controlled retries / failure isolation; **ask before changing the selected patches**, preserve the original configuration and inputs.
- [ ] Store local reproducible reports for developers; do not claim a retry will always repair incompatibilities.

### Patch History and Rollback
- [ ] Save patch history, versions, patch source versions, selected options, build checksums, and signing identity.
- [ ] Optionally retain APK backups with user-controlled storage limits and cleanup.
- [ ] Safe reinstallation / restoration with signature compatibility and Android version/downgrade constraints explained.

## 3. Sakura Performance Engine — real, benchmarked modes

- [ ] **Eco:** conservative concurrency, lower peak RAM, disk cache.
- [ ] **Balanced (default):** adaptive concurrency and memory, disk cache.
- [ ] **Maximum:** higher safe concurrency *only where pipeline parallelism and available CPU/RAM allow*; no false speed promises.
- [ ] On-device APK metadata caching keyed by relevant file and bundle version.
- [ ] Streamed file operations and reduced copying, temporary-file hygiene, and crash-safe cleanup.
- [ ] Lazy composition / stable identity for long patch lists; minimize recomposition and unnecessary graphics effects.
- [ ] Record per-stage wall time, CPU, memory peak, I/O, success/failure and APK size; compare with pre-change baseline.
- [ ] Optimize DEX memory pressure for midrange devices such as Xiaomi 12 Lite; preserve foreground service / process stability on MIUI/HyperOS.
- [ ] Ensure Maximum mode safely falls back when memory is insufficient; no silent OOM-prone force-threading.

## 4. Quality gates: when is the next version done?

- [ ] Build and unit/integration tests pass on the exact final `main` commit; debug APK artifact uploaded to GitHub Actions.
- [ ] Screenshot regression comparisons for **each major screen**, populated/empty/loading/error states, dialogs and bottom sheets, in **Sakura Dark and Sakura Light** at matching display specs.
- [ ] Test **System theme** live switching, font scaling, touch targets >=48 dp, TalkBack, navigation insets, keyboard, rotations and accessibility.
- [ ] End-to-end APK selection → source selection → patch selection → patch build → signature/installation or export, including success, cancel, error and retry.
- [ ] Verify batch patching, saved selections, per-app overrides, custom sources, backups/restore, onboarding, and update paths remain functional.
- [ ] Test large/multi-DEX APKs, unavailable patch sources, incompatible target versions, storage exhaustion, interrupted operations and process recreation.
- [ ] Do not show mock diagnostic results as real, invent patch confidence percentages, silently alter configurations, or claim root/device support without testing.
- [ ] Benchmark Eco/Balanced/Maximum on at least a lower-memory Android device and a contemporary midrange phone. Report measured regressions honestly.
- [ ] Link build and test reports in release notes, and separate completed features from experiments.

## Implementation sequence

1. Establish visual reference screenshots, fonts/design tokens, native component library and screenshot-test harness.
2. Port APK Patcher, progress/results and the core bottom sheets **pixel by pixel**, while preserving functional wiring.
3. Port Home, Sources, Settings, PatchLab's four tabs and the remaining dialogs, empty and error states.
4. Implement Patch Profiles and Sakura Diff 2.0 with real data models and tests.
5. Implement measured PatchLab bytecode analysis, Smart Recovery, and Patch History/Rollback.
6. Implement performance profiles and benchmarked pipeline optimizations.
7. Run full UI parity, functional, accessibility, performance, stability and release-gate validation before calling the major version complete.

**Scope commitment:** All items above are requested for the *next version*, but each must be honestly labeled implemented/tested/experimental rather than assumed working. We should not sacrifice the existing APK patch functionality to reach visual parity.
