package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class InventoryJournal {
 private InventoryJournal(){}
 private static Path path(ServerPlayer player) throws IOException {
  Path dir=player.getServer().getWorldPath(LevelResource.ROOT).resolve("worldarchitect/inventories");
  Files.createDirectories(dir);
  return dir.resolve(player.getUUID()+".nbt");
 }
 public static void capture(ServerPlayer player) throws IOException {
  if(!player.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
  Path target=path(player);
  if(Files.exists(target)) throw new IOException("Unresolved inventory snapshot");
  CompoundTag root=new CompoundTag();
  root.putInt("schema",2);
  root.putUUID("owner",player.getUUID());
  root.putInt("experienceLevel",player.experienceLevel);
  root.putInt("totalExperience",player.totalExperience);
  root.putFloat("experienceProgress",player.experienceProgress);
  root.put("inventory",player.getInventory().save(new ListTag()));
  root.putInt("selected",player.getInventory().selected);
  Path tmp=Files.createTempFile(target.getParent(),"inventory-",".tmp");
  try {
   NbtIo.writeCompressed(root,tmp);
   try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.WRITE)){c.force(true);}
   Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);
  } finally {Files.deleteIfExists(tmp);}
 }
 public static boolean exists(ServerPlayer player) throws IOException {return Files.exists(path(player));}
 public static void restore(ServerPlayer player) throws IOException {
  if(!player.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
  Path target=path(player);
  CompoundTag root=NbtIo.readCompressed(target,NbtAccounter.create(32L*1024*1024));
  int schema=root.getInt("schema");
  if((schema!=1&&schema!=2)||!root.contains("inventory",9)||(schema==2&&(!root.hasUUID("owner")||!root.getUUID("owner").equals(player.getUUID())))) throw new IOException("Invalid inventory snapshot");
  int selected=root.getInt("selected");
  if(selected<0||selected>8) throw new IOException("Invalid selected slot");
  if(schema==2&&(!root.contains("experienceLevel",3)||!root.contains("totalExperience",3)||!root.contains("experienceProgress",5)||root.getInt("experienceLevel")<0||root.getInt("totalExperience")<0||!Float.isFinite(root.getFloat("experienceProgress"))||root.getFloat("experienceProgress")<0||root.getFloat("experienceProgress")>1))throw new IOException("Invalid experience snapshot");
  player.getInventory().load(root.getList("inventory",10));
  if(schema==2){
   player.experienceLevel=root.getInt("experienceLevel");
   player.totalExperience=root.getInt("totalExperience");
   player.experienceProgress=root.getFloat("experienceProgress");
  }
  player.getInventory().selected=selected;
  player.inventoryMenu.broadcastChanges();
 }
 public static void clearAfterConfirmedSave(ServerPlayer player) throws IOException {Files.deleteIfExists(path(player));}
}
