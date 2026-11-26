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
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(root.getParent()))
            throw new IOException("Symbolic links are not supported in the map catalog");
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
