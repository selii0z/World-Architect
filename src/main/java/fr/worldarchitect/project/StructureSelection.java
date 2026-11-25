package fr.worldarchitect.project;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public final class StructureSelection {
 public record Selection(ResourceKey<Level> dimension, BlockPos first, BlockPos second) {
  public boolean complete(){return first!=null && second!=null;}
  public BlockPos min(){requireComplete();return new BlockPos(Math.min(first.getX(),second.getX()),Math.min(first.getY(),second.getY()),Math.min(first.getZ(),second.getZ()));}
  public BlockPos max(){requireComplete();return new BlockPos(Math.max(first.getX(),second.getX()),Math.max(first.getY(),second.getY()),Math.max(first.getZ(),second.getZ()));}
  private void requireComplete(){if(!complete())throw new IllegalStateException("Selection incomplete");}
  public long volume(){var a=min();var b=max();return Math.multiplyExact(Math.multiplyExact((long)b.getX()-a.getX()+1,(long)b.getY()-a.getY()+1),(long)b.getZ()-a.getZ()+1);}
  public void validate(long limit){if(volume()>limit)throw new IllegalArgumentException("Selection exceeds volume limit");}
 }
 private final Map<UUID,Selection> selections=new HashMap<>();
 public Selection set(UUID player,ResourceKey<Level> dimension,BlockPos position,boolean first){
  var old=selections.get(player);
  if(old==null||!old.dimension().equals(dimension))old=new Selection(dimension,null,null);
  var next=new Selection(dimension,first?position.immutable():old.first(),first?old.second():position.immutable());
  selections.put(player,next);return next;
 }
 public Selection get(UUID player){return selections.get(player);}
 public void clear(UUID player){selections.remove(player);}
}
