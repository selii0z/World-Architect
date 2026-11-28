# Interrupted edit recovery

Use the original operator account in the affected project. Load all affected chunks before using F8 > Tools > Recover. Conflicts prevent recovery; never overwrite conflicting blocks automatically.

The pending archive is intentionally retained even after successful restoration. Stop the server normally, back up the entire world including worldarchitect, and verify restored terrain and history before any manual journal handling. Do not remove pending archives merely to bypass the lock. Interrupted undo/redo may require history reconciliation. There is no automatic unlock yet.

Test on disposable worlds only. Verify normal completion, interrupted edits, interrupted recovery, conflicting changes, unloaded chunks, and interrupted undo/redo. Compilation does not establish crash durability.
