package fr.worldarchitect.project;

import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid="worldarchitect")
public final class BlockEditScheduler {
    private BlockEditScheduler(){}
    private record Running(UUID owner, BlockEditJob job, java.nio.file.Path archive){}
    private static final Map<MinecraftServer,Map<ResourceKey<Level>,Running>> JOBS=new IdentityHashMap<>();
    public static void submit(ServerPlayer player,StructureSelection.Selection selection,EditGeometry.Shape shape,
            net.minecraft.world.level.block.state.BlockState target,net.minecraft.world.level.block.Block filter) throws java.io.IOException {
        ConstructionSessions.requireEditor(player);
        var server=player.getServer();
        var dimension=player.serverLevel().dimension();
        var jobs=JOBS.computeIfAbsent(server,s->new LinkedHashMap<>());
        if(jobs.containsKey(dimension))throw new IllegalStateException("Construction operation already running");
        var changes=BlockEditPlan.prepare(player,selection,shape,target,filter);
        if(changes.isEmpty())return;
        var project=ConstructionSessions.requireEditor(player);
        var archive=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("worldarchitect/block-edits").resolve(project.id().toString()).resolve(UUID.randomUUID()+".pending.nbt");
        BlockEditArchive.save(archive,player.serverLevel(),player.getUUID(),changes);
        jobs.put(dimension,new Running(player.getUUID(),new BlockEditJob(player.serverLevel(),changes),archive));
    }
    public static boolean cancel(ServerPlayer player){
        if(!player.getServer().isSameThread())throw new IllegalStateException("Server thread required");
        var jobs=JOBS.get(player.getServer());
        if(jobs==null)return false;
        var running=jobs.get(player.serverLevel().dimension());
        if(running==null||!running.owner().equals(player.getUUID()))return false;
        running.job().cancel();return true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var jobs=JOBS.get(event.getServer());
        if(jobs==null)return;
        var iterator=jobs.entrySet().iterator();
        if(!iterator.hasNext())return;
        // Rotate the queue so all dimensions share the same server-wide budget.
        var selected=iterator.next();
        var dimension=selected.getKey();
        var entry=selected.getValue();
        var job=entry.job();
        job.tick(512);
        switch(job.state()){
            case COMPLETE,CANCELLED,FAILED -> {
                iterator.remove();
                try{
                    if(job.state()==BlockEditJob.State.COMPLETE){
                        var destination=entry.archive().resolveSibling(entry.archive().getFileName().toString().replace(".pending.nbt",".complete.nbt"));
                        java.nio.file.Files.move(entry.archive(),destination,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                        var history=BlockEditHistory.load(destination.resolveSibling("history.json"));
                        var id=UUID.fromString(destination.getFileName().toString().replace(".complete.nbt",""));
                        var expired=history.record(new BlockEditHistory.Entry(id,entry.owner()));
                        for(var old:expired){
                            try{java.nio.file.Files.deleteIfExists(destination.resolveSibling(old.archive()+".complete.nbt"));}
                            catch(java.io.IOException cleanup){com.mojang.logging.LogUtils.getLogger().warn("Cannot remove expired edit archive",cleanup);}
                        }
                    }else if(!job.rollbackConflict())java.nio.file.Files.deleteIfExists(entry.archive());
                }catch(java.io.IOException ex){com.mojang.logging.LogUtils.getLogger().error("Cannot finalize block edit archive",ex);}
                var player=event.getServer().getPlayerList().getPlayer(entry.owner());
                if(player!=null)player.sendSystemMessage(net.minecraft.network.chat.Component.literal("World Architect: "+job.state()+" ("+job.applied()+"/"+job.total()+")"+(job.rollbackConflict()?" - restauration incomplète":"")));
            }
            default -> {iterator.remove();jobs.put(dimension,entry);}
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){
        var jobs=JOBS.remove(event.getServer());
        if(jobs!=null&&!jobs.isEmpty())com.mojang.logging.LogUtils.getLogger().warn("Server stopped with {} unfinished construction operations; persistent recovery is not yet implemented",jobs.size());
    }
}
