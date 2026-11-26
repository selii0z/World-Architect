package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import net.neoforged.fml.loading.FMLPaths;

/** Read-only discovery of server map sources. Loading is a separate operation. */
public final class MapCatalog {
    public enum Format { WORLD, REGION, ZIP, SCHEM, NBT, LITEMATIC, PROJECT, EMPTY }
    public record Entry(String collection, String name, Format format) {}
    public record Collection(String name, List<Entry> maps) {}
    private MapCatalog() {}

    public static Path root() throws IOException {
        Path root = FMLPaths.GAMEDIR.get().resolve("worldarchitect").resolve("maps");
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(root.getParent()))
            throw new IOException("Symbolic links are not supported in the map catalog");
        Files.createDirectories(root);
        return root;
    }

    private static boolean directory(Path p) {
        return Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean file(Path p) {
        return Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS);
    }

    private static Path child(Path parent, String name) throws IOException {
        if (name == null || !name.matches("[\\p{L}\\p{N} _.-]{1,80}") ||
                name.equals(".") || name.equals("..") || name.endsWith(".") || name.endsWith(" "))
            throw new IOException("Invalid map or collection name");
        Path path = parent.resolve(name);
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not supported");
        return path;
    }

    public static Path createCollection(String name) throws IOException {
        Path path = child(root(), name);
        Files.createDirectories(path);
        return path;
    }

    public static Path reserveMap(String collection, String name) throws IOException {
        Path path = child(createCollection(collection), name);
        return Files.createDirectory(path);
    }

    public static void linkProject(Path folder, Project project) throws IOException {
        Files.writeString(folder.resolve("project.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(project), StandardOpenOption.CREATE_NEW);
    }

    public static Path resolveSchematic(String collection,String name) throws IOException {
        Path parent=child(root(),collection);
        Path source=child(parent,name);
        if(!directory(parent)||!file(source)||!(name.toLowerCase(Locale.ROOT).endsWith(".schem")||name.toLowerCase(Locale.ROOT).endsWith(".litematic")))
            throw new IOException("Schematic or Litematica file required");
        return source;
    }

    public static Path resolveTerrain(String collection,String name) throws IOException {
        Path parent=child(root(),collection);
        Path source=child(parent,name);
        if(!directory(parent)||!directory(source)||!directory(source.resolve("region"))||Files.exists(source.resolve("session.lock"),LinkOption.NOFOLLOW_LINKS)) throw new IOException("Offline terrain folder required");
        return source;
    }

    public static Path importSource(String collection, String sourceName, String destinationName) throws IOException {
        Path parent = child(root(), collection);
        if (!directory(parent)) throw new IOException("Unknown collection");
        Path source = child(parent, sourceName);
        Path destination = child(parent, destinationName);
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(destinationName);
        Format type = format(source);
        if (type == Format.ZIP) return MapArchive.importTerrain(source, destination);
        if ((type == Format.WORLD || type == Format.REGION) && directory(source.resolve("region"))) {
            if (Files.exists(source.resolve("session.lock"), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Import an offline copy without session.lock");
            return MapTerrain.snapshot(source, destination);
        }
        if (type == Format.REGION) return importLooseRegions(source, destination);
        throw new IOException("Unsupported terrain source");
    }

    private static Path importLooseRegions(Path source, Path destination) throws IOException {
        List<Path> regions;
        if (file(source)) regions = List.of(source);
        else if (directory(source)) {
            try (Stream<Path> entries = Files.list(source)) {
                regions = entries.filter(MapCatalog::file)
                    .filter(p -> p.getFileName().toString().matches("r\\.-?[0-9]+\\.-?[0-9]+\\.mca")).toList();
            }
        } else throw new IOException("Invalid region source");
        if (regions.isEmpty()) throw new IOException("No region files found");
        for (Path region : regions) {
            if (!region.getFileName().toString().matches("r\\.-?[0-9]+\\.-?[0-9]+\\.mca"))
                throw new IOException("Invalid region filename");
            if (Files.size(region) < 8192 || Files.size(region) % 4096 != 0)
                throw new IOException("Invalid region file size: " + region.getFileName());
        }
        Path staging = Files.createTempDirectory(destination.getParent(), ".region-import-");
        try {
            Path folder = Files.createDirectory(staging.resolve("region"));
            for (Path region : regions) Files.copy(region, folder.resolve(region.getFileName()), LinkOption.NOFOLLOW_LINKS);
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(destination.toString());
            Files.move(staging, destination);
            return destination;
        } finally {
            if (Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> paths = Files.walk(staging)) {
                    for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                }
            }
        }
    }

    private static Format format(Path path) throws IOException {
        if (directory(path)) {
            if (file(path.resolve("project.json"))) return Format.PROJECT;
            if (file(path.resolve("level.dat"))) return Format.WORLD;
            if (directory(path.resolve("region"))) return Format.REGION;
            try (Stream<Path> children = Files.list(path)) {
                if (children.anyMatch(p -> file(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mca")))
                    return Format.REGION;
            }
            return Format.EMPTY;
        }
        if (!file(path)) return null;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip")) return Format.ZIP;
        if (name.endsWith(".schem") || name.endsWith(".schematic")) return Format.SCHEM;
        if (name.endsWith(".nbt")) return Format.NBT;
        if (name.endsWith(".litematic")) return Format.LITEMATIC;
        if (name.endsWith(".mca")) return Format.REGION;
        return null;
    }

    public static List<Collection> scan() throws IOException {
        List<Collection> result = new ArrayList<>();
        try (Stream<Path> collections = Files.list(root())) {
            for (Path collection : collections.filter(MapCatalog::directory).sorted().toList()) {
                String name = collection.getFileName().toString();
                List<Entry> entries = new ArrayList<>();
                try (Stream<Path> maps = Files.list(collection)) {
                    for (Path map : maps.sorted().toList()) {
                        Format format = format(map);
                        if (format != null) entries.add(new Entry(name, map.getFileName().toString(), format));
                    }
                }
                result.add(new Collection(name, List.copyOf(entries)));
            }
        }
        return List.copyOf(result);
    }
}
