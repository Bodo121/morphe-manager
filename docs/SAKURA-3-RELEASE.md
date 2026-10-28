# SakuraPatcher 3

- Independent `app.sakura.patcher` package and a legacy migration APK; see SAKURA-3-MIGRATION.md before moving existing data.
- Sakura-owned release lookup and downloaded-update package/version/certificate checks.
- Ready-to-patch review and real engine dry runs without compiling/signing/installing an APK.
- Persistent build recipes, source/input/output hashes, logs, archived APKs, reviewed rebuilds with a newer input, and interrupted-work recovery.
- Portable profile source identities, version preview, bounded import and transactional restoration.
- Patch failure guidance with explicit limits on conflict detection.
- PatchLab compares permissions, components, certificates, method instruction hashes, resources and native paths. Metadata matches no longer claim fingerprint compatibility.
- Recent-build entry on Home, static UI strings in resources, Romanian translations for the new primary controls.
- Reduced-motion preference, optional Sakura petals, background/Battery Saver/patcher effect suspension, truthful progress, bounded image memory, and an opt-in local responsiveness probe.

Unit and core tests, release compilation, Android signature/package checks, and emulator startup are release gates. No claim is made that emulator tests substitute for testing on a Xiaomi 12 Lite. Release artifacts use the existing permanent Sakura signing key.
