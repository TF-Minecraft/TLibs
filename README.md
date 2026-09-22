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
- **Shared persistence and utilities** — provides database support and common text, time, and location helpers for dependent plugins.

TLibs supports the gameplay plugins that provide the player-facing experiences, keeping common behavior in one place.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/TLibs/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
