# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Continuous integration via GitHub Actions: unit tests, Android Lint, and
  library + example assembly run on every push and pull request.
- Unit test suite (JUnit + Robolectric) covering the geometry/SVG utilities and
  the `SignaturePad` save/restore lifecycle. This is the safety net for the
  upcoming crash fix.
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

### Known issues (targeted for the next release)
- Background/rotation crash `Could not copy bitmap to parcel blob`
  (#178, #169, #183, #187) — root-caused: the signature `Bitmap` is stored in
  the saved-state `Bundle`. The fix is to stop parcelling the bitmap.
- `SvgBuilder.build()` is not idempotent (a second call duplicates the last
  stroke); the `SvgPathBuilder` zero-curve discard guard is dead code. Both are
  pinned by characterization tests.
