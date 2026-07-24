# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

_Nothing yet._

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
- **`getTransparentSignatureBitmap(true)` cropped one row/column of ink** (#64).
  The trim used exclusive bounds (`xMax - xMin`) even though `xMax`/`yMax` are the
  inclusive last-ink indices; it now uses `xMax - xMin + 1` so the final row/column
  is kept.
- **`getSignatureSvg()` returned an empty SVG after a rotation / configuration
  change.** The signature was restored from a PNG bitmap, which repaints the ink
  but leaves the SVG path builder empty. The SVG paths are now persisted alongside
  the PNG in saved state (under an independent 256 KB cap) and re-injected on
  restore, so `getSignatureSvg()` returns the signature again — using the original
  view dimensions as the `viewBox` so it renders as drawn, and remaining consistent
  across repeated rotations.
- **`SvgBuilder.build()` was not idempotent** — a second call duplicated the last
  stroke. `build()` (and the new `getInnerPaths()`) are now idempotent.

### Added
- `SvgBuilder.getInnerPaths()` / `restorePaths(String)` — additive helpers used to
  persist and restore the SVG path fragments across a configuration change.

### Behavior notes
- If a signature is exceptionally large and its PNG exceeds the 256 KB cap, it is
  not persisted across a configuration change (the pad restores empty and the
  user re-signs) rather than risking a `TransactionTooLargeException`. This only
  affects pathologically large signatures; a typical signature is a few KB.
- An untouched (never-drawn) pad now restores as **empty** (`isEmpty()` returns
  `true`) after a configuration change. Previously it persisted a blank bitmap and
  restored as non-empty, spuriously firing `onSigned()`. Drawn signatures continue
  to restore and fire listeners as before.
- After restoring a signature across a rotation, any strokes drawn *afterward* are
  captured in the new (rotated) view coordinate space and are therefore
  geometrically inconsistent with the restored strokes within the same SVG
  document. The visible signature (bitmap) remains correct. Reconciling both into a
  single coordinate space would require replaying vector strokes and is deferred to
  the 2.0 modernization.

### Known issues (targeted for 2.0)
- The `SvgPathBuilder` zero-curve discard guard is dead code, pinned by a
  characterization test.

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
