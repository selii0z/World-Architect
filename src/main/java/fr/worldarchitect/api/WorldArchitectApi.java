package fr.worldarchitect.api;

import fr.worldarchitect.project.MapCatalog;
import java.io.IOException;
import java.util.List;

/** Server-side integration entry point. No dependency on gameplay mods. */
public final class WorldArchitectApi {
    public static final int API_VERSION = 2;
    private WorldArchitectApi() {}

    public record InstanceHandle(java.util.UUID id, net.minecraft.server.level.ServerLevel level) {}

    /** Nonblocking creation with explicit identity for later cleanup. */
    public static java.util.concurrent.CompletableFuture<InstanceHandle> createInstance(
            net.minecraft.server.MinecraftServer server, String collection, String map) {
        var backend = InstanceServices.backend(server);
        if (backend.isEmpty()) return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalStateException("Runtime dimensions unavailable"));
        var manager = InstanceServices.get(server);
        return manager.prepare(collection, map).thenCompose(id ->
                manager.open(server, id, backend.get()).thenApply(level -> new InstanceHandle(id, level)));
    }

    public static java.util.concurrent.CompletableFuture<Boolean> destroyInstance(
            net.minecraft.server.MinecraftServer server, java.util.UUID id) {
        return InstanceServices.destroyInstance(server, id);
    }

    public static boolean runtimeAvailable(net.minecraft.server.MinecraftServer server) {
        return InstanceServices.backend(server).isPresent();
    }

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
