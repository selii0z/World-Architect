package fr.worldarchitect.project;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public final class ConstructionSessions {
 private ConstructionSessions(){}
 // Keep disabled until modded inventories and recovery are validated.
 private static final boolean ENABLED=Boolean.getBoolean("worldarchitect.experimentalConstruction");
 public static Project requireEditor(ServerPlayer player) throws IOException {
  if(!ENABLED)throw new IllegalStateException("Experimental construction disabled; use a disposable test world with -Dworldarchitect.experimentalConstruction=true");
  if(!player.getServer().isSameThread()||!player.hasPermissions(2)||!player.isCreative())throw new IllegalStateException("Construction authorization required");
  if(!InventoryJournal.exists(player)||VisitJournal.read(player)==null)throw new IllegalStateException("No active construction session");
  return ProjectService.forLevel(player.serverLevel()).orElseThrow(()->new IllegalStateException("Not a project dimension"));
 }
 public static void enter(ServerPlayer player, UUID projectId) throws IOException {
  if(!ENABLED) throw new IllegalStateException("Experimental construction disabled; use a disposable test world with -Dworldarchitect.experimentalConstruction=true");
  Project project=ProjectService.requireProject(player,projectId);
  var target=ProjectWorlds.resolve(player,project).orElseThrow(()->new IllegalStateException("Dimension not loaded"));
  if(VisitJournal.read(player)!=null||InventoryJournal.exists(player)) throw new IllegalStateException("Unresolved session");
  player.closeContainer();
  VisitJournal.save(player,new VisitJournal.Entry(player.serverLevel().dimension().location().toString(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot(),player.gameMode.getGameModeForPlayer().getName()));
  InventoryJournal.capture(player);
  player.getInventory().clearContent();
  player.setGameMode(GameType.SPECTATOR);
  player.teleportTo(target,0.5,Math.min(100,target.getMaxBuildHeight()-2),0.5,0,0);
  player.setGameMode(GameType.CREATIVE);
 }
 public static void leave(ServerPlayer player) throws IOException {
  if(InventoryJournal.exists(player)){
   player.closeContainer();
   player.setGameMode(GameType.SPECTATOR);
   InventoryJournal.restore(player);
   player.getServer().getPlayerList().saveAll();
   InventoryJournal.clearAfterConfirmedSave(player);
  }
  VisitSessions.leave(player);
 }
}
