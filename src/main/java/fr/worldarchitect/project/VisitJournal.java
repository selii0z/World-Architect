package fr.worldarchitect.project;
import java.io.IOException;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.UUID;
import com.google.gson.Gson;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class VisitJournal {
 private static final Gson GSON=new Gson();
 public record Entry(String dimension,double x,double y,double z,float yaw,float pitch,String mode){}
 private static Path path(ServerPlayer player) throws IOException {
  Path dir=player.getServer().getWorldPath(LevelResource.ROOT).resolve("worldarchitect/visits");
  Files.createDirectories(dir);
  return dir.resolve(player.getUUID()+".json");
 }
 public static void save(ServerPlayer player,Entry entry) throws IOException {
  Path target=path(player);
  if(Files.exists(target)) throw new IOException("Existing recovery journal");
  Path tmp=Files.createTempFile(target.getParent(),"visit-",".tmp");
  try {
   Files.writeString(tmp,GSON.toJson(entry));
   try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.WRITE)){c.force(true);}
   Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);
  } finally {Files.deleteIfExists(tmp);}
 }
 public static Entry read(ServerPlayer player) throws IOException {
  Path p=path(player);
  return Files.exists(p)?GSON.fromJson(Files.readString(p),Entry.class):null;
 }
 public static void clear(ServerPlayer player) throws IOException {Files.deleteIfExists(path(player));}
}
