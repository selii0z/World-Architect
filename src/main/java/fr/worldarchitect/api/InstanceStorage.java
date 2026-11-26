package fr.worldarchitect.api;

import fr.worldarchitect.project.MapCatalog;
import fr.worldarchitect.project.MapTerrain;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

/** Offline terrain staging only; does not register or load a Minecraft dimension. */
public final class InstanceStorage {
    private InstanceStorage() {}
    public record PreparedInstance(UUID id, String collection, String map, Path terrain) {}

    /** Blocking filesystem operation. Invoke on a worker thread. */
    public static PreparedInstance prepare(Path worldRoot, String collection, String map) throws IOException {
        Path source = MapCatalog.resolveTerrain(collection, map);
        Path root = worldRoot.resolve("worldarchitect").resolve("instances");
        if (Files.isSymbolicLink(root.getParent()) || Files.isSymbolicLink(root))
            throw new IOException("Symbolic links are not supported");
        Files.createDirectories(root);
        UUID id = UUID.randomUUID();
        Path destination = root.resolve(id.toString());
        MapTerrain.snapshot(source, destination);
        return new PreparedInstance(id, collection, map, destination);
    }
}
