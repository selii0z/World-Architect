package fr.worldarchitect.api;

import com.mojang.serialization.JsonOps;
import fr.worldarchitect.project.DimensionDefinitions;
import fr.worldarchitect.project.Project;
import fr.worldarchitect.project.ProjectWorlds;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import net.minecraft.Util;
import net.minecraft.core.RegistryOps;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Experimental native runtime levels. All map mutations run after server ticks. */
public final class RuntimeDimensionBackend implements DimensionBackend {
 public static final boolean ENABLED=Boolean.getBoolean("worldarchitect.experimentalDynamicDimensions");
 private final MinecraftServer server;
 private record Pending<T>(Callable<T> work,CompletableFuture<T> result){
  void run(){try{result.complete(work.call());}catch(Exception ex){result.completeExceptionally(ex);}}
 }
 private final ArrayDeque<Pending<?>> queue=new ArrayDeque<>();
 private boolean stopping;
 private record Mounted(ServerLevel level,Path prepared,Path mounted){}
 private final Map<UUID,Mounted> instances=new HashMap<>();
 RuntimeDimensionBackend(MinecraftServer server){this.server=server;}
 @Override public boolean supported(MinecraftServer candidate){return ENABLED&&candidate==server;}
 public static boolean enabled(MinecraftServer server){
  return InstanceServices.backend(server).filter(b->b instanceof RuntimeDimensionBackend).isPresent();
 }
 private synchronized <T> CompletableFuture<T> enqueue(Callable<T> task){
  if(stopping)return CompletableFuture.failedFuture(new IllegalStateException("Server stopping"));
  if(queue.size()>=64)return CompletableFuture.failedFuture(new IllegalStateException("Runtime dimension queue full"));
  var future=new CompletableFuture<T>();
  queue.addLast(new Pending<>(task,future));return future;
 }
 void drain(){
  if(!server.isSameThread())throw new IllegalStateException("Server thread required");
  Pending<?> task;
  synchronized(this){task=stopping?null:queue.pollFirst();}
  if(task!=null)task.run();
 }
 synchronized void stop(){
  stopping=true;
  while(!queue.isEmpty())queue.removeFirst().result().completeExceptionally(new IllegalStateException("Server stopping"));
  // Vanilla closes still-registered levels. Their terrain remains in dimensions/ for recovery.
  if(!instances.isEmpty())com.mojang.logging.LogUtils.getLogger().warn("World Architect: {} instances retained in dimensions/worldarchitect after shutdown",instances.size());
 }
 public static void scheduleProject(MinecraftServer server,Project project){
  InstanceServices.backend(server).filter(b->b instanceof RuntimeDimensionBackend).ifPresent(b->{
   var runtime=(RuntimeDimensionBackend)b;
   runtime.enqueue(()->{
    var key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(project.dimensionId()));
    var level=runtime.load(key,project);
    ProjectWorlds.configureBorder(level,project);
    return level;
   }).whenComplete((level,error)->{
    if(error!=null)com.mojang.logging.LogUtils.getLogger().error("Runtime project load failed; project data retained: {}",project.id(),error);
   });
  });
 }
 private static ResourceKey<Level> key(UUID id){
  return ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("worldarchitect","instance_"+id.toString().replace("-","")));
 }
 private Path root(){return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();}
 private static void noLinks(Path root,Path target)throws IOException{
  if(!target.startsWith(root))throw new IOException("Unexpected dimension path");
  Path current=root;
  if(Files.isSymbolicLink(current))throw new IOException("Symbolic link world root");
  for(var part:root.relativize(target)){
   current=current.resolve(part);
   if(Files.isSymbolicLink(current))throw new IOException("Symbolic link dimension path");
  }
 }
 @Override public CompletableFuture<ServerLevel> open(MinecraftServer candidate,UUID id,Path preparedTerrain){
  if(!supported(candidate))return CompletableFuture.failedFuture(new IllegalStateException("Unsupported runtime backend"));
  return enqueue(()->{
   var key=key(id);
   var root=root();
   var prepared=preparedTerrain.toAbsolutePath().normalize();
   if(!prepared.equals(root.resolve("worldarchitect/instances").resolve(id.toString())))throw new IOException("Unexpected prepared terrain");
   var mounted=root.resolve("dimensions/worldarchitect").resolve(key.location().getPath());
   noLinks(root,prepared);noLinks(root,mounted);
   if(instances.containsKey(id)||server.getLevel(key)!=null||Files.exists(mounted,LinkOption.NOFOLLOW_LINKS))throw new IOException("Instance destination already exists");
   if(!Files.isDirectory(prepared.resolve("region"),LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing instance regions");
   Files.createDirectories(mounted.getParent());
   Files.move(prepared,mounted,StandardCopyOption.ATOMIC_MOVE);
   try{
    var project=new Project(id,"Instance "+id,Project.WorldType.IMPORTED,0,"worldarchitect:generic",false);
    var level=load(key,project);
    instances.put(id,new Mounted(level,prepared,mounted));return level;
   }catch(Exception e){
    // Do not move or delete data after a partially successful level construction.
    com.mojang.logging.LogUtils.getLogger().error("Instance opening failed; terrain retained at {}",mounted,e);
    throw e;
   }
  });
 }
 @Override public CompletableFuture<Void> close(MinecraftServer candidate,UUID id){
  if(!supported(candidate))return CompletableFuture.failedFuture(new IllegalStateException("Unsupported runtime backend"));
  return enqueue(()->{
   var entry=instances.get(id);
   if(entry==null||server.getLevel(key(id))!=entry.level())throw new IllegalStateException("Instance not mounted");
   if(!entry.level().players().isEmpty())throw new BusyException("Players must leave before closing the instance");
   noLinks(root(),entry.prepared());noLinks(root(),entry.mounted());
   if(Files.exists(entry.prepared(),LinkOption.NOFOLLOW_LINKS))throw new BusyException("Prepared path already exists; no files changed");
   entry.level().save(null,true,false);
   NeoForge.EVENT_BUS.post(new LevelEvent.Unload(entry.level()));
   if(!entry.level().players().isEmpty())throw new BusyException("Players entered during unload");
   server.forgeGetWorldMap().remove(key(id),entry.level());
   server.markWorldsDirty();
   entry.level().close();
   Files.move(entry.mounted(),entry.prepared(),StandardCopyOption.ATOMIC_MOVE);
   instances.remove(id);return null;
  });
 }
 private ServerLevel load(ResourceKey<Level> key,Project project)throws IOException{
  if(!server.isSameThread())throw new IllegalStateException("Server thread required");
  var existing=server.getLevel(key);
  if(existing!=null)return existing;
  if(server.overworld()==null)throw new IllegalStateException("Overworld not ready");
  var root=root();
  noLinks(root,root.resolve("dimensions").resolve(key.location().getNamespace()).resolve(key.location().getPath()));
  var stem=LevelStem.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),DimensionDefinitions.definition(project)).getOrThrow();
  var data=server.getWorldData();
  var level=new ServerLevel(server,Util.backgroundExecutor(),server.storageSource,new DerivedLevelData(data,data.overworldData()),key,stem,
    new ChunkProgressListener(){
     public void updateSpawnPos(ChunkPos pos){}
     public void onStatusChange(ChunkPos pos,ChunkStatus status){}
     public void start(){}
     public void stop(){}
    },false,BiomeManager.obfuscateSeed(server.overworld().getSeed()),List.of(),false,null);
  server.forgeGetWorldMap().put(key,level);
  server.markWorldsDirty();
  NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
  return level;
 }
}
