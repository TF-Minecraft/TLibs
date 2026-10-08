# TLibs

> Shared item handling and plugin foundations for TF-Minecraft.

TLibs is a supporting library plugin used throughout the TF-Minecraft ecosystem. It gives gameplay plugins a common way to recognize and create items, work with custom blocks, and carry important item details through supported changes.

Players encounter its effects through the plugins that depend on it: custom equipment, crafting ingredients, item skins, and socketed upgrades can share the same underlying item handling.

## Features

- **Common item recognition** — shared handling for vanilla items and supported custom-item sources, including MMOItems and ItemsAdder.
- **Custom block support** — recognizes vanilla blocks, ItemsAdder blocks, and supported furniture hitboxes.
- **Equipment appearance preservation** — carries supported skin and appearance information through item rebuilding.
- **Tiered socket handling** — shared rules for socket categories and the gems or runes applied to them.
- **Equipment events** — exposes armour equip changes so other plugins can respond consistently.
- **Inventory scanning** — shared periodic and event-driven scans; gameplay plugins register handlers for their own item updates.
- **Shared persistence and utilities** — provides database support and common text, time, and location helpers for dependent plugins.

TLibs supports the gameplay plugins that provide the player-facing experiences, keeping common behavior in one place.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/TLibs/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests and coverage

Run `mvn clean verify` with Java 21 after preparing the pinned dependencies.
JUnit 5, Mockito and MockBukkit exercise item/block APIs, equipment and socket
handling, inventory scanning, SQLite storage and plugin lifecycle. These tests
do not start a live Paper server or verify the full dependent-plugin stack.

JaCoCo enforces 100% production line coverage with no exclusions; instruction
and branch coverage are reported separately. HTML/XML reports are written to
`target/site/jacoco/`, and Surefire results to `target/surefire-reports/`.
Build CI uploads both.

Run `python3 -m unittest discover -s tests -v` for the dependency installer and
artifact validation tests. These use Python's standard-library `unittest`,
report to the terminal and are outside the Java coverage gate. The Dependency
installer workflow also checks release resolution and Maven installation.

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
