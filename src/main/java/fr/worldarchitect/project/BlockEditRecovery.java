package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public final class BlockEditRecovery {
    private BlockEditRecovery(){}
    public record Plan(List<BlockEditPlan.Change> restore,int unchanged,int conflicts){}
    public static Plan inspect(Path archive,ServerLevel level,Project project,UUID owner)throws IOException{
        if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
        var changes=BlockEditArchive.load(archive,level,owner,true);
        var restore=new ArrayList<BlockEditPlan.Change>();
        int unchanged=0,conflicts=0;
        for(var change:changes){
            var p=change.position();
            if(p.getY()<level.getMinBuildHeight()||p.getY()>=level.getMaxBuildHeight()
                ||!project.bounds().contains(p.getX()+0.5,p.getZ()+0.5)
                ||!level.getWorldBorder().isWithinBounds(p)||!level.hasChunkAt(p)){
                conflicts++;continue;
            }
            var current=level.getBlockState(p);
            if(current.equals(change.after()))unchanged++;
            else if(current.equals(change.before())&&!current.hasBlockEntity())restore.add(change);
            else conflicts++;
        }
        return new Plan(List.copyOf(restore),unchanged,conflicts);
    }
}
