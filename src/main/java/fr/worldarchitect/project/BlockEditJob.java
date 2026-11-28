package fr.worldarchitect.project;

import java.util.List;
import net.minecraft.server.level.ServerLevel;

public final class BlockEditJob {
    public enum State { READY, APPLYING, ROLLING_BACK, COMPLETE, CANCELLED, FAILED }
    private final ServerLevel level;
    private final List<BlockEditPlan.Change> changes;
    private int applied;
    private State state=State.READY;
    private boolean rollbackConflict;
    private boolean cancelled;
    public BlockEditJob(ServerLevel level,List<BlockEditPlan.Change> changes){
        this.level=java.util.Objects.requireNonNull(level);
        this.changes=List.copyOf(changes);
        if(this.changes.size()>262144)throw new IllegalArgumentException("Too many changes");
    }
    public State state(){return state;}
    public int applied(){return applied;}
    public int total(){return changes.size();}
    public boolean rollbackConflict(){return rollbackConflict;}
    private void checkThread(){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
    }
    public void cancel(){
        checkThread();
        if(state==State.READY||state==State.APPLYING){cancelled=true;state=State.ROLLING_BACK;}
    }
    public void tick(int budget){
        checkThread();
        if(budget<1||budget>4096)throw new IllegalArgumentException("Invalid tick budget");
        if(state==State.READY)state=State.APPLYING;
        while(budget-->0){
            if(state==State.APPLYING){
                if(applied==changes.size()){state=State.COMPLETE;return;}
                var change=changes.get(applied);
                try{
                    if(!level.hasChunkAt(change.position())||!level.getBlockState(change.position()).equals(change.before())){
                        state=State.ROLLING_BACK;continue;
                    }
                    if(!level.setBlock(change.position(),change.after(),2)){
                        if(level.getBlockState(change.position()).equals(change.after()))applied++;
                        state=State.ROLLING_BACK;continue;
                    }
                    applied++;
                }catch(RuntimeException ex){
                    if(level.hasChunkAt(change.position())&&level.getBlockState(change.position()).equals(change.after()))applied++;
                    state=State.ROLLING_BACK;
                }
            }else if(state==State.ROLLING_BACK){
                if(applied==0){state=cancelled&&!rollbackConflict?State.CANCELLED:State.FAILED;return;}
                var change=changes.get(applied-1);
                if(!level.hasChunkAt(change.position()))return;
                try{
                    var current=level.getBlockState(change.position());
                    if(current.equals(change.after())){
                        if(!level.setBlock(change.position(),change.before(),2)&&!level.getBlockState(change.position()).equals(change.before())){rollbackConflict=true;}
                    }else if(!current.equals(change.before()))rollbackConflict=true;
                }catch(RuntimeException ex){rollbackConflict=true;}
                applied--;
            }else return;
        }
    }
}
