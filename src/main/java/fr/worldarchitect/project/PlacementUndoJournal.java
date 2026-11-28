package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

final class PlacementUndoJournal {
 private PlacementUndoJournal(){}
 // Internal recovery primitive; caller must enforce session authorization and conflict checks.
 static boolean restore(ServerLevel level,UUID player,UUID id) throws IOException {
  if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
  Path file=level.getServer().getWorldPath(LevelResource.ROOT).resolve("worldarchitect/undo").resolve(id+".nbt");
  var root=NbtIo.readCompressed(file,net.minecraft.nbt.NbtAccounter.create(64L*1024*1024));
  if(root.getInt("schema")!=1||!root.hasUUID("player")||!player.equals(root.getUUID("player"))||!level.dimension().location().toString().equals(root.getString("dimension"))||!root.contains("before",10))throw new IOException("Invalid undo snapshot");
  var data=root.getCompound("before");
  var size=data.getList("size",3);
  if(size.size()!=3)throw new IOException("Invalid undo size");
  int x=size.getInt(0),y=size.getInt(1),z=size.getInt(2);
  if(x<=0||y<=0||z<=0||x>262144||y>262144||z>262144||(long)x*y*z>262144)throw new IOException("Undo volume exceeds limit");
  var min=BlockPos.of(root.getLong("anchor"));
  var max=min.offset(x-1,y-1,z-1);
  if(min.getY()<level.getMinBuildHeight()||max.getY()>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(min)||!level.getWorldBorder().isWithinBounds(max))throw new IOException("Undo outside project bounds");
  var project=ProjectService.forLevel(level).orElseThrow(()->new IOException("Not a project dimension"));
  if(!project.bounds().contains(min.getX()+0.5,min.getZ()+0.5)||!project.bounds().contains(max.getX()+0.5,max.getZ()+0.5))throw new IOException("Undo outside rectangular project bounds");
  for(int cx=min.getX()>>4;cx<=max.getX()>>4;cx++)for(int cz=min.getZ()>>4;cz<=max.getZ()>>4;cz++)
   if(!level.hasChunk(cx,cz))throw new IOException("Unloaded undo region");
  var template=new StructureTemplate();
  template.load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),data);
  var settings=new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setIgnoreEntities(true);
  return template.placeInWorld(level,min,min,settings,level.getRandom(),3);
 }
 static UUID capture(ServerLevel level,UUID player,StructurePlacement.Plan plan) throws IOException {
  if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
  var min=plan.min();var max=plan.max();
  var size=new BlockPos(max.getX()-min.getX()+1,max.getY()-min.getY()+1,max.getZ()-min.getZ()+1);
  if(size.getX()<=0||size.getY()<=0||size.getZ()<=0||(long)size.getX()*size.getY()*size.getZ()>262144)throw new IOException("Invalid undo volume");
  for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)
   if(!level.hasChunk(x,z))throw new IOException("Unloaded undo region");
  var template=new StructureTemplate();
  template.fillFromWorld(level,min,size,false,null);
  var root=new CompoundTag();
  root.putInt("schema",1);
  root.putString("dimension",level.dimension().location().toString());
  root.putUUID("player",player);
  root.putLong("anchor",min.asLong());
  root.put("before",template.save(new CompoundTag()));
  Path dir=level.getServer().getWorldPath(LevelResource.ROOT).resolve("worldarchitect/undo");
  Files.createDirectories(dir);
  UUID id=UUID.randomUUID();
  Path tmp=Files.createTempFile(dir,"undo-",".tmp");
  try{
   NbtIo.writeCompressed(root,tmp);
   try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
   Files.move(tmp,dir.resolve(id+".nbt"),StandardCopyOption.ATOMIC_MOVE);
  }finally{Files.deleteIfExists(tmp);}
  return id;
 }
}
