package fr.worldarchitect.project;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class VisitSessions {
 private record ReturnPoint(ResourceKey<Level> dimension,double x,double y,double z,float yaw,float pitch,GameType mode){}
 private static final Map<UUID,ReturnPoint> sessions=new HashMap<>();
 private VisitSessions(){}
 public static void enter(ServerPlayer player,UUID projectId){
  Project project=ProjectService.requireProject(player,projectId);
  ServerLevel target=ProjectWorlds.resolve(player,project).orElseThrow(()->new IllegalStateException("Dimension not loaded"));
  if(sessions.containsKey(player.getUUID())) throw new IllegalStateException("Already visiting");
  var point=new ReturnPoint(player.serverLevel().dimension(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot(),player.gameMode.getGameModeForPlayer());
  sessions.put(player.getUUID(),point);
  player.setGameMode(GameType.SPECTATOR);
  player.teleportTo(target,0.5,Math.min(100,target.getMaxBuildHeight()-2),0.5,0,0);
 }
 public static void leave(ServerPlayer player){
  if(player.getServer()==null || !player.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
  var point=sessions.get(player.getUUID());
  if(point==null) throw new IllegalStateException("No visit");
  var target=player.getServer().getLevel(point.dimension());
  if(target==null) throw new IllegalStateException("Return dimension unavailable");
  player.teleportTo(target,point.x(),point.y(),point.z(),point.yaw(),point.pitch());
  player.setGameMode(point.mode());
  sessions.remove(player.getUUID());
 }
}
