package fr.worldarchitect.project;

import net.minecraft.server.level.ServerLevel;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Implementations must coordinate chunk, entity and POI writes.
 * A completed future guarantees an isolated, immutable terrain copy.
 * Flushing a loaded level alone does not satisfy this contract.
 */
public interface ProjectSnapshotBackend {
 boolean supported(ServerLevel level);
 CompletableFuture<Path> snapshot(ServerLevel level,Path destination);
}
