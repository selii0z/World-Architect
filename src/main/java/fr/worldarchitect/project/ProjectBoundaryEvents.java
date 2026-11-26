package fr.worldarchitect.project;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid="worldarchitect")
public final class ProjectBoundaryEvents {
 private ProjectBoundaryEvents(){}
 private static boolean allowed(ServerLevel level,BlockPos pos){
  return ProjectService.forLevel(level).map(p->p.bounds().contains(pos.getX()+0.5,pos.getZ()+0.5)).orElse(true);
 }
 @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
  if(!(event.getEntity() instanceof ServerPlayer player))return;
  var project=ProjectService.forLevel(player.serverLevel()).orElse(null);
  if(project==null)return;
  var b=project.bounds();
  if(b.unlimited()||b.contains(player.getX(),player.getZ()))return;
  double x=Math.max(-b.length()/2.0+0.5,Math.min(b.length()/2.0-0.5,player.getX()));
  double z=Math.max(-b.width()/2.0+0.5,Math.min(b.width()/2.0-0.5,player.getZ()));
  player.teleportTo(player.serverLevel(),x,player.getY(),z,player.getYRot(),player.getXRot());
 }
 private static boolean manualAllowed(ServerLevel level,BlockPos pos,net.minecraft.world.entity.Entity entity){
  if(!allowed(level,pos))return false;
  var project=ProjectService.forLevel(level).orElse(null);
  if(project==null)return true;
  if(!(entity instanceof ServerPlayer player))return false;
  try{ConstructionSessions.requireEditor(player);BlockEditScheduler.requireIdle(player,project);return true;}
  catch(java.io.IOException|RuntimeException ex){return false;}
 }
 @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event){
  if(event.getLevel() instanceof ServerLevel level&&!manualAllowed(level,event.getPos(),event.getPlayer()))event.setCanceled(true);
 }
 @SubscribeEvent public static void placeBlock(BlockEvent.EntityPlaceEvent event){
  if(event.getLevel() instanceof ServerLevel level&&!manualAllowed(level,event.getPos(),event.getEntity()))event.setCanceled(true);
 }
 @SubscribeEvent public static void placeMultiple(BlockEvent.EntityMultiPlaceEvent event){
  if(event.getLevel() instanceof ServerLevel level)
   for(var snapshot:event.getReplacedBlockSnapshots())
    if(!manualAllowed(level,snapshot.getPos(),event.getEntity())){event.setCanceled(true);return;}
 }
}
