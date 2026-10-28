<div align="center">
  <img src="assets/sakura-patcher-icon.webp" alt="SakuraPatcher icon" width="160" />

  # SakuraPatcher 🌸

  **A community fork of Morphe Manager for Android enthusiasts.**

  [Builds](https://github.com/Bodo121/morphe-manager/actions/workflows/sakura_signed_release.yml) · [Source](https://github.com/Bodo121/morphe-manager) · [Original Morphe project](https://github.com/MorpheApp/morphe-manager)
</div>

## Features

- An experimental, opt-in **Allow patches for other apps** switch in Settings → Patcher. It displays patches targeting other Android package names without renaming the selected APK.
- Patch bundles from multiple sources and expert patch controls inherited from Morphe.
- Lightweight default appearance: static background, no default parallax or blur.
- Batch installation choice remembered for subsequent batches.

**Compatibility note:** Showing a cross-package patch does **not** make it compatible with the target APK. Missing fingerprints, modified bytecode, or incompatible resources can cause patch failures. Turn the switch off for normal patching.

## Building

The [SakuraPatcher release workflow](https://github.com/Bodo121/morphe-manager/actions/workflows/sakura_signed_release.yml) builds permanent-key signed release APKs, verifies their package identities and signatures, and publishes them without unit-test or emulator jobs. The primary app uses `app.sakura.patcher`; the legacy migration bridge uses `app.morphe.manager`. See the migration guide before moving an existing installation.

## Acknowledgments and licensing

SakuraPatcher is an **unofficial** fork of [Morphe Manager](https://github.com/MorpheApp/morphe-manager). It is not affiliated with or endorsed by Morphe. The upstream code retains its copyright and applicable license notices, including `NOTICE`. This fork uses a separately created Sakura-themed icon. Refer to the upstream project for patch engine and dependency details.

## Development status

Experimental. Build success is not equivalent to passing on-device tests, especially on MIUI/HyperOS systems that can terminate memory-heavy patcher processes.

## Sakura PatchLab (native Android)

Open **Settings → Patching → Sakura PatchLab** to inspect a standalone APK entirely on-device. The scanner reads the Android package/version/permissions, DEX and native-library counts, and ZIP structure; it cross-references currently loaded patch-source metadata and provides a copyable report. A second APK can be compared by ZIP entry names, CRC and sizes.

The results are **metadata checks**, not proof that a patch's bytecode fingerprints match. The optional cross-package switch uses the same manager preference as normal patching. This tool does not execute APK code, upload the APK, or replace the manager's existing patch/install engine.

### Imported UI design

The supplied Google AI Studio export was a React/Vite mockup with simulated patch results, not an Android project. Sakura Dark/Light colors, compact technical cards, screen labels, and the diagnostics workflow inspired a native Jetpack Compose implementation. The working Morphe patcher, installers, sources, onboarding, backup, settings, and batch flows remain native.


## SakuraPatcher 3 workspace

The independent application ID is `app.sakura.patcher`. Existing Sakura users should follow [the migration guide](docs/SAKURA-3-MIGRATION.md) and keep a private backup of their APK signing key. The release includes a migration bridge using the old ID.

Open **Settings → Build workspace** for retained build recipes, hashes, logs, installation checks, and reviewed rebuilds with a newer APK. Every single-app run offers a **Ready to patch?** review and a real engine **Dry run**. Dry runs execute patch logic and dependencies but do not verify compilation, signing, or installation.

PatchLab compares APK permissions, components, signing certificates, bounded method instruction indexes, resources, and native paths. A metadata package/version match is not fingerprint verification. Motion preferences respect Android animation settings, accessibility, visibility, Battery Saver, and the patcher workload.

See [release details](docs/SAKURA-3-RELEASE.md) for validation scope and known limits.
