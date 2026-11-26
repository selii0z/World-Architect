package fr.worldarchitect.project;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.io.IOException;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

@EventBusSubscriber(modid="worldarchitect")
public final class ProjectService {
 private static final Map<MinecraftServer, ProjectRegistry> REGISTRIES = new IdentityHashMap<>();
 @SubscribeEvent public static void start(ServerStartingEvent event) {
  MinecraftServer server = event.getServer();
  try {
   ProjectRegistry registry = new ProjectRegistry(server.getWorldPath(LevelResource.ROOT).resolve("worldarchitect"));
   REGISTRIES.put(server, registry);
   for (Project project : registry.list()) {
    var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(project.dimensionId()));
    var level=server.getLevel(key);
    if(level!=null){
     ProjectWorlds.configureBorder(level,project);
    }else com.mojang.logging.LogUtils.getLogger().info("Project {} dimension not loaded; restart with project datapack enabled",project.id());

    try { ProjectDatapack.stage(server, project); }
    catch (java.nio.file.FileAlreadyExistsException ignored) {}
    catch (IOException e) { com.mojang.logging.LogUtils.getLogger().error("Cannot stage project {}", project.id(), e); }
   }
  } catch(IOException e) {
   com.mojang.logging.LogUtils.getLogger().error("World Architect registry unavailable; existing data preserved", e);
  }
 }
 @SubscribeEvent public static void stop(ServerStoppedEvent event) {
  REGISTRIES.remove(event.getServer());
 }
 public static java.util.Optional<Project> forLevel(net.minecraft.server.level.ServerLevel level){
  var registry=REGISTRIES.get(level.getServer());
  if(registry==null)return java.util.Optional.empty();
  String dimension=level.dimension().location().toString();
  return registry.list().stream().filter(p->p.dimensionId().equals(dimension)).findFirst();
 }
 private static ProjectRegistry authorized(ServerPlayer player) {
  if (!player.hasPermissions(2)) throw new SecurityException("Administrator required");
  MinecraftServer server = player.getServer();
  if (server == null || !server.isSameThread()) throw new IllegalStateException("Server thread required");
  ProjectRegistry registry = REGISTRIES.get(server);
  if (registry == null) throw new IllegalStateException("Project registry unavailable");
  return registry;
 }
 public static Project requireProject(ServerPlayer player, java.util.UUID id) {
  return authorized(player).find(id).orElseThrow(() -> new IllegalArgumentException("Unknown project"));
 }
 public static boolean isReady(ServerPlayer player, java.util.UUID id) {
  return ProjectWorlds.resolve(player, requireProject(player,id)).isPresent();
 }
 public static List<Project> list(ServerPlayer player) { return authorized(player).list(); }
 public static java.util.concurrent.CompletableFuture<Project> activateImported(ServerPlayer player,String collection,String name) throws IOException {
  var registry=authorized(player);
  var source=MapCatalog.resolveTerrain(collection,name);
  var project=new Project(java.util.UUID.randomUUID(),name,Project.WorldType.IMPORTED,0,"worldarchitect:generic",true,0);
  var server=player.getServer();
  var location=net.minecraft.resources.ResourceLocation.parse(project.dimensionId());
  var destination=server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(location.getNamespace()).resolve(location.getPath());
  return java.util.concurrent.CompletableFuture.runAsync(()->{
   try { MapTerrain.snapshot(source,destination);
    var overview=source.resolve("overview.png");
    if(java.nio.file.Files.isRegularFile(overview,java.nio.file.LinkOption.NOFOLLOW_LINKS)&&java.nio.file.Files.size(overview)<=5242880L)java.nio.file.Files.copy(overview,destination.resolve("overview.png"),java.nio.file.LinkOption.NOFOLLOW_LINKS);
   } catch(IOException e){throw new java.util.concurrent.CompletionException(e);}
  }).thenCompose(ignored->server.submit(()->{
   if(REGISTRIES.get(server)!=registry)throw new IllegalStateException("Server registry changed");
   try { ProjectDatapack.stage(server,project);return registry.register(project); }
   catch(IOException e){throw new java.util.concurrent.CompletionException(e);}
  }));
 }
 public static Project create(ServerPlayer player, String name, Project.WorldType type, int size, String profile, boolean backups) throws IOException {return create(player,name,type,size,size,profile,backups);}
 public static Project create(ServerPlayer player,String name,Project.WorldType type,int size,int width,String profile,boolean backups) throws IOException {
  if (type == Project.WorldType.IMPORTED) throw new IllegalArgumentException("Import not implemented");
  Project project = authorized(player).create(name,type,size,width,profile,backups);
  ProjectDatapack.stage(player.getServer(), project);
  return project;
 }
}
