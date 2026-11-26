package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class ProjectMarkers {
 private static final Gson GSON=new Gson();
 private ProjectMarkers(){}
 public enum Category { GENERAL, BUILDING, ACCESS, LANDMARK, ZONE }
 public enum Color { WHITE, GREEN, BLUE, YELLOW, RED, PURPLE }
 public record Marker(UUID id,String name,int x,int y,int z,Category category,Color color){
  public Marker { if(category==null)category=Category.GENERAL;if(color==null)color=Color.WHITE; }
 }
 private record Data(List<Marker> markers){}
 private static Path path(ServerPlayer player,Project project){
  return player.getServer().getWorldPath(LevelResource.ROOT).resolve("worldarchitect/markers").resolve(project.id()+".json");
 }
 public static List<Marker> list(ServerPlayer player)throws IOException{
  var project=ProjectService.forLevel(player.serverLevel()).orElseThrow(()->new IOException("Not a project dimension"));
  var file=path(player,project);
  if(!Files.exists(file))return List.of();
  if(Files.size(file)>1048576)throw new IOException("Marker file too large");
  try{
   var data=GSON.fromJson(Files.readString(file),Data.class);
   if(data==null||data.markers()==null||data.markers().size()>512)throw new IOException("Invalid markers");
   return List.copyOf(data.markers());
  }catch(RuntimeException ex){throw new IOException("Invalid marker data",ex);}
 }
 public static Marker add(ServerPlayer player,String name)throws IOException{return add(player,name,Category.GENERAL,Color.WHITE);}
 public static Marker add(ServerPlayer player,String name,Category category,Color color)throws IOException{
  return addAt(player,name,category,color,player.blockPosition());
 }
 public static Marker addSurface(ServerPlayer player,String name,Category category,Color color,int x,int z)throws IOException{
  var project=ConstructionSessions.requireEditor(player);
  var level=player.serverLevel();
  if(!project.bounds().contains(x+0.5,z+0.5)||Math.abs((long)x)>29999984||Math.abs((long)z)>29999984)throw new IOException("Outside project");
  var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
  if(chunk==null)throw new IOException("Terrain not loaded");
  int y=chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x&15,z&15)+1;
  if(y<=level.getMinBuildHeight()||y>=level.getMaxBuildHeight()-1)throw new IOException("Invalid surface");
  return addAt(player,name,category,color,new net.minecraft.core.BlockPos(x,y,z));
 }
 private static Marker addAt(ServerPlayer player,String name,Category category,Color color,net.minecraft.core.BlockPos p)throws IOException{
  var project=ConstructionSessions.requireEditor(player);
  name=name.strip();
  if(name.isEmpty()||name.length()>64||name.chars().anyMatch(Character::isISOControl))throw new IOException("Invalid marker name");
  if(!project.bounds().contains(p.getX()+0.5,p.getZ()+0.5))throw new IOException("Outside project");
  var markers=new ArrayList<>(list(player));
  if(markers.size()>=512)throw new IOException("Marker limit reached");
  var marker=new Marker(UUID.randomUUID(),name,p.getX(),p.getY(),p.getZ(),category,color);
  markers.add(marker);save(path(player,project),markers);return marker;
 }
 public static void teleport(ServerPlayer player,UUID id)throws IOException{
  var project=ConstructionSessions.requireEditor(player);
  BlockEditScheduler.requireIdle(player,project);
  var marker=list(player).stream().filter(m->id.equals(m.id())).findFirst().orElseThrow(()->new IOException("Marker not found"));
  var level=player.serverLevel();
  var pos=new net.minecraft.core.BlockPos(marker.x(),marker.y(),marker.z());
  if(!project.bounds().contains(marker.x()+0.5,marker.z()+0.5)||!level.getWorldBorder().isWithinBounds(pos)||marker.y()<=level.getMinBuildHeight()||marker.y()>=level.getMaxBuildHeight()-1||!level.hasChunkAt(pos))throw new IOException("Marker outside loaded safe terrain");
  if(!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()||!level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()||!level.getFluidState(pos).isEmpty()||!level.getFluidState(pos.above()).isEmpty())throw new IOException("Marker obstructed");
  player.teleportTo(level,marker.x()+0.5,marker.y(),marker.z()+0.5,player.getYRot(),player.getXRot());
 }
 public static boolean remove(ServerPlayer player,UUID id)throws IOException{
  var project=ConstructionSessions.requireEditor(player);
  var markers=new ArrayList<>(list(player));
  if(!markers.removeIf(m->id.equals(m.id())))return false;
  save(path(player,project),markers);return true;
 }
 private static void save(Path file,List<Marker> markers)throws IOException{
  Files.createDirectories(file.getParent());
  var tmp=Files.createTempFile(file.getParent(),"markers-",".tmp");
  try{
   Files.writeString(tmp,GSON.toJson(new Data(markers)));
   try(var channel=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
   Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }finally{Files.deleteIfExists(tmp);}
 }
}
