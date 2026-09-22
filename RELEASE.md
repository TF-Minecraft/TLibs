# TLibs releases

Build from `main` with Java 21 and the dependencies declared in `pom.xml`.
See [dependency setup](DEPENDENCIES.md) for source builds and consumer setup.

The release version comes from the POM. A matching numeric `v*` tag runs Maven
verification and packages the runtime JAR, checksums and build metadata into a
draft release. Review those artifacts before publishing.

Follow the [shared build and release guide](https://github.com/TF-Minecraft/Docs/blob/main/PIPELINES.md)
for tagging, dependency access, provenance and validation. Release-specific logs
and results belong with the release or its PR.
