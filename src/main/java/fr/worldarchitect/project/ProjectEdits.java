package fr.worldarchitect.project;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

public final class ProjectEdits {
 private ProjectEdits(){}
 private static java.nio.file.Path historyPath(ServerPlayer player,Project project){
  return player.getServer().getWorldPath(LevelResource.ROOT).resolve("worldarchitect/history").resolve(project.id()+".json");
 }
 public static boolean undo(ServerPlayer player) throws IOException {
  var project=ConstructionSessions.requireEditor(player);
  var path=historyPath(player,project);
  var history=EditHistory.load(path);
  var owner=player.getUUID();
  history.acquire(owner);
  try{
   var edit=history.peekUndo(owner);
   if(edit==null)return false;
   if(!owner.equals(edit.player()))throw new IOException("Latest edit belongs to another editor");
   var level=player.serverLevel();
   var recovery=PlacementUndoJournal.capture(level,owner,edit.plan());
   try{
    if(!PlacementUndoJournal.restore(level,owner,edit.undoSnapshot()))throw new IOException("Undo failed");
    history.confirmUndo(owner,edit);
    history.save(path);
   }catch(Exception failure){
    try{if(!PlacementUndoJournal.restore(level,owner,recovery))throw new IOException("Rollback failed");}
    catch(Exception rollback){failure.addSuppressed(rollback);}
    throw new IOException("Undo failed; recovery snapshot "+recovery,failure);
   }
   return true;
  }finally{history.release(owner);}
 }
 public static boolean redo(ServerPlayer player) throws IOException {
  var project=ConstructionSessions.requireEditor(player);
  var path=historyPath(player,project);
  var history=EditHistory.load(path);
  var owner=player.getUUID();
  history.acquire(owner);
  try{
   var edit=history.peekRedo(owner);
   if(edit==null)return false;
   if(!owner.equals(edit.player()))throw new IOException("Latest edit belongs to another editor");
   var level=player.serverLevel();
   var result=StructurePlacement.placeWithUndo(level,owner,edit.plan(),edit.includeAir());
   if(!result.success())throw new IOException("Redo failed");
   try{
    history.confirmRedo(owner,edit,new EditHistory.Edit(owner,result.undoId(),edit.plan(),edit.includeAir()));
    history.save(path);
   }catch(Exception failure){
    try{if(!PlacementUndoJournal.restore(level,owner,result.undoId()))throw new IOException("Rollback failed");}
    catch(Exception rollback){failure.addSuppressed(rollback);}
    throw new IOException("Redo failed; recovery snapshot "+result.undoId(),failure);
   }
   return true;
  }finally{history.release(owner);}
 }
 public static UUID place(ServerPlayer player,StructurePlacement.Plan requested,boolean includeAir) throws IOException {
  var project=ConstructionSessions.requireEditor(player);
  var path=historyPath(player,project);
  var history=EditHistory.load(path);
  var owner=player.getUUID();
  history.acquire(owner);
  try{
   var plan=StructurePlacement.preview(player.serverLevel(),requested.structure(),requested.anchor(),requested.rotation(),requested.mirror());
   var result=StructurePlacement.placeWithUndo(player.serverLevel(),owner,plan,includeAir);
   if(!result.success())throw new IOException("Placement failed");
   try{
    history.record(owner,new EditHistory.Edit(owner,result.undoId(),plan,includeAir));
    history.save(path);
   }catch(Exception failure){
    try{
     if(!PlacementUndoJournal.restore(player.serverLevel(),owner,result.undoId()))throw new IOException("Rollback unsuccessful");
    }catch(Exception rollback){failure.addSuppressed(rollback);}
    throw new IOException("History save failed; recovery snapshot "+result.undoId(),failure);
   }
   return result.undoId();
  }finally{history.release(owner);}
 }
}
