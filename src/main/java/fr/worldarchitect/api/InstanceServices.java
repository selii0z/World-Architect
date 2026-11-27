package fr.worldarchitect.api;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid="worldarchitect")
public final class InstanceServices {
    private record Service(InstanceManager manager, ExecutorService executor) {}
    private static final Map<MinecraftServer, DimensionBackend> BACKENDS = new IdentityHashMap<>();
    /** Register a runtime implementation once, from the server thread. */
    public static synchronized void registerBackend(MinecraftServer server, DimensionBackend backend) {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
        java.util.Objects.requireNonNull(backend);
        if (!backend.supported(server)) throw new IllegalArgumentException("Unsupported dimension backend");
        if (BACKENDS.putIfAbsent(server, backend) != null) throw new IllegalStateException("Dimension backend already registered");
    }
    public static synchronized java.util.Optional<DimensionBackend> backend(MinecraftServer server) {
        return java.util.Optional.ofNullable(BACKENDS.get(server));
    }
    public static java.util.concurrent.CompletableFuture<net.minecraft.server.level.ServerLevel> open(MinecraftServer server, java.util.UUID id) {
        var backend = backend(server);
        return backend.isPresent() ? get(server).open(server,id,backend.get()) :
            java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("No runtime dimension backend installed"));
    }
    public static java.util.concurrent.CompletableFuture<Void> close(MinecraftServer server, java.util.UUID id) {
        var backend = backend(server);
        return backend.isPresent() ? get(server).close(server,id,backend.get()) :
            java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("No runtime dimension backend installed"));
    }
    /** Prepare and mount an independent map copy; completion means the level is registered. */
    public static java.util.concurrent.CompletableFuture<net.minecraft.server.level.ServerLevel> createInstance(
            MinecraftServer server, String collection, String map) {
        var backend = backend(server);
        if (backend.isEmpty()) return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalStateException("No runtime dimension backend installed"));
        var manager = get(server);
        return manager.prepare(collection, map).thenCompose(id -> manager.open(server, id, backend.get()));
    }

    /** Close an empty instance before deleting its private terrain. */
    public static java.util.concurrent.CompletableFuture<Boolean> destroyInstance(MinecraftServer server, java.util.UUID id) {
        var backend = backend(server);
        if (backend.isEmpty()) return java.util.concurrent.CompletableFuture.failedFuture(
                new IllegalStateException("No runtime dimension backend installed"));
        var manager = get(server);
        return manager.close(server, id, backend.get()).thenCompose(ignored -> manager.discard(id));
    }

    private static final Map<MinecraftServer, Service> SERVICES = new IdentityHashMap<>();
    private InstanceServices() {}

    @SubscribeEvent public static synchronized void start(ServerStartingEvent event) {
        var server = event.getServer();
        var executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "WorldArchitect-Instances");
            thread.setDaemon(true);
            return thread;
        });
        SERVICES.put(server, new Service(new InstanceManager(server.getWorldPath(LevelResource.ROOT), executor), executor));
        if(RuntimeDimensionBackend.ENABLED)registerBackend(server,new RuntimeDimensionBackend(server));
    }

    public static synchronized InstanceManager get(MinecraftServer server) {
        var service = SERVICES.get(server);
        if (service == null) throw new IllegalStateException("Instance service unavailable");
        return service.manager();
    }

    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        backend(event.getServer()).ifPresent(b->{if(b instanceof RuntimeDimensionBackend runtime)runtime.drain();});
    }
    @SubscribeEvent public static synchronized void stop(ServerStoppingEvent event) {
        var backend=BACKENDS.remove(event.getServer());
        if(backend instanceof RuntimeDimensionBackend runtime)runtime.stop();
        var service = SERVICES.remove(event.getServer());
        if (service != null) service.executor().shutdown();
    }
}
