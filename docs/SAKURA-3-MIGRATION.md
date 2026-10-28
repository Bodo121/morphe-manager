# Moving to SakuraPatcher 3

SakuraPatcher now installs as `app.sakura.patcher`. It can coexist with official Morphe and the old Sakura build (`app.morphe.manager`). Android keeps each application's private data separate; installing the new package cannot import old data automatically.

1. If using Sakura 2, install **SakuraPatcher-legacy-migration.apk** over it. This bridge has the old package ID and the same permanent Sakura release certificate. It cannot replace official Morphe, whose certificate differs.
2. In the bridge, export settings, patch selections/options, and the APK signing keystore using the existing backup/export screens. Export named profiles separately. Keep the signing-key backup private: changing that key prevents ordinary updates to apps you previously patched.
3. Install **SakuraPatcher-release.apk** (the independent package), import the backup and named profiles, and add/download the matching patch sources. The new source identities remap profiles across local IDs.
4. Verify the configuration and build one app before removing the old Sakura installation. Keep the old app and backups until migration is confirmed. This release never uninstalls either app automatically.

Build workspace keeps up to ten completed builds with retained APKs, recipes, and logs. An interrupted process is marked interrupted; restore its recipe and restart from the beginning. Android cannot resume a dead patch engine midway through an arbitrary bytecode transformation.

A dry run executes selected patch logic and dependencies, including fingerprint resolution, but skips compilation, APK writing, signing, and installation. It may use significant CPU/RAM and executes the same trusted patch bundles as a normal build. Passing does not guarantee that resource compilation or installation will succeed. Conflict guidance does not claim that overlapping edits are safe merely because no exception occurred.

PatchLab indexes up to 50,000 method bodies and 20,000 ZIP paths. It compares method signatures and instruction hashes, not behavioral equivalence; DEX index changes can alter instruction hashes. Reports disclose incomplete indexes. APK bytes and reports are local unless explicitly exported or shared.

The in-app responsiveness probe measures frame callback intervals and process PSS for ten seconds. It is not a GPU benchmark. Physical-device checks on HyperOS (screen off, background restrictions, cancellation, process death, large fonts, TalkBack) remain necessary for device-specific confidence.
