package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** Copies native terrain from an offline or otherwise quiescent source.
 * Never call against a world with active chunk writes. */
public final class MapTerrain {
    private MapTerrain() {}

    public static Path snapshot(Path source, Path destination) throws IOException {
        source = source.toAbsolutePath().normalize();
        destination = destination.toAbsolutePath().normalize();
        if (destination.startsWith(source) || source.startsWith(destination))
            throw new IOException("Overlapping terrain paths");
        if (!Files.isDirectory(source.resolve("region"), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Missing region directory");
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
            throw new FileAlreadyExistsException(destination.toString());
        Path parent = destination.getParent();
        Files.createDirectories(parent);
        Path staging = Files.createDirectory(parent.resolve(".terrain-" + UUID.randomUUID()));
        try {
            for (String name : List.of("region", "entities", "poi")) {
                Path from = source.resolve(name);
                if (!Files.exists(from, LinkOption.NOFOLLOW_LINKS)) continue;
                if (!Files.isDirectory(from, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Invalid terrain directory: " + name);
                try (Stream<Path> paths = Files.walk(from)) {
                    for (Path entry : paths.toList()) {
                        Path target = staging.resolve(name).resolve(from.relativize(entry));
                        if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(target);
                        else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) Files.copy(entry, target);
                        else throw new IOException("Unsupported terrain entry: " + entry.getFileName());
                    }
                }
            }
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
            return destination;
        } finally {
            if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> paths = Files.walk(staging)) {
                    for (Path entry : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(entry);
                }
            }
        }
    }
}
