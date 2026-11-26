package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.*;

public final class NativeStructureReader {
    private NativeStructureReader() {}
    public static CompoundTag read(Path source) throws IOException {
        CompoundTag root=NbtIo.readCompressed(source,NbtAccounter.create(64L*1024*1024));
        ListTag size=root.getList("size",3);
        if(size.size()!=3)throw new IOException("Invalid structure dimensions");
        int w=size.getInt(0),h=size.getInt(1),d=size.getInt(2);
        if(w<=0||h<=0||d<=0||w>262144||h>262144||d>262144||(long)w*h*d>262144)
            throw new IOException("Structure exceeds prefab limits");
        if(root.contains("palettes"))throw new IOException("Multiple palettes not supported");
        ListTag palette=root.getList("palette",10),blocks=root.getList("blocks",10);
        if(palette.isEmpty()||palette.size()>65536||blocks.size()>262144)throw new IOException("Invalid structure palette or blocks");
        java.util.BitSet seen=new java.util.BitSet(w*h*d);
        for(int i=0;i<blocks.size();i++){
            CompoundTag block=blocks.getCompound(i);
            ListTag pos=block.getList("pos",3);
            int state=block.getInt("state");
            if(pos.size()!=3||!block.contains("state",3)||state<0||state>=palette.size())throw new IOException("Invalid block entry");
            int x=pos.getInt(0),y=pos.getInt(1),z=pos.getInt(2);
            if(x<0||x>=w||y<0||y>=h||z<0||z>=d)throw new IOException("Block outside structure");
            int index=x+w*(z+d*y);
            if(seen.get(index))throw new IOException("Duplicate block position");
            seen.set(index);
            block.remove("nbt");
        }
        root.put("entities",new ListTag());
        return root;
    }
}
