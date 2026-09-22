# Using TLibs from Maven

Consumers declare `me.plugins:tlibs` with `provided` scope. TLibs remains a
separate server plugin and is never shaded into its consumers. No consumer needs
a TLibs JAR in `libs/` or a developer-specific `tfmc.tlibs` path.

There is no configured hosted Maven registry yet. Until one is available, this
repository supplies one shared installer using Maven's standard `install-file`
goal. It verifies the entire JAR before installing and writes a minimal POM so
obsolete system paths in embedded POMs cannot propagate to consumers.

## Local builds

With Python 3, Maven and the appropriate JDK on PATH, run from a consumer:

```sh
python3 ../tlibs/tools/install-dependency.py --pom pom.xml
mvn clean verify
```

The installer uses the consumer's `tlibs.version`. For private sources,
authenticate `gh` with Contents read access to the repository listed below, or
use a local copy without network access to that source:

```sh
python3 ../tlibs/tools/install-dependency.py --pom pom.xml --assets ../server-assets
# Alternatively, any local JAR with exactly the pinned checksum:
python3 ../tlibs/tools/install-dependency.py --pom pom.xml --jar /path/to/TLibs.jar
```

`--maven-repo /path/to/cache` selects an isolated Maven repository. Pass the same
path to subsequent builds with `-Dmaven.repo.local=/path/to/cache`. Installation
can be repeated and is shared by all consumers using that Maven cache.

Other plugins' existing non-TLibs dependencies still need their own preparation.
The installer does not launch Minecraft or copy anything to a server.

## Current release

All TF-Minecraft consumers use `me.plugins:tlibs:1.1.0`, built from the current
TLibs source. Download [TLibs-1.1.0.jar](https://github.com/TF-Minecraft/TLibs/releases/download/v1.1.0/TLibs-1.1.0.jar)
for the server. Both compilation and server runtime require **Java 25**. Stop the
server, replace the existing TLibs JAR (do not leave two copies), and restart.
The installer downloads this public release without private-repository credentials.

The release includes the repository's current Cooking item-path and tall-furniture
handling, plus its existing APIs. This is a source upgrade, not a relabelled legacy
binary. Consumer compilation and unit tests do not replace an in-game smoke test.

## Retained versions for rollback

| Maven version | Source | Compatibility |
| --- | --- | --- |
| `1.1.0` | Public TLibs release `v1.1.0` | Current source; Java 25 |
| `1.0-legacy-d8062cdca816` | Immutable public Surgery commit | Exact former Surgery, GeigerCounters and DenarEconomy bundled JAR; Java 17 bytecode |
| `1.0-tfmc-bbac27e3055d` | Pinned private `TF-Minecraft/server-assets` commit | Verified TFMC runtime binary; requires JDK/server Java 25 |
| `1.0-tfmc-ecddb0e40a4d` | Pinned private ServerAssets commit; original `JustinasLa/tfmc-deps` release `v1` as an alternate source | Exact ActivityTF, RPCharacters and TFMCCore checksum; Java 17 bytecode |

The full SHA-256 values and exact source references live in
[`tools/artifacts.json`](tools/artifacts.json). For the legacy private binary, the installer first tries ServerAssets, then
its original private release if access to ServerAssets is unavailable. Both
sources must match the same full checksum; a mismatch fails the installation.
`--jar` supports an authorized local copy with that same checksum. The version strings identify actual binary
content because these different JARs all advertise plugin version `1.0`.

Consumers that previously used an external, unversioned Windows path now pin the
verified runtime build. This establishes a reproducible baseline; it is not a
claim that the unavailable original Windows JAR was byte-identical.

## CI

After setting up Java/Maven, call `.github/actions/setup` from this repository
using its full commit SHA. The action reads `pom.xml` and runs the same installer.
Pass `token: ${{ secrets.DEPS_TOKEN }}` for private sources. Public releases and legacy builds
need no secret. Prepare other dependencies separately before the Maven build.

## Source builds

The source coordinates are `me.plugins:tlibs:1.1.0`. Supply the declared
compile-time API JARs and build with Java 25:

```sh
mvn -Dtfmc.refs=/path/to/reference-jars \
    -Dtfmc.plugins=/path/to/gunsandgadgets \
    -Dtfmc.cooking=/path/to/cooking clean verify
```

Output is `target/TLibs-1.1.0.jar`; `plugin.yml` receives the same Maven version.
Packaging never copies the JAR into a developer's server directory. The optional
integrations remain external; only Commons Lang and SQLite are shaded.
GunsAndGadgets 1.0.6 and Cooking 0.1.5-ALPHA provide compile-time APIs.
See [RELEASE.md](RELEASE.md) for the release inputs and validation.
Do not overwrite a published version: change the Maven version for the next release.

Once a hosted Maven registry and credentials are configured, publish these
exact artifacts with their minimal POMs, add that registry to consumers, and
remove the installer step. Do not publish the private artifacts to a public
registry by default. Existing consumer coordinates do not need to change.

## Rollback

Revert a consumer's upgrade commit to restore its previous pinned TLibs version.
For runtime rollback, restore the previous server TLibs JAR and restart.
The pinned versions coexist in Maven's cache, and no runtime plugin is replaced
by this migration. Reverting the shared installer does not delete cached inputs.
