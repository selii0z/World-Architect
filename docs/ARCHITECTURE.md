# World Architect 0.1.0

Minecraft 1.21.1 / NeoForge / Java 21. Standalone client and server mod.

## Core
- Project registry with UUID, display name, dimension identifier, bounds, generation profile and schema version.
- Server-authoritative administration; F8 client UI, permission checks on every mutation.
- Project creation, import, duplication, entry and exit. Dynamic dimension lifecycle must be validated against NeoForge before enabling creation.
- Isolated creative editing sessions with durable recovery journal for original inventory, game mode and return position.
- Save project metadata atomically; flush world chunks before consistent backups. Never copy actively changing region files without coordination.
- Automatic saves every five minutes; retain ten rotating backups, plus named manual snapshots. Restore only offline or with dimension safely unloaded. Never delete source data on failed restore.
- Test sessions operate on independent copies.

## Extensions
Namespaced marker types and versioned exports for Escape From Minecraft, PokéLeague, Field Orders and PokéWorld. No mandatory dependencies on those mods.

## UI
French-first localization, scrollable project list, creation wizard, construction toolbar, explicit save and backup status. Destructive actions require confirmation.

## Acceptance tests
Restart persistence, disconnect recovery, unauthorized request rejection, backup/restore integrity, concurrent editor safety, and isolation of test worlds.

Implementation status: design only; no compiled mod yet.
