# Publishing `com.github.gcacace:signature-pad` to Maven Central

The build publishes to Maven Central via the **Sonatype Central Portal** using the
[`com.vanniktech.maven.publish`](https://vanniktech.github.io/gradle-maven-publish-plugin/)
Gradle plugin. Coordinates are pinned to **`com.github.gcacace:signature-pad`**
in `signature-pad/build.gradle` so every release stays drop-in compatible for
existing consumers.

> **Historical note:** releases 1.0.0–1.3.1 were published through **Bintray +
> JCenter**, which JFrog **shut down in 2021**. That pipeline no longer works —
> this document describes its modern replacement.

---

## One-time access recovery (maintainer action required)

Bintray is gone and the old signing key may be lost. Before a release can be
cut, the following must be re-established. **These are account/credential steps
only the maintainer can perform** — they cannot be scripted in this repo.

### 1. Sonatype Central Portal namespace

1. Sign in at <https://central.sonatype.com/> (Google/GitHub SSO works).
2. Confirm the **`com.github.gcacace`** namespace is claimed and verified under
   your account.
   - Because the group starts with `com.github.`, verification is done by
     proving ownership of the GitHub account `gcacace` (the Portal creates a
     temporary verification repo you confirm). This is easier than a custom
     domain and is the recommended path here.
   - If the namespace was migrated automatically from the old OSSRH account,
     it should already appear as verified.
3. Generate a **user token** (Portal → *View Account* → *Generate User Token*).
   This yields a username/password pair used for the `mavenCentralUsername` /
   `mavenCentralPassword` Gradle properties below.

### 2. GPG signing key

Central requires every artifact to be PGP-signed.

```bash
# Generate a new key (RSA 4096, no expiry). For a CI-friendly, passphrase-less
# key, use a batch file instead of the interactive prompt:
#   printf '%s\n' 'Key-Type: RSA' 'Key-Length: 4096' 'Subkey-Type: RSA' \
#     'Subkey-Length: 4096' 'Name-Real: Gianluca Cacace' \
#     'Name-Email: gianluca.cacace@gmail.com' 'Expire-Date: 0' \
#     '%no-protection' '%commit' | gpg --batch --generate-key /dev/stdin
gpg --full-generate-key

# List it and note the KEY_ID (the long hex after the algorithm, e.g. rsa4096/ABCD1234...)
gpg --list-secret-keys --keyid-format=long

# Publish the PUBLIC key so Central can verify signatures.
# NOTE: `gpg --send-keys` is broken in some recent GnuPG 2.5.x builds
# ("keyserver send failed: Invalid argument"). If so, upload via HTTP instead:
gpg --armor --export <KEY_ID> > pubkey.asc
curl -s "https://keyserver.ubuntu.com/pks/add" --data-urlencode "keytext@pubkey.asc"
# keyserver.ubuntu.com is the one Central checks and needs no email verification.

# Export the SECRET key in ASCII-armored form for the Gradle build to consume
gpg --armor --export-secret-keys <KEY_ID> > signing-key.asc
```

Keep `signing-key.asc` **out of git**. The repo's `.gitignore` now covers
`*.asc`, `*.gpg`, `*.key`, and `secring.*` — but verify with
`git check-ignore signing-key.asc` before committing anything.

### 3. Local credentials

Put these in `~/.gradle/gradle.properties` (user-global, never in the repo):

```properties
mavenCentralUsername=<portal-token-username>
mavenCentralPassword=<portal-token-password>

# In-memory signing (preferred — points at the exported key)
signingInMemoryKey=<paste the full contents of signing-key.asc, newlines as \n>
signingInMemoryKeyPassword=<key passphrase, or LEAVE EMPTY for a no-passphrase key>
```

> If the key was generated with `%no-protection` (no passphrase), the
> `signingInMemoryKeyPassword` value **must be empty** — a leftover placeholder
> there will be used as the passphrase and signing will fail.

To convert `signing-key.asc` into the single-line `signingInMemoryKey` value:
```bash
awk 'BEGIN{ORS="\\n"} {print}' signing-key.asc   # then paste the result
```

Alternatively use the classic `signing.keyId` / `signing.password` /
`signing.secretKeyRingFile` trio if you prefer a keyring file.

---

## Cutting a release

1. Set the version in `gradle.properties` (`VERSION_NAME` / `VERSION_CODE`).
   - Use a plain version like `1.4.0` for a release; a `-SNAPSHOT` suffix routes
     to the snapshot repository instead.
2. Build and publish:

   ```bash
   export JAVA_HOME=$(/usr/libexec/java_home -v 17)
   ./gradlew :signature-pad:publishAndReleaseToMavenCentral --no-configuration-cache
   ```

   `publishAndReleaseToMavenCentral` uploads **and** auto-releases the
   deployment. To inspect it in the Portal before it goes public, use
   `publishToMavenCentral` instead and click *Publish* manually at
   <https://central.sonatype.com/publishing/deployments>.
3. Tag the release in git (`git tag 1.4.0 && git push --tags`) and cut a GitHub
   Release with the changelog.

## Verifying coordinates without credentials

To confirm the build produces the correct artifact/POM (no account needed):

```bash
./gradlew :signature-pad:publishToMavenLocal            # full artifacts incl. signing
./gradlew :signature-pad:generatePomFileForMavenPublication   # POM only, no signing
cat signature-pad/build/publications/maven/pom-default.xml
```

The POM must show `com.github.gcacace` / `signature-pad` / current version and
`aar` packaging.
