package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;

public final class BlockEditArchive {
    private BlockEditArchive(){}
    public static void save(Path file, ServerLevel level, UUID owner, List<BlockEditPlan.Change> changes) throws IOException {
        if(changes.size()>262144)throw new IOException("Edit too large");
        CompoundTag root=new CompoundTag();
        root.putInt("schema",1);
        root.putString("dimension",level.dimension().location().toString());
        root.putUUID("owner",owner);
        ListTag entries=new ListTag();
        for(var change:changes){
            CompoundTag entry=new CompoundTag();
            entry.putLong("pos",change.position().asLong());
            entry.put("before",NbtUtils.writeBlockState(change.before()));
            entry.put("after",NbtUtils.writeBlockState(change.after()));
            entries.add(entry);
        }
        root.put("changes",entries);
        Path dir=file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp=Files.createTempFile(dir,"edit-",".tmp");
        try{
            NbtIo.writeCompressed(root,tmp);
            try(var channel=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE);
        }finally{Files.deleteIfExists(tmp);}
    }
    public static List<BlockEditPlan.Change> load(Path file,ServerLevel level,UUID owner,boolean reverse) throws IOException {
        CompoundTag root=NbtIo.readCompressed(file,NbtAccounter.create(256L*1024*1024));
        if(root.getInt("schema")!=1||!root.getString("dimension").equals(level.dimension().location().toString())||!root.hasUUID("owner")||!root.getUUID("owner").equals(owner))
            throw new IOException("Invalid edit archive");
        ListTag entries=root.getList("changes",10);
        if(entries.size()>262144)throw new IOException("Edit too large");
        var lookup=level.registryAccess().lookupOrThrow(Registries.BLOCK);
        var changes=new ArrayList<BlockEditPlan.Change>();
        var seen=new HashSet<BlockPos>();
        for(int i=0;i<entries.size();i++){
            var entry=entries.getCompound(i);
            if(!entry.contains("pos",4)||!entry.contains("before",10)||!entry.contains("after",10))throw new IOException("Invalid edit entry");
            var pos=BlockPos.of(entry.getLong("pos"));
            if(!seen.add(pos))throw new IOException("Duplicate edit position");
            var before=NbtUtils.readBlockState(lookup,entry.getCompound("before"));
            var after=NbtUtils.readBlockState(lookup,entry.getCompound("after"));
            if(before.hasBlockEntity()||after.hasBlockEntity()||!NbtUtils.writeBlockState(before).equals(entry.getCompound("before"))||!NbtUtils.writeBlockState(after).equals(entry.getCompound("after")))throw new IOException("Invalid archived block state");
            changes.add(new BlockEditPlan.Change(pos,reverse?after:before,reverse?before:after));
        }
        if(reverse)Collections.reverse(changes);
        return List.copyOf(changes);
    }
}
