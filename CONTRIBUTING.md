# Contributing to Android Signature Pad

Thanks for taking the time to contribute! This library is a small, focused
Android view, and contributions of all kinds — bug reports, fixes, docs, and
features — are welcome.

## Ways to contribute

- **Report a bug** using the *Bug report* issue template. A minimal reproduction
  (or a failing test) is the single most helpful thing you can include.
- **Request a feature** using the *Feature request* issue template.
- **Ask a question** — but please check the [README](README.md) and existing
  issues first; usage questions are often already answered there.
- **Open a pull request** for a fix or feature (see below).

## Project layout

- `signature-pad/` — the published library (`com.github.gcacace:signature-pad`).
- `SignaturePad-Example/` — a sample app that exercises the library end to end.

## Building and testing

The project builds with the Gradle wrapper. You need **JDK 17** (the Android
Gradle Plugin requires it) and the Android SDK.

```bash
# Unit tests (JUnit + Robolectric) — the fast inner loop
./gradlew :signature-pad:testDebugUnitTest

# Lint
./gradlew :signature-pad:lintRelease

# Assemble the library and the example app
./gradlew :signature-pad:assembleRelease :SignaturePad-Example:assembleDebug
```

These are exactly the checks that run in CI (see
[`.github/workflows/ci.yml`](.github/workflows/ci.yml)) on every push and pull
request, so running them locally before pushing avoids surprises.

## Pull request guidelines

- **Branch** from `master` and open the PR against `master`.
- **Keep the public API stable.** This is a widely used library
  (`com.github.gcacace.signaturepad.views.SignaturePad`, its XML attributes, and
  the `utils` package are public surface). Prefer additive, backwards-compatible
  changes. If a breaking change is genuinely needed, call it out explicitly in the
  PR description so it can be scheduled for a major release.
- **Add or update tests.** New behavior should come with a test that fails without
  the change. The test suite runs under Robolectric; rendering-sensitive tests use
  `@GraphicsMode(GraphicsMode.Mode.NATIVE)` so that canvas drawing actually
  rasterizes.
- **Match the surrounding code style** (the code is Java; match its naming,
  indentation, and comment density).
- **Update the docs** — the [README](README.md) and
  [CHANGELOG](CHANGELOG.md) — when your change is user-visible. New entries go
  under the `[Unreleased]` heading in the changelog.
- **Keep commits focused** and write a clear description of *what* changed and
  *why*.

## Reporting security issues

Please do **not** open a public issue for security vulnerabilities. See
[SECURITY.md](SECURITY.md) for how to report them privately.

## Code of Conduct

This project adheres to a [Code of Conduct](CODE_OF_CONDUCT.md). By participating,
you are expected to uphold it.

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE), the same license that covers this project.
