# TLibs 1.1.0

First versioned release built from this repository. The Java source is unchanged
from `7493b7fe7962b28c972f7d4a910e52b5a7d1fcc4` (latest source change
`541dbcd`, Cooking item checker improvements). Maven and the embedded plugin
both report `1.1.0`. Requires Java 25.

Built with Maven 3.9.9 and Java 25.0.4.1. `mvn clean verify` passes all 10 tests.
The release archive excludes external plugin/server APIs. The SHA-256 is recorded
in `tools/artifacts.json` and published alongside the JAR. The fixed Maven output
timestamp makes repeated builds with identical inputs reproducible.

Compile-time API inputs (private inputs are not published with this release):

| Dependency | Filename | SHA-256 |
| --- | --- | --- |
| spigot-api | `spigot-api.jar` | `b7156eab567772f9fdac0bf696b209111790c3f69149ab9a742e3dd030ec4b10` |
| bungeecord-chat | `BungeeCord.jar` | `3bc6fa2477eb30f840d14c6b9e7074d8326ef9a100add8a6db89e85ecf53a3f2` |
| ItemsAdder | `ItemsAdder_3.5.0-r2.jar` | `0116d714822b6f0cfebfe833a5df2e4754797b637d0fe709afb762c56da463a3` |
| MMOItems | `MMOItems-6.10.jar` | `c84700df5942fd969d3e8c2f1ad2c3ebeb17987ef88b426408d1306e49c4aada` |
| MythicLib | `MythicLib-1.7.jar` | `660ff2a6ec86bc8d7779948cf65c1637e271a16e1ef812d6a273f4a5c5eec73c` |
| item-nbt-api-plugin | `item-nbt-api-plugin-2.15.0.jar` | `a5f593d6214b8dff79d5d304b3a813160836be4b0b714f1e0f79a9e0de0b9ee1` |
| GunsAndGadgets | `gunsandgadgets-1.0.6.jar` | `db5beb70f316d0d5980981d79d9572a8538aa331e8bd7960a8115c483dc98afa` |
| Cooking | `cooking-0.1.5-ALPHA.jar` | `c2b208566fb67db465caaaefdab09a2bbdcbb1261307c1c9d701c3f8a9eda2cc` |

See [DEPENDENCIES.md](DEPENDENCIES.md) for the build command and consumer setup.
Consumer compatibility results and rollout status are recorded in the workspace
migration report. No live Minecraft server was modified by this release.
