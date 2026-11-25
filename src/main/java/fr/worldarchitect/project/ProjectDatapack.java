package fr.worldarchitect.project;
import java.nio.file.*;
import java.io.IOException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class ProjectDatapack {
 private ProjectDatapack(){}
 public static void stage(MinecraftServer server, Project project) throws IOException {
  if(!server.isSameThread()) throw new IllegalStateException("Server thread required");
  Path pack=server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("worldarchitect_projects");
  Files.createDirectories(pack);
  Path metadata=pack.resolve("pack.mcmeta");
  if(Files.notExists(metadata)){
   Files.writeString(metadata,"{\"pack\":{\"pack_format\":48,\"description\":\"World Architect project dimensions\"}}",StandardOpenOption.CREATE_NEW);
  }
  DimensionDefinitions.stage(project,pack);
 }
}
