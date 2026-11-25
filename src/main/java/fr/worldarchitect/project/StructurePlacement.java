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
 public record Plan(UUID structure,BlockPos anchor,Rotation rotation,Mirror mirror,BlockPos min,BlockPos max){}
 public static Plan preview(ServerLevel level,UUID id,BlockPos anchor,Rotation rotation,Mirror mirror) throws IOException {
  var template=StructureLibrary.load(level,id);
  var settings=new StructurePlaceSettings().setRotation(rotation).setMirror(mirror).setIgnoreEntities(true);
  var bounds=template.getBoundingBox(settings,anchor);
  var min=new BlockPos(bounds.minX(),bounds.minY(),bounds.minZ());
  var max=new BlockPos(bounds.maxX(),bounds.maxY(),bounds.maxZ());
  if(min.getY()<level.getMinBuildHeight()||max.getY()>=level.getMaxBuildHeight())throw new IllegalArgumentException("Outside build height");
  if(!level.getWorldBorder().isWithinBounds(min)||!level.getWorldBorder().isWithinBounds(max))throw new IllegalArgumentException("Outside world border");
  return new Plan(id,anchor.immutable(),rotation,mirror,min,max);
 }
}
