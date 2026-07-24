# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.4.0]

Backward-compatible hotfix release. No public API changes — drop-in for existing
consumers of `com.github.gcacace:signature-pad`.

### Fixed
- **Background/rotation crash `Could not copy bitmap to parcel blob`**
  (#178, #169, #183, #187). The signature was stored in the saved-state `Bundle`
  as a raw `Bitmap`, which the framework copies to a native parcel blob during
  `activityStopped()` — this failed / overran the ~1 MB Binder buffer on large
  signatures. The signature is now persisted as a PNG-compressed `byte[]`, which
  never takes that path, and the payload is size-capped (256 KB) as a safeguard.
- **`saving instance state may throw due to zero bounds`** (#145):
  `IllegalArgumentException: width and height must be > 0` when saving state
  before the view had a size, or when trimming a blank/single-dot signature.
  `Bitmap.createBitmap` dimensions are now clamped to at least 1px.
- **Single taps did not render a dot** (#41). A tap produces a zero-length curve,
  which drew nothing; a single dot is now painted for degenerate curves.

### Behavior notes
- If a signature is exceptionally large and its PNG exceeds the 256 KB cap, it is
  not persisted across a configuration change (the pad restores empty and the
  user re-signs) rather than risking a `TransactionTooLargeException`. This only
  affects pathologically large signatures; a typical signature is a few KB.
- An untouched (never-drawn) pad now restores as **empty** (`isEmpty()` returns
  `true`) after a configuration change. Previously it persisted a blank bitmap and
  restored as non-empty, spuriously firing `onSigned()`. Drawn signatures continue
  to restore and fire listeners as before.

### Known issues (targeted for 2.0)
- `getSignatureSvg()` still returns an empty SVG after a rotation (the restored
  signature is a bitmap, so its vector path is not reconstructed).
- `SvgBuilder.build()` is not idempotent (a second call duplicates the last
  stroke); the `SvgPathBuilder` zero-curve discard guard is dead code. Both are
  pinned by characterization tests.

## [Phase 0 & 1 — build/publishing revival and test safety net]

### Added
- Continuous integration via GitHub Actions: unit tests, Android Lint, and
  library + example assembly run on every push and pull request.
- Unit test suite (JUnit + Robolectric) covering the geometry/SVG utilities and
  the `SignaturePad` save/restore lifecycle. This is the safety net for the
  crash fix.
- `PUBLISHING.md` documenting the Maven Central (Sonatype Central Portal)
  release process.

### Changed
- **Build system revived.** Gradle `6.2.2` → `8.13`, Android Gradle Plugin
  `3.6.1` → `8.11.1`. Dependency resolution moved from the shut-down `jcenter()`
  to `google()` + `mavenCentral()`.
- **Publishing revived.** Replaced the discontinued Bintray pipeline with the
  `com.vanniktech.maven.publish` plugin targeting the Sonatype Central Portal.
  The published coordinate is unchanged: `com.github.gcacace:signature-pad`.
- `compileSdk` `29` → `36`; `minSdk` `14` → `21`.

### Fixed
- Consumer ProGuard rules referenced the pre-AndroidX `android.databinding.**`
  namespace; corrected to `androidx.databinding.**`.
