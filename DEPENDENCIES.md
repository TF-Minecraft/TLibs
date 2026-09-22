# Shared plugin dependencies

TFMC plugins declare each shared plugin using its native Maven coordinates and
`provided` scope. The server supplies those plugins separately. The shared
installer verifies release JAR checksums and installs minimal Maven POMs, so
provider build dependencies and cyclic source references do not propagate.

## Build a consumer

With Python 3, Maven and the appropriate JDK on PATH:

```sh
python3 ../tlibs/tools/install-plugins.py --pom pom.xml --lock .build/plugin-dependencies.json
# Prepare the remaining third-party inputs, if the consumer requires them:
GH_TOKEN="$(gh auth token)" bash .github/scripts/prepare-release.sh
mvn clean verify
```

Local builds default to the exact versions in the POM. `--mode latest` selects
published releases and updates the checkout's dependency version properties only
after all inputs pass verification. CI uses this mode. Stable releases use
GitHub's latest release selection. Cooking and InteractibleFurniture explicitly
allow their existing ALPHA/BETA releases; drafts are always excluded. Missing
releases, assets, credentials or checksum mismatches fail the build.

`tools/plugins.json` is the registry of supported coordinates, release repositories
and filename patterns. Only direct declared dependencies are installed. This
handles TLibs/Cooking/GunsAndGadgets and RPCharacters/SimpleFactions cycles without
recursively building their source or shading their classes into consumers.

```yaml
- name: Install shared plugin dependencies
  uses: TF-Minecraft/TLibs/.github/actions/setup-plugins@<full-action-commit-sha>
  with:
    mode: latest
    private-token: ${{ secrets.DEPS_TOKEN }} # Only consumers of the private inputs below
```

The action writes `.build/plugin-dependencies.json` with exact coordinates,
checksums, sources and selection modes. Consumer workflows preserve it with the
build artifacts and release metadata. Keep the action pinned to a full commit;
publishing a release promotes its API to the next consumer build.

## Source unavailable

AdvancedCrafting 1.2.1 and MusicalInstruments 2.5 remain checksum-pinned private
ServerAssets inputs in both modes. AdvancedCrafting source is not yet available;
MusicalInstruments' current source is 2.4 and lacks ActivityTF's InstrumentPlayEvent.
Do not publish these binaries publicly or substitute incompatible source builds.
Supply `TFMC_PRIVATE_TOKEN` with Contents read access to ServerAssets, or pass
`--assets ../server-assets` for a local checkout. Public release requests use the
separate `GH_TOKEN` (the action defaults to `github.token`).

## Rebuild and rollback

Use `--mode pinned` and the versions recorded in a successful build's metadata
for an exact API selection. Versions coexist in Maven's cache; `--maven-repo`
selects an isolated cache. Revert a consumer migration commit to restore its
previous preparation path; retained private inputs are not deleted. This only
changes builds and does not install plugins on a running server.

The standalone TLibs installer below remains supported for older consumers.

---

## Legacy standalone TLibs installer

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

Local consumer defaults use `me.plugins:tlibs:1.1.0`, built from the current
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

## CI: latest stable release

Older consumers can call the standalone action with
`version: latest`. The action resolves GitHub's **latest published stable release**
once per build, downloads its versioned JAR and verifies its SHA-256 against the
GitHub asset digest (or the release checksum file). Draft releases and prereleases
are excluded. Publishing a new stable release marked latest is the promotion
step; DEV build artifacts and draft tags do not change consumer builds.

The action installs the actual release version in Maven and updates only the
`tlibs.version` property in the checked-out consumer POM after installation succeeds.
Every following Maven command in that job therefore uses that same version.
No source commit is created, and local builds retain their explicit POM default.
Failures stop the job rather than silently selecting an older release.

```yaml
- name: Install latest stable TLibs in Maven
  uses: TF-Minecraft/TLibs/.github/actions/setup@<full-action-commit-sha>
  with:
    version: latest
```

Keep the action implementation pinned to a full commit SHA; release selection is
dynamic. Outputs `version` and `sha256`, plus the build log, record the exact input.
For a reproducible rebuild or rollback, use `version: pinned` (the default) and
set the POM's `tlibs.version` to a catalogued version. Other private build inputs
still require their usual credentials. Latest TLibs is public; the action's default
GitHub token only authenticates the release API request.

To opt into this behavior locally (this edits your POM):

```sh
python3 ../tlibs/tools/install-dependency.py --pom pom.xml --latest
```

## TLibs source builds

The source coordinates are `me.plugins:tlibs:1.1.0`. With Java 25, install its
provided Cooking and GunsAndGadgets APIs, prepare the remaining third-party inputs,
and build:

```sh
python3 tools/install-plugins.py --pom pom.xml
GH_TOKEN="$(gh auth token)" bash .github/scripts/prepare-release.sh
mvn clean verify
```

Output is `target/TLibs-1.1.0.jar`; `plugin.yml` receives the same Maven version.
Packaging never copies the JAR into a developer's server directory. The optional
integrations remain external; only Commons Lang and SQLite are shaded.
See [RELEASE.md](RELEASE.md) for the original 1.1.0 release inputs and validation.
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
