package fr.worldarchitect.project;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

@net.neoforged.fml.common.EventBusSubscriber(modid="worldarchitect")
public final class ConstructionSessions {
 private static final java.util.Map<ServerPlayer,UUID> ACTIVE=new java.util.IdentityHashMap<>();
 public static boolean isolated(ServerPlayer player){return ACTIVE.containsKey(player);}
 @net.neoforged.bus.api.SubscribeEvent
 public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){
  if(!(event.getEntity() instanceof ServerPlayer player))return;
  UUID expected=ACTIVE.get(player);
  if(expected==null)return;
  var project=ProjectService.forLevel(player.serverLevel()).orElse(null);
  if(project!=null&&expected.equals(project.id())&&player.hasPermissions(2)&&player.isCreative())return;
  try{leave(player);}
  catch(Exception e){
   player.setGameMode(GameType.SPECTATOR);
   ACTIVE.remove(player);
   com.mojang.logging.LogUtils.getLogger().error("Construction recovery failed",e);
   player.connection.disconnect(net.minecraft.network.chat.Component.literal("World Architect : récupération impossible. Journaux conservés."));
  }
 }
 @net.neoforged.bus.api.SubscribeEvent
 public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event){
  if(event.getEntity() instanceof ServerPlayer player)ACTIVE.remove(player);
 }
 @net.neoforged.bus.api.SubscribeEvent
 public static void stop(net.neoforged.neoforge.event.server.ServerStoppedEvent event){
  ACTIVE.keySet().removeIf(player->player.getServer()==event.getServer());
 }
 private ConstructionSessions(){}
 // Keep disabled until modded inventories and recovery are validated.
 private static final boolean ENABLED=Boolean.getBoolean("worldarchitect.experimentalConstruction");
 public static Project requireEditor(ServerPlayer player) throws IOException {
  if(!ENABLED)throw new IllegalStateException("Experimental construction disabled; use a disposable test world with -Dworldarchitect.experimentalConstruction=true");
  if(!player.getServer().isSameThread()||!player.hasPermissions(2)||!player.isCreative())throw new IllegalStateException("Construction authorization required");
  if(!InventoryJournal.exists(player)||VisitJournal.read(player)==null)throw new IllegalStateException("No active construction session");
  var project=ProjectService.forLevel(player.serverLevel()).orElseThrow(()->new IllegalStateException("Not a project dimension"));
  if(!project.id().equals(ACTIVE.get(player)))throw new IllegalStateException("Construction session belongs to another project");
  return project;
 }
 public static void enter(ServerPlayer player, UUID projectId) throws IOException {
  if(!ENABLED) throw new IllegalStateException("Experimental construction disabled; use a disposable test world with -Dworldarchitect.experimentalConstruction=true");
  Project project=ProjectService.requireProject(player,projectId);
  var target=ProjectWorlds.resolve(player,project).orElseThrow(()->new IllegalStateException("Dimension not loaded"));
  if(VisitJournal.read(player)!=null||InventoryJournal.exists(player)) throw new IllegalStateException("Unresolved session");
  player.closeContainer();
  VisitJournal.save(player,new VisitJournal.Entry(player.serverLevel().dimension().location().toString(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot(),player.gameMode.getGameModeForPlayer().getName()));
  InventoryJournal.capture(player);
  player.setGameMode(GameType.SPECTATOR);
  player.getInventory().clearContent();
  player.experienceLevel=0;
  player.totalExperience=0;
  player.experienceProgress=0;
  ACTIVE.put(player,project.id());
  player.teleportTo(target,0.5,Math.min(100,target.getMaxBuildHeight()-2),0.5,0,0);
  if(player.serverLevel()!=target)throw new IllegalStateException("Construction teleport failed");
  player.setGameMode(GameType.CREATIVE);
 }
 public static void leave(ServerPlayer player) throws IOException {
  if(player.getServer()==null||!player.getServer().isSameThread())throw new IllegalStateException("Server thread required");
  if(InventoryJournal.exists(player)){
   var point=VisitJournal.read(player);
   if(point==null)throw new IOException("Missing visit recovery journal");
   var returnKey=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(point.dimension()));
   var target=player.getServer().getLevel(returnKey);
   if(target==null)throw new IOException("Return dimension unavailable");
   player.closeContainer();
   player.setGameMode(GameType.SPECTATOR);
   player.teleportTo(target,point.x(),point.y(),point.z(),point.yaw(),point.pitch());
   if(player.serverLevel()!=target)throw new IOException("Return teleport failed");
   InventoryJournal.restore(player);
   player.getServer().getPlayerList().saveAll();
   InventoryJournal.clearAfterConfirmedSave(player);
  }
  VisitSessions.leave(player);
  ACTIVE.remove(player);
 }
}
