package fr.worldarchitect.api;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/** Manages offline copies only. Live dimensions must use a separate lifecycle. */
public final class InstanceManager {
    private final Path worldRoot;
    private final Executor executor;
    private final Map<UUID, InstanceStorage.PreparedInstance> prepared = new ConcurrentHashMap<>();
    private final Map<UUID, LiveInstance> live = new ConcurrentHashMap<>();
    private final Set<UUID> deleting = ConcurrentHashMap.newKeySet();

    public InstanceManager(Path worldRoot, Executor executor) {
        this.worldRoot = worldRoot.toAbsolutePath().normalize();
        this.executor = Objects.requireNonNull(executor);
    }

    public CompletableFuture<UUID> prepare(String collection, String map) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var instance = InstanceStorage.prepare(worldRoot, collection, map);
                live.put(instance.id(), new LiveInstance(instance.id(), instance.terrain()));
                prepared.put(instance.id(), instance);
                return instance.id();
            } catch (IOException e) { throw new CompletionException(e); }
        }, executor);
    }

    public Set<UUID> listPrepared() { return Set.copyOf(prepared.keySet()); }

    public synchronized CompletableFuture<net.minecraft.server.level.ServerLevel> open(net.minecraft.server.MinecraftServer server, UUID id, DimensionBackend backend) {
        var instance = live.get(id);
        if(instance == null || deleting.contains(id)) return CompletableFuture.failedFuture(new IllegalStateException("Instance unavailable"));
        return instance.open(server, backend);
    }

    public synchronized CompletableFuture<Void> close(net.minecraft.server.MinecraftServer server, UUID id, DimensionBackend backend) {
        var instance = live.get(id);
        if(instance == null || deleting.contains(id)) return CompletableFuture.failedFuture(new IllegalStateException("Instance unavailable"));
        return instance.close(server, backend);
    }

    public synchronized CompletableFuture<Boolean> discard(UUID id) {
        var instance = prepared.get(id);
        if (instance == null || !live.get(id).canDiscard() || !deleting.add(id))
            return CompletableFuture.completedFuture(false);
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Path root = worldRoot.resolve("worldarchitect").resolve("instances");
                    Path target = root.resolve(id.toString());
                    if (!target.equals(instance.terrain().toAbsolutePath().normalize()) ||
                        Files.isSymbolicLink(root.getParent()) || Files.isSymbolicLink(root) ||
                        Files.isSymbolicLink(target))
                        throw new IOException("Unsafe instance path");
                    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                        try (var paths = Files.walk(target)) {
                            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                                Files.deleteIfExists(path);
                        }
                    }
                    prepared.remove(id, instance);
                    live.remove(id);
                    return true;
                } catch (IOException e) { throw new CompletionException(e); }
                finally { deleting.remove(id); }
            }, executor);
        } catch (RuntimeException e) { deleting.remove(id); throw e; }
    }
}
