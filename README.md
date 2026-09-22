# tlibs

Technical documentation is maintained in [TF-Minecraft/docs](https://github.com/TF-Minecraft/docs/tree/main/projects/tlibs).

Use that project index for setup, configuration, architecture, integration and testing guides. This repository contains the source and project-specific assets.

Shared Maven dependency setup: [DEPENDENCIES.md](DEPENDENCIES.md).

## Builds and releases

With `GH_TOKEN` set for ServerAssets read access, run `bash .github/scripts/prepare-release.sh` and `mvn clean verify`. PR builds run unit tests and publish UTC `DEV-YYYYMMDD-HHmm` JARs. Numeric tags matching the Maven version create draft releases. See the [shared pipeline guide](https://github.com/TF-Minecraft/Docs/blob/main/PIPELINES.md).

## Shared plugin dependencies

Build and release workflows install checksum-verified plugin releases through
[TLibs' shared installer](https://github.com/TF-Minecraft/TLibs/blob/main/DEPENDENCIES.md).
CI selects the latest published versions; local builds use the explicit Maven
version properties. Shared plugins use `provided` scope and remain separate
server plugins. Each build records exact versions and checksums in
`.build/plugin-dependencies.json` alongside its JAR.

From this checkout, with the TLibs repository next to it:

```sh
python3 ../tlibs/tools/install-plugins.py --pom pom.xml
```

Prepare any remaining third-party inputs with `.github/scripts/prepare-release.sh`
before running Maven. Source-unavailable AdvancedCrafting and MusicalInstruments
inputs remain private and checksum-pinned wherever declared; see the installer
documentation for authentication and reproducible rebuilds.
