# tlibs

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs/blob/main/projects/TLibs/README.md).

Use that project index for setup, configuration, architecture, integration and testing guides. This repository contains the source and project-specific assets.

Shared Maven dependency setup: [DEPENDENCIES.md](DEPENDENCIES.md).

## Builds and releases

With `GH_TOKEN` set for ServerAssets read access, run `bash .github/scripts/prepare-release.sh` and `mvn clean verify`. PR builds run unit tests and publish UTC `DEV-YYYYMMDD-HHmm` JARs. Numeric tags matching the Maven version create draft releases. See the [shared pipeline guide](https://github.com/TF-Minecraft/Docs/blob/main/PIPELINES.md).
