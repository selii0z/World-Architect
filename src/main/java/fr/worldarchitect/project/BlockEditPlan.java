package fr.worldarchitect.project;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

public final class BlockEditPlan {
    private BlockEditPlan() {}
    public record Change(BlockPos position, BlockState before, BlockState after) {}
    public static List<Change> prepare(ServerPlayer player, StructureSelection.Selection selection,
            EditGeometry.Shape shape, BlockState target, Block filter) {
        var project=ConstructionSessions.requireEditor(player);
        var level=player.serverLevel();
        if(!selection.dimension().equals(level.dimension()))throw new IllegalArgumentException("Wrong dimension");
        selection.validate(262144);
        var min=selection.min();
        var max=selection.max();
        if(min.getY()<level.getMinBuildHeight()||max.getY()>=level.getMaxBuildHeight()
            ||!project.bounds().contains(min.getX()+0.5,min.getZ()+0.5)
            ||!project.bounds().contains(max.getX()+0.5,max.getZ()+0.5)
            ||!level.getWorldBorder().isWithinBounds(min)||!level.getWorldBorder().isWithinBounds(max))
            throw new IllegalArgumentException("Edit outside project bounds");
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++)
            for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)
                if(!level.hasChunk(x,z))throw new IllegalStateException("Edit contains unloaded chunks");
        if(target.hasBlockEntity())throw new IllegalArgumentException("Block entity targets are not supported");
        var changes=new ArrayList<Change>();
        for(var pos:EditGeometry.positions(min,max,shape)){
            var before=level.getBlockState(pos);
            if(before.equals(target)||(filter!=null&&!before.is(filter)))continue;
            if(before.hasBlockEntity())throw new IllegalArgumentException("Edit would overwrite a block entity");
            changes.add(new Change(pos.immutable(),before,target));
        }
        return List.copyOf(changes);
    }
}
