package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

public final class ImportedTerrain {
    private ImportedTerrain() {}

    /** Only for a newly allocated dimension which has never been loaded. */
    public static Path prepare(MinecraftServer server, Project project, Path source) throws IOException {
        if (project.worldType() != Project.WorldType.IMPORTED)
            throw new IllegalArgumentException("Imported project required");
        var location = net.minecraft.resources.ResourceLocation.parse(project.dimensionId());
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, location);
        if (server.getLevel(key) != null) throw new IOException("Dimension already loaded");
        Path destination = server.getWorldPath(LevelResource.ROOT)
            .resolve("dimensions").resolve(location.getNamespace()).resolve(location.getPath());
        return MapTerrain.snapshot(source, destination);
    }
}
