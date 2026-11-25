package fr.worldarchitect.project;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import java.util.Optional;

public final class ProjectWorlds {
 private ProjectWorlds(){}
 public static Optional<ServerLevel> resolve(ServerPlayer player, Project project){
  if(!player.hasPermissions(2)) throw new SecurityException("Administrator required");
  var server=player.getServer();
  if(server==null || !server.isSameThread()) throw new IllegalStateException("Server thread required");
  ResourceKey<Level> key=ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(project.dimensionId()));
  return Optional.ofNullable(server.getLevel(key));
 }
 public static void configureBorder(ServerLevel level, Project project){
  if(!level.dimension().location().toString().equals(project.dimensionId())) throw new IllegalArgumentException("Wrong dimension");
  if(!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
  level.getWorldBorder().setCenter(0,0);
  level.getWorldBorder().setSize(project.bounds().unlimited()?59999968:project.bounds().enclosingSquare());
 }
}
