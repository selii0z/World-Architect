package fr.worldarchitect.project;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.resources.ResourceLocation;
public final class SurfaceColors {
 private SurfaceColors(){}
 public static int[] resolve(ChunkSurface.Surface[] surface){
  if(surface.length!=256)throw new IllegalArgumentException("Invalid surface size");
  int[] colors=new int[256];
  var lookup=BuiltInRegistries.BLOCK.asLookup();
  for(int i=0;i<256;i++){
   var entry=surface[i];if(entry==null)continue;
   var id=ResourceLocation.tryParse(entry.state().getString("Name"));
   if(id==null||!BuiltInRegistries.BLOCK.containsKey(id))continue;
   var state=NbtUtils.readBlockState(lookup,entry.state());
   var pos=new BlockPos(i%16,entry.y(),i/16);
   colors[i]=0xff000000|state.getMapColor(EmptyBlockGetter.INSTANCE,pos).col;
  }
  return colors;
 }
}
