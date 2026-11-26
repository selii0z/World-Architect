package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.*;

/** Imports single-region Litematica files as vanilla structure data. */
public final class LitematicReader {
    private LitematicReader() {}
    public static CompoundTag read(Path source) throws IOException {
        CompoundTag root = NbtIo.readCompressed(source, NbtAccounter.create(64L * 1024 * 1024));
        CompoundTag regions = root.getCompound("Regions");
        if (regions.getAllKeys().size() != 1) throw new IOException("Exactly one Litematica region is currently supported");
        CompoundTag region = regions.getCompound(regions.getAllKeys().iterator().next());
        CompoundTag size = region.getCompound("Size");
        long x = Math.abs((long)size.getInt("x")), y = Math.abs((long)size.getInt("y")), z = Math.abs((long)size.getInt("z"));
        if (x == 0 || y == 0 || z == 0 || x > 262144 || y > 262144 || z > 262144 || x*y*z > 262144)
            throw new IOException("Litematica region exceeds prefab limits");
        int w=(int)x,h=(int)y,d=(int)z,volume=w*h*d;
        ListTag palette = region.getList("BlockStatePalette",10);
        if (palette.isEmpty() || palette.size()>65536) throw new IOException("Invalid Litematica palette");
        int bits=Math.max(2,32-Integer.numberOfLeadingZeros(palette.size()-1));
        long[] packed=region.getLongArray("BlockStates");
        if (packed.length != ((long)volume*bits+63)/64) throw new IOException("Invalid packed block data length");
        long mask=(1L<<bits)-1;
        ListTag blocks=new ListTag();
        for(int i=0;i<volume;i++){
            long offset=(long)i*bits;
            int word=(int)(offset>>>6),shift=(int)(offset&63);
            long value=packed[word]>>>shift;
            if(shift+bits>64)value|=packed[word+1]<<(64-shift);
            int index=(int)(value&mask);
            if(index>=palette.size())throw new IOException("Invalid Litematica block index");
            CompoundTag block=new CompoundTag();
            block.put("pos",ints(i%w,i/(w*d),(i/w)%d));
            block.putInt("state",index);
            blocks.add(block);
        }
        CompoundTag result=new CompoundTag();
        result.put("size",ints(w,h,d));
        result.put("palette",palette.copy());
        result.put("blocks",blocks);
        result.put("entities",new ListTag());
        result.putInt("DataVersion",root.getInt("MinecraftDataVersion"));
        return result;
    }
    private static ListTag ints(int... values){
        ListTag result=new ListTag();
        for(int value:values)result.add(IntTag.valueOf(value));
        return result;
    }
}
