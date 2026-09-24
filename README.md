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

## Inventory scan API

From TLibs 2.1.0, `net.tfminecraft.tlibs.itemscan.ItemScanService` owns the shared
scanner on Paper. Its synchronous player traversal does not support Folia.
TLibs starts it on enable and stops it on disable. Plugins with a hard
TLibs dependency subscribe an `ItemScanHandler` during enable and unsubscribe
during disable; only TLibs should call `start` or `stop`.

The scanner processes one online player every two ticks, scans inventory-open
events immediately, and calls pickup handlers with a null inventory and slot -1.
Handlers run synchronously in registration order and retain ownership of item
mutation. Existing Core consumers can coexist during migration: update each
consumer to subscribe to one provider only. Deploy TLibs 2.1.0 before migrated
consumers; rolling a consumer back restores its Core scanner subscription.

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/TLibs/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## License

Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE). Third-party dependencies and bundled material
retain their own licenses.
