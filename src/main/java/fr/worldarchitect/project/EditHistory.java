package fr.worldarchitect.project;
import java.util.*;

final class EditHistory {
 record Edit(UUID player,UUID undoSnapshot,StructurePlacement.Plan plan,boolean includeAir){}
 private static final int LIMIT=50;
 private final Deque<Edit> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
 private UUID owner;
 private record State(int schema,List<Edit> undo,List<Edit> redo){}
 synchronized void save(java.nio.file.Path file) throws java.io.IOException {
  var gson=new com.google.gson.Gson();
  var bytes=gson.toJson(new State(1,List.copyOf(undo),List.copyOf(redo))).getBytes(java.nio.charset.StandardCharsets.UTF_8);
  var dir=file.toAbsolutePath().getParent();
  java.nio.file.Files.createDirectories(dir);
  var tmp=java.nio.file.Files.createTempFile(dir,"history-",".tmp");
  try{
   java.nio.file.Files.write(tmp,bytes);
   try(var channel=java.nio.channels.FileChannel.open(tmp,java.nio.file.StandardOpenOption.WRITE)){channel.force(true);}
   java.nio.file.Files.move(tmp,file,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }finally{java.nio.file.Files.deleteIfExists(tmp);}
 }
 static EditHistory load(java.nio.file.Path file) throws java.io.IOException {
  var history=new EditHistory();
  if(!java.nio.file.Files.exists(file))return history;
  if(java.nio.file.Files.size(file)>2*1024*1024)throw new java.io.IOException("History too large");
  try{
   var state=new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(file),State.class);
   if(state==null||state.schema()!=1||state.undo()==null||state.redo()==null||state.undo().size()+state.redo().size()>LIMIT)throw new java.io.IOException("Invalid history");
   for(var edit:state.undo())validate(edit);
   for(var edit:state.redo())validate(edit);
   history.undo.addAll(state.undo());history.redo.addAll(state.redo());
   return history;
  }catch(RuntimeException e){throw new java.io.IOException("Invalid history",e);}
 }
 private static void validate(Edit edit) throws java.io.IOException {
  if(edit==null||edit.player()==null||edit.undoSnapshot()==null||edit.plan()==null||edit.plan().structure()==null||edit.plan().anchor()==null||edit.plan().rotation()==null||edit.plan().mirror()==null||edit.plan().min()==null||edit.plan().max()==null)throw new java.io.IOException("Invalid history entry");
 }
 synchronized void acquire(UUID player){
  if(owner!=null&&!owner.equals(player))throw new IllegalStateException("Project already being edited");
  owner=player;
 }
 synchronized void release(UUID player){if(player.equals(owner))owner=null;}
 private void check(UUID player){if(!player.equals(owner))throw new IllegalStateException("Editor lock required");}
 synchronized void record(UUID player,Edit edit){
  check(player);
  if(!player.equals(edit.player()))throw new IllegalArgumentException("Wrong editor");
  undo.addLast(edit);redo.clear();
  while(undo.size()>LIMIT)undo.removeFirst();
 }
 synchronized Edit peekUndo(UUID player){check(player);return undo.peekLast();}
 synchronized Edit peekRedo(UUID player){check(player);return redo.peekLast();}
 synchronized void confirmUndo(UUID player,Edit expected){
  check(player);
  if(expected==null||!expected.equals(undo.peekLast()))throw new IllegalStateException("History changed");
  redo.addLast(undo.removeLast());
 }
 synchronized void confirmRedo(UUID player,Edit expected,Edit replacement){
  check(player);
  if(expected==null||!expected.equals(redo.peekLast())||!player.equals(replacement.player()))throw new IllegalStateException("History changed");
  redo.removeLast();undo.addLast(replacement);
 }
 synchronized List<Edit> entries(UUID player){check(player);return List.copyOf(undo);}
}
