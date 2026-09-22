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

## Pinned versions

| Maven version | Source | Compatibility |
| --- | --- | --- |
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
Pass `token: ${{ secrets.DEPS_TOKEN }}` for private sources. Public legacy builds
need no secret. Prepare other dependencies separately before the Maven build.

## Source development and future publication

The source project's canonical coordinates are already
`me.plugins:tlibs:1.0-SNAPSHOT`. After supplying its declared build dependencies,
`mvn install` installs a source build. A consumer can explicitly test it with
`mvn -Dtlibs.version=1.0-SNAPSHOT clean verify`; source builds never overwrite the
immutable binary versions above. Use Java 25 for current TLibs source.

Once a hosted Maven registry and credentials are configured, publish these
exact artifacts with their minimal POMs, add that registry to consumers, and
remove the installer step. Do not publish the private artifacts to a public
registry by default. Existing consumer coordinates do not need to change.

## Rollback

Revert a consumer's migration commit to restore its previous dependency setup.
The pinned versions coexist in Maven's cache, and no runtime plugin is replaced
by this migration. Reverting the shared installer does not delete cached inputs.
