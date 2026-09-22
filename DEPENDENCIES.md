# Shared plugin dependencies

TFMC plugins declare shared plugins using their Maven coordinates and `provided`
scope. Java APIs use `net.tfminecraft.<plugin>` packages. The server supplies each
plugin separately; consumers do not shade provider classes into their own jars.

## Build a consumer

Use Java 21, Maven, and Python 3. Run from the consumer checkout:

```sh
python3 ../tlibs/tools/install-plugins.py --pom pom.xml --mode pinned \
  --lock .build/plugin-dependencies.json
mvn clean verify
```

If the consumer has private third-party dependencies, run its
`.github/scripts/prepare-release.sh` with `GH_TOKEN` set to an authorized
ServerAssets read token before building. The script verifies its pinned
checksums. These inputs stay in `libs/` and are not committed or published.

The installer resolves the exact versions declared in the POM, verifies release
JAR checksums, and installs minimal Maven POMs. Only direct declared dependencies
are installed. Provider build dependencies do not propagate recursively through
cycles such as TLibs/Cooking/GunsAndGadgets or RPCharacters/SimpleFactions.

`tools/plugins.json` defines supported coordinates, release repositories, and
artifact filename patterns. A missing release, artifact, credential, or matching
checksum fails the build. Draft releases are not build inputs.

## Continuous integration

Development and release workflows use exact dependency versions:

```yaml
- name: Install shared plugin dependencies
  uses: TF-Minecraft/TLibs/.github/actions/setup-plugins@<full-action-commit-sha>
  with:
    mode: pinned
```

Keep the action pinned to a full reviewed commit SHA. The action uses
`github.token` to resolve public release assets and writes
`.build/plugin-dependencies.json` with coordinates, checksums, and sources.
Preserve this file with build artifacts and release metadata.

To select newer dependency versions locally, run the installer with
`--mode latest`, then review and commit the resulting POM changes. CI continues
to use the committed versions. Cooking and InteractibleFurniture permit their
ALPHA/BETA release channels; other managed plugins use stable releases.

`--maven-repo /path/to/cache` selects an isolated Maven cache. Pass the same path
to builds with `-Dmaven.repo.local=/path/to/cache`.

## TLibs source builds

TLibs 2.0.0 uses Maven coordinates `me.plugins:tlibs:2.0.0` and Java API package
`net.tfminecraft.tlibs`. Prepare its declared providers, then build and install:

```sh
python3 tools/install-plugins.py --pom pom.xml --mode pinned
mvn clean install
```

The runtime artifact is `target/TLibs-2.0.0.jar`. Its plugin descriptor receives
the Maven version. Build and deploy consumers with matching provider versions.
These commands build artifacts; they do not modify a running Minecraft server.
