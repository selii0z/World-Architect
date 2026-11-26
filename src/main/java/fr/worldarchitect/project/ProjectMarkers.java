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
 public record Marker(UUID id,String name,int x,int y,int z){}
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
 public static Marker add(ServerPlayer player,String name)throws IOException{
  var project=ConstructionSessions.requireEditor(player);
  name=name.strip();
  if(name.isEmpty()||name.length()>64||name.chars().anyMatch(Character::isISOControl))throw new IOException("Invalid marker name");
  var p=player.blockPosition();
  if(!project.bounds().contains(p.getX()+0.5,p.getZ()+0.5))throw new IOException("Outside project");
  var markers=new ArrayList<>(list(player));
  if(markers.size()>=512)throw new IOException("Marker limit reached");
  var marker=new Marker(UUID.randomUUID(),name,p.getX(),p.getY(),p.getZ());
  markers.add(marker);save(path(player,project),markers);return marker;
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
