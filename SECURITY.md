# Security Policy

## Supported Versions

Security fixes are applied to the latest released version of
`com.github.gcacace:signature-pad`. Please make sure you are on the latest
release before reporting an issue.

| Version | Supported          |
| ------- | ------------------ |
| 1.4.x   | :white_check_mark: |
| < 1.4   | :x:                |

## Reporting a Vulnerability

Please **do not** report security vulnerabilities through public GitHub issues,
as that discloses the problem before a fix is available.

Instead, report them privately to the maintainer,
[@gcacace](https://github.com/gcacace), by reaching out through GitHub.

Please include as much of the following as you can:

- A description of the vulnerability and its potential impact.
- Steps to reproduce, or a proof-of-concept.
- The affected version(s) of the library.

You can expect an acknowledgement of your report and, once the issue is
confirmed, coordination on a fix and a disclosure timeline.

## Scope

This is a client-side Android UI library for capturing signatures. It performs
no networking and stores nothing on its own; the most relevant concerns are
around how signature data is rendered, serialized (bitmap/SVG), and persisted in
saved-state. Reports that fall within that surface are in scope.
