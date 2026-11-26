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
    private final Set<UUID> deleting = ConcurrentHashMap.newKeySet();

    public InstanceManager(Path worldRoot, Executor executor) {
        this.worldRoot = worldRoot.toAbsolutePath().normalize();
        this.executor = Objects.requireNonNull(executor);
    }

    public CompletableFuture<UUID> prepare(String collection, String map) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var instance = InstanceStorage.prepare(worldRoot, collection, map);
                prepared.put(instance.id(), instance);
                return instance.id();
            } catch (IOException e) { throw new CompletionException(e); }
        }, executor);
    }

    public Set<UUID> listPrepared() { return Set.copyOf(prepared.keySet()); }

    public CompletableFuture<Boolean> discard(UUID id) {
        var instance = prepared.get(id);
        if (instance == null || !deleting.add(id))
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
                    return true;
                } catch (IOException e) { throw new CompletionException(e); }
                finally { deleting.remove(id); }
            }, executor);
        } catch (RuntimeException e) { deleting.remove(id); throw e; }
    }
}
