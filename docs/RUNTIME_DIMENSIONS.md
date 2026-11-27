# Experimental runtime dimensions and saves

Use only a disposable copy of the server world. Enable with JVM argument
`-Dworldarchitect.experimentalDynamicDimensions=true` before `-jar`.
No additional runtime dependency is introduced. This is not a production release.

- F8 project creation and imported-map activation request a native runtime level
  after the next server tick. Refresh the project list to check readiness.
  Existing project datapacks remain the restart fallback.
- Integrations: obtain InstanceServices.get(server), call prepare(collection,map),
  then InstanceServices.open(server,id) asynchronously. Never block the server
  thread waiting on these futures. Close only after all players have left;
  InstanceServices.close(server,id) moves the saved terrain back to the prepared
  location. Discard explicitly only after a successful close.
- Failure and forced shutdown preserve instance files, including terrain moved to
  dimensions/worldarchitect/instance_*. Automatic remount after a crash is not
  implemented. Inspect the logs and recover offline; never delete a failed instance.
- Native project saves run every five minutes for loaded projects with automatic
  saves enabled. The F8 manual save is native terrain persistence, not a ZIP
  archive. Rotating live ZIP archives still require a validated snapshot backend.

Acceptance: test two different copies of the same imported map, verify different
dimension keys and independent block/entity data, enter and exit, reject close
while occupied, close/reopen, stop/restart, and confirm source map checksums.
Repeat with multiple clients and the complete server modpack. Compilation alone
does not validate dynamic level registration, client teleport, storage or recovery.
