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
