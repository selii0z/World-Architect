# Experimental construction acceptance tests

Use a disposable world and test account only. Back up the entire server first. Enable with JVM argument `-Dworldarchitect.experimentalConstruction=true` before `-jar` (or in the launcher JVM arguments for singleplayer). Default remains disabled.

## Session checks
- Create a project, restart, select it in F8 and click Construire.
- Before entry record inventory, armor, offhand, selected slot, XP, position and game mode. Include modded items and separately record accessory slots and backpacks.
- Verify original inventory is absent in construction. Add disposable creative items, then leave via F8. Compare all recorded data; creative items must not escape.
- Repeat with disconnect, normal server stop and forced termination of the disposable server during construction. Reconnect and verify recovery. Never delete recovery journals to bypass an error.
- Verify non-operators cannot enter or submit edits, and that disabled mode rejects entry.

## Edit checks
- Select two corners in loaded chunks; test all seven shapes, replacement filter, cancellation, undo and redo. Compare exact block states.
- Test outside bounds, unloaded chunks, block entities and concurrent edits; invalid operations must be rejected.
- Stop during an edit and inspect pending archives. Automatic edit recovery is not implemented yet; do not treat this case as passing.

## Release gate
Record Minecraft/NeoForge versions, installed mods, logs and pass/fail for each check. Modded accessory isolation, crash recovery of block edits and dynamic dimensions are not validated. Do not enable on a production server until these limitations are addressed.
