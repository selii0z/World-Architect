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
 public record Metadata(UUID id,String name,String category,int width,int height,int depth){}
 private static Path directory(ServerLevel level){return level.getServer().getWorldPath(LevelResource.ROOT).resolve("worldarchitect/structures");}
 private static CompoundTag read(ServerLevel level,UUID id) throws IOException {
  if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
  var root=NbtIo.readCompressed(directory(level).resolve(id+".nbt"),net.minecraft.nbt.NbtAccounter.create(64L*1024*1024));
  if(root.getInt("schema")!=1||!root.contains("structure",10))throw new IOException("Invalid structure");
  return root;
 }
 public static java.util.List<Metadata> list(ServerLevel level) throws IOException {
  if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
  var result=new java.util.ArrayList<Metadata>();
  if(!Files.isDirectory(directory(level)))return result;
  try(var files=Files.list(directory(level))){
   for(var file:files.filter(p->p.getFileName().toString().endsWith(".nbt")).toList()){
    try{
     String filename=file.getFileName().toString();
     UUID id=UUID.fromString(filename.substring(0,filename.length()-4));
     var root=read(level,id);
     var size=root.getCompound("structure").getList("size",3);
     if(size.size()!=3)throw new IOException("Invalid dimensions");
     result.add(new Metadata(id,root.getString("name"),root.getString("category"),size.getInt(0),size.getInt(1),size.getInt(2)));
    }catch(IOException|RuntimeException ex){com.mojang.logging.LogUtils.getLogger().warn("Cannot read structure {}",file,ex);}
   }
  }
  result.sort(java.util.Comparator.comparing(Metadata::category).thenComparing(Metadata::name).thenComparing(Metadata::id));
  return java.util.List.copyOf(result);
 }
 public record PreviewBlock(BlockPos offset,int stateId){}
 public static java.util.List<PreviewBlock> previewBlocks(ServerLevel level,UUID id) throws IOException {
  var data=read(level,id).getCompound("structure");
  var palette=data.getList("palette",10);
  var blocks=data.getList("blocks",10);
  if(blocks.size()>262144||palette.isEmpty()||palette.size()>65536)throw new IOException("Invalid preview data");
  var lookup=level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK);
  var states=new java.util.ArrayList<net.minecraft.world.level.block.state.BlockState>();
  for(int i=0;i<palette.size();i++)states.add(net.minecraft.nbt.NbtUtils.readBlockState(lookup,palette.getCompound(i)));
  var result=new java.util.ArrayList<PreviewBlock>();
  for(int i=0;i<blocks.size();i++){
   var entry=blocks.getCompound(i);var pos=entry.getList("pos",3);int index=entry.getInt("state");
   if(pos.size()!=3||index<0||index>=states.size())throw new IOException("Invalid preview block");
   var state=states.get(index);
   if(state.isAir()||state.is(net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK))continue;
   result.add(new PreviewBlock(new BlockPos(pos.getInt(0),pos.getInt(1),pos.getInt(2)),net.minecraft.world.level.block.Block.getId(state)));
  }
  return java.util.List.copyOf(result);
 }
 public static StructureTemplate load(ServerLevel level,UUID id) throws IOException {
  var data=read(level,id).getCompound("structure");
  var size=data.getList("size",3);
  if(size.size()!=3)throw new IOException("Invalid dimensions");
  int x=size.getInt(0),y=size.getInt(1),z=size.getInt(2);
  if(x<=0||y<=0||z<=0||x>262144||y>262144||z>262144||(long)x*y*z>262144)throw new IOException("Structure exceeds limits");
  var template=new StructureTemplate();
  template.load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),data);
  return template;
 }
 public static UUID capture(ServerLevel level, StructureSelection.Selection selection,String name,String category) throws IOException {
  if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
  if(!selection.dimension().equals(level.dimension()))throw new IllegalArgumentException("Wrong dimension");
  if(name==null||name.isBlank()||name.length()>80||category==null||category.isBlank()||category.length()>60)throw new IllegalArgumentException("Invalid metadata");
  selection.validate(262144);
  BlockPos min=selection.min(),max=selection.max();
  if(min.getY()<level.getMinBuildHeight()||max.getY()>=level.getMaxBuildHeight())throw new IllegalArgumentException("Outside build height");
  if(!level.getWorldBorder().isWithinBounds(min)||!level.getWorldBorder().isWithinBounds(max))throw new IllegalArgumentException("Outside world border");
  var project=ProjectService.forLevel(level).orElseThrow(()->new IllegalStateException("Not a project dimension"));
  if(!project.bounds().contains(min.getX()+0.5,min.getZ()+0.5)||!project.bounds().contains(max.getX()+0.5,max.getZ()+0.5))throw new IllegalArgumentException("Selection outside project bounds");
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
