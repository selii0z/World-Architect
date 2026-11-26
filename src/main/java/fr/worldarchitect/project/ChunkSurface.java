package fr.worldarchitect.project;
import java.io.IOException;
import net.minecraft.nbt.*;
public final class ChunkSurface {
 private ChunkSurface(){}
 public record Surface(int y,CompoundTag state){}
 /** Modern 1.21 chunk sections, padded palette storage. */
 public static Surface[] read(CompoundTag chunk)throws IOException{
  var result=new Surface[256];
  var sections=chunk.getList("sections",10);
  if(sections.size()>256)throw new IOException("Too many sections");
  for(int s=0;s<sections.size();s++){
   var section=sections.getCompound(s);
   int base=section.getByte("Y")*16;
   var states=section.getCompound("block_states");
   var palette=states.getList("palette",10);
   int count=palette.size();
   if(count==0)continue;
   if(count>4096)throw new IOException("Invalid palette");
   int bits=Math.max(4,32-Integer.numberOfLeadingZeros(count-1)),per=64/bits;
   long[] data=states.getLongArray("data");
   if(count>1&&data.length!=(4096+per-1)/per)throw new IOException("Invalid block storage");
   for(int z=0;z<16;z++)for(int x=0;x<16;x++){
    int column=z*16+x;
    for(int y=15;y>=0;y--){
     if(result[column]!=null&&result[column].y()>=base+y)break;
     int index=y*256+z*16+x;
     int p=count==1?0:(int)((data[index/per]>>>(index%per*bits))&((1L<<bits)-1));
     if(p>=count)throw new IOException("Invalid palette index");
     var state=palette.getCompound(p);String name=state.getString("Name");
     if(name.isEmpty())throw new IOException("Missing block name");
     if(name.equals("minecraft:air")||name.equals("minecraft:cave_air")||name.equals("minecraft:void_air"))continue;
     result[column]=new Surface(base+y,state.copy());break;
    }
   }
  }
  return result;
 }
}
