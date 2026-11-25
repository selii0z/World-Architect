package fr.worldarchitect.project;
import java.util.UUID;
import java.io.IOException;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid="worldarchitect")
public final class VisitSessions {
 private VisitSessions(){}
 public static void enter(ServerPlayer player,UUID projectId) throws IOException {
  Project project=ProjectService.requireProject(player,projectId);
  ServerLevel target=ProjectWorlds.resolve(player,project).orElseThrow(()->new IllegalStateException("Dimension not loaded"));
  if(VisitJournal.read(player)!=null) throw new IllegalStateException("Existing visit");
  VisitJournal.save(player,new VisitJournal.Entry(player.serverLevel().dimension().location().toString(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot(),player.gameMode.getGameModeForPlayer().getName()));
  player.setGameMode(GameType.SPECTATOR);
  player.teleportTo(target,0.5,Math.min(100,target.getMaxBuildHeight()-2),0.5,0,0);
 }
 public static void leave(ServerPlayer player) throws IOException {
  var server=player.getServer();
  if(server==null||!server.isSameThread()) throw new IllegalStateException("Server thread required");
  var point=VisitJournal.read(player);
  if(point==null) return;
  var target=server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(point.dimension())));
  if(target==null) throw new IllegalStateException("Return dimension unavailable");
  var mode=GameType.byName(point.mode());
  if(mode==null) throw new IllegalStateException("Invalid original mode");
  player.teleportTo(target,point.x(),point.y(),point.z(),point.yaw(),point.pitch());
  player.setGameMode(mode);
  server.getPlayerList().saveAll();
  VisitJournal.clear(player);
 }
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
  if(event.getEntity() instanceof ServerPlayer player){
   try {ConstructionSessions.leave(player);} catch(Exception e){
    com.mojang.logging.LogUtils.getLogger().error("Visit recovery failed for {}",player.getUUID(),e);
    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("World Architect : récupération impossible, journal conservé."));
   }
  }
 }
}
