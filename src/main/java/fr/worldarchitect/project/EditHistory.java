package fr.worldarchitect.project;
import java.util.*;

final class EditHistory {
 record Edit(UUID player,UUID undoSnapshot,StructurePlacement.Plan plan,boolean includeAir){}
 private static final int LIMIT=50;
 private final Deque<Edit> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
 private UUID owner;
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
