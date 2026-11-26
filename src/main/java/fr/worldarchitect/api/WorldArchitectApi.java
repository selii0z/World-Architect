package fr.worldarchitect.api;

import fr.worldarchitect.project.MapCatalog;
import java.io.IOException;
import java.util.List;

/** Server-side integration entry point. No dependency on gameplay mods. */
public final class WorldArchitectApi {
    public static final int API_VERSION = 1;
    private WorldArchitectApi() {}

    public record MapInfo(String collection, String name, String format) {}

    /** Performs filesystem IO; call outside the server tick thread. */
    public static List<MapInfo> listMaps() throws IOException {
        return MapCatalog.scan().stream()
            .flatMap(collection -> collection.maps().stream())
            .map(map -> new MapInfo(map.collection(), map.name(), map.format().name()))
            .toList();
    }

    public static boolean containsMap(String collection, String name) throws IOException {
        return listMaps().stream().anyMatch(map ->
            map.collection().equals(collection) && map.name().equals(name));
    }
}
