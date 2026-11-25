package fr.worldarchitect.project;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public final class StructurePlacement {
 private StructurePlacement(){}
 // Do not expose to players until undo and construction authorization are integrated.
 record PlacementResult(UUID undoId,boolean success){}
 static PlacementResult placeWithUndo(ServerLevel level,UUID player,Plan requested,boolean includeAir) throws IOException {
  var plan=preview(level,requested.structure(),requested.anchor(),requested.rotation(),requested.mirror());
  UUID undoId=PlacementUndoJournal.capture(level,player,plan);
  try {
   boolean success=placeInternal(level,plan,includeAir);
   if(!success && !PlacementUndoJournal.restore(level,player,undoId))throw new IOException("Placement and rollback failed; snapshot "+undoId);
   return new PlacementResult(undoId,success);
  } catch(Exception failure){
   try {
    if(!PlacementUndoJournal.restore(level,player,undoId))throw new IOException("Rollback unsuccessful");
   }catch(Exception rollback){failure.addSuppressed(rollback);}
   throw new IOException("Placement failed; recovery snapshot "+undoId,failure);
  }
 }
 private static boolean placeInternal(ServerLevel level,Plan requested,boolean includeAir) throws IOException {
  if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
  var plan=preview(level,requested.structure(),requested.anchor(),requested.rotation(),requested.mirror());
  for(int x=plan.min().getX()>>4;x<=plan.max().getX()>>4;x++)
   for(int z=plan.min().getZ()>>4;z<=plan.max().getZ()>>4;z++)
    if(!level.hasChunk(x,z))throw new IllegalStateException("Destination contains unloaded chunks");
  var template=StructureLibrary.load(level,plan.structure());
  var settings=new StructurePlaceSettings().setRotation(plan.rotation()).setMirror(plan.mirror()).setIgnoreEntities(true);
  settings.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.STRUCTURE_BLOCK);
  if(!includeAir)settings.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.AIR);
  return template.placeInWorld(level,plan.anchor(),plan.anchor(),settings,level.getRandom(),3);
 }
 public record Plan(UUID structure,BlockPos anchor,Rotation rotation,Mirror mirror,BlockPos min,BlockPos max){}
 public static Plan preview(ServerLevel level,UUID id,BlockPos anchor,Rotation rotation,Mirror mirror) throws IOException {
  var template=StructureLibrary.load(level,id);
  var settings=new StructurePlaceSettings().setRotation(rotation).setMirror(mirror).setIgnoreEntities(true);
  var bounds=template.getBoundingBox(settings,anchor);
  var min=new BlockPos(bounds.minX(),bounds.minY(),bounds.minZ());
  var max=new BlockPos(bounds.maxX(),bounds.maxY(),bounds.maxZ());
  if(min.getY()<level.getMinBuildHeight()||max.getY()>=level.getMaxBuildHeight())throw new IllegalArgumentException("Outside build height");
  if(!level.getWorldBorder().isWithinBounds(min)||!level.getWorldBorder().isWithinBounds(max))throw new IllegalArgumentException("Outside world border");
  var project=ProjectService.forLevel(level).orElse(null);
  if(project!=null){
   var rectangle=project.bounds();
   if(!rectangle.contains(min.getX()+0.5,min.getZ()+0.5)||!rectangle.contains(max.getX()+0.5,max.getZ()+0.5))
    throw new IllegalArgumentException("Structure extends outside project bounds");
  }
  return new Plan(id,anchor.immutable(),rotation,mirror,min,max);
 }
}
