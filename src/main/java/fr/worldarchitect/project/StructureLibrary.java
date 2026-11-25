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

public final class StructureLibrary {
 private StructureLibrary(){}
 public static UUID capture(ServerLevel level, StructureSelection.Selection selection,String name,String category) throws IOException {
  if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
  if(!selection.dimension().equals(level.dimension()))throw new IllegalArgumentException("Wrong dimension");
  if(name==null||name.isBlank()||name.length()>80||category==null||category.isBlank()||category.length()>60)throw new IllegalArgumentException("Invalid metadata");
  selection.validate(262144);
  BlockPos min=selection.min(),max=selection.max();
  if(min.getY()<level.getMinBuildHeight()||max.getY()>=level.getMaxBuildHeight())throw new IllegalArgumentException("Outside build height");
  if(!level.getWorldBorder().isWithinBounds(min)||!level.getWorldBorder().isWithinBounds(max))throw new IllegalArgumentException("Outside world border");
  for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)
   if(!level.hasChunk(x,z))throw new IllegalStateException("Selection contains unloaded chunks");
  BlockPos size=new BlockPos(max.getX()-min.getX()+1,max.getY()-min.getY()+1,max.getZ()-min.getZ()+1);
  StructureTemplate template=new StructureTemplate();
  template.fillFromWorld(level,min,size,false,null);
  CompoundTag data=template.save(new CompoundTag());
  // Strip all block entity payloads by default, including modded inventories.
  var blocks=data.getList("blocks",10);
  for(int i=0;i<blocks.size();i++)blocks.getCompound(i).remove("nbt");
  CompoundTag root=new CompoundTag();
  root.putInt("schema",1);
  root.putString("name",name.strip());
  root.putString("category",category.strip());
  root.put("structure",data);
  Path dir=level.getServer().getWorldPath(LevelResource.ROOT).resolve("worldarchitect/structures");
  Files.createDirectories(dir);
  UUID id=UUID.randomUUID();
  Path tmp=Files.createTempFile(dir,"capture-",".tmp");
  try{
   NbtIo.writeCompressed(root,tmp);
   try(FileChannel channel=FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
   Files.move(tmp,dir.resolve(id+".nbt"),StandardCopyOption.ATOMIC_MOVE);
  }finally{Files.deleteIfExists(tmp);}
  return id;
 }
}
