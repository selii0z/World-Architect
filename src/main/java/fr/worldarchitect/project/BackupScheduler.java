package fr.worldarchitect.project;

import java.util.*;
import java.time.Duration;

final class BackupScheduler {
 private static final long INTERVAL=Duration.ofMinutes(5).toNanos();
 private static final long RETRY=Duration.ofSeconds(30).toNanos();
 private final Map<UUID,Long> deadlines=new HashMap<>();
 private final Set<UUID> running=new HashSet<>();
 synchronized boolean claimAutomatic(UUID project,boolean enabled,long now){
  if(!enabled){deadlines.remove(project);return false;}
  long due=deadlines.computeIfAbsent(project,id->now+INTERVAL);
  if(now-due<0||running.contains(project))return false;
  return running.add(project);
 }
 synchronized boolean claimManual(UUID project){return running.add(project);}
 synchronized void complete(UUID project,boolean success,long now){
  if(!running.remove(project))throw new IllegalStateException("No active backup");
  deadlines.put(project,now+(success?INTERVAL:RETRY));
 }
 synchronized boolean busy(UUID project){return running.contains(project);}
 synchronized void forget(UUID project){
  if(running.contains(project))throw new IllegalStateException("Backup in progress");
  deadlines.remove(project);
 }
}
