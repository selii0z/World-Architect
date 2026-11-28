package fr.worldarchitect.project;
import java.nio.file.Path;
public final class OverviewJob {
 public record Status(String phase,int done,int total,String error){}
 private volatile Status status=new Status("idle",0,0,"");
 private Thread worker;
 public Status status(){return status;}
 public synchronized void start(Path snapshot,Path output,int x,int z,int length,int width){
  if(worker!=null&&worker.isAlive())throw new IllegalStateException("Overview already running");
  status=new Status("running",0,0,"");
  worker=new Thread(()->{
   try{
    var overview=OfflineAerialBuilder.build(snapshot,x,z,length,width,(done,total)->status=new Status("running",done,total,""));
    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
    OverviewStorage.save(output,overview);
    status=new Status("complete",status.total(),status.total(),"");
   }catch(Exception e){
    status=new Status(Thread.currentThread().isInterrupted()||e instanceof InterruptedException?"cancelled":"failed",status.done(),status.total(),String.valueOf(e.getMessage()));
   }
  },"worldarchitect-overview");
  worker.setDaemon(true);worker.start();
 }
 public synchronized void cancel(){if(worker!=null&&worker.isAlive())worker.interrupt();}
}
