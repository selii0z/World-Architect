package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.google.gson.Gson;

final class BlockEditHistory {
    private static final Gson GSON=new Gson();
    private static final int LIMIT=50;
    record Entry(UUID archive,UUID owner){}
    record State(int schema,List<Entry> undo,List<Entry> redo){}
    private final Path file;
    private final ArrayList<Entry> undo,redo;
    private BlockEditHistory(Path file,List<Entry> undo,List<Entry> redo){
        this.file=file;this.undo=new ArrayList<>(undo);this.redo=new ArrayList<>(redo);
    }
    static BlockEditHistory load(Path file)throws IOException{
        if(!Files.exists(file))return new BlockEditHistory(file,List.of(),List.of());
        if(Files.size(file)>1048576)throw new IOException("History too large");
        try{
            State s=GSON.fromJson(Files.readString(file),State.class);
            if(s==null||s.schema()!=1||s.undo()==null||s.redo()==null||s.undo().size()+s.redo().size()>LIMIT)throw new IOException("Invalid history");
            Set<UUID> ids=new HashSet<>();
            for(var list:List.of(s.undo(),s.redo()))for(var e:list)
                if(e==null||e.archive()==null||e.owner()==null||!ids.add(e.archive()))throw new IOException("Invalid history entry");
            return new BlockEditHistory(file,s.undo(),s.redo());
        }catch(RuntimeException ex){throw new IOException("Invalid history",ex);}
    }
    Entry peek(boolean reverse,UUID owner){
        var list=reverse?undo:redo;
        if(list.isEmpty())return null;
        var entry=list.getLast();
        if(!entry.owner().equals(owner))throw new IllegalStateException("Latest edit belongs to another editor");
        return entry;
    }
    List<Entry> record(Entry entry)throws IOException{
        var removed=new ArrayList<>(redo);
        redo.clear();undo.add(entry);
        while(undo.size()>LIMIT)removed.add(undo.removeFirst());
        save();return List.copyOf(removed);
    }
    void confirm(boolean reverse,Entry expected)throws IOException{
        var from=reverse?undo:redo;var to=reverse?redo:undo;
        if(from.isEmpty()||!from.getLast().equals(expected))throw new IOException("History changed");
        to.add(from.removeLast());save();
    }
    private void save()throws IOException{
        Path dir=file.toAbsolutePath().getParent();Files.createDirectories(dir);
        Path tmp=Files.createTempFile(dir,"block-history-",".tmp");
        try{
            Files.writeString(tmp,GSON.toJson(new State(1,List.copyOf(undo),List.copyOf(redo))));
            try(var channel=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(tmp);}
    }
}
