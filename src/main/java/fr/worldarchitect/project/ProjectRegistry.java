package fr.worldarchitect.project;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.*;

public final class ProjectRegistry {
 private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
 private record Snapshot(int schema, List<Project> projects) {}
 private final Path file;
 private final Map<UUID, Project> projects = new LinkedHashMap<>();

 public ProjectRegistry(Path directory) throws IOException {
  Files.createDirectories(directory);
  file = directory.resolve("projects.json");
  if (Files.exists(file)) {
   try {
    Snapshot snapshot = GSON.fromJson(Files.readString(file), Snapshot.class);
    if (snapshot == null || snapshot.schema() != 1 || snapshot.projects() == null)
     throw new IOException("Unsupported project registry");
    for (Project p : snapshot.projects()) {
     if (p == null || projects.putIfAbsent(p.id(), p) != null)
      throw new IOException("Invalid or duplicate project");
    }
   } catch (RuntimeException e) { throw new IOException("Cannot read project registry; original preserved", e); }
  }
 }
 public synchronized List<Project> list() { return List.copyOf(projects.values()); }
 public synchronized Optional<Project> find(UUID id) { return Optional.ofNullable(projects.get(id)); }
 public synchronized Project create(String name, Project.WorldType type, int size, String profile, boolean backups) throws IOException {return create(name,type,size,size,profile,backups);}
 public synchronized Project create(String name, Project.WorldType type,int size,int width,String profile,boolean backups) throws IOException {
  Project p = new Project(UUID.randomUUID(), name, type, size, profile, backups,width);
  return register(p);
 }
 public synchronized Project register(Project p) throws IOException {
  if(projects.containsKey(p.id())) throw new IllegalArgumentException("Duplicate project");
  Map<UUID, Project> next = new LinkedHashMap<>(projects);
  next.put(p.id(), p);
  persist(next);
  projects.put(p.id(), p);
  return p;
 }
 private void persist(Map<UUID, Project> next) throws IOException {
  Path temp = Files.createTempFile(file.getParent(), "projects-", ".tmp");
  try {
   Files.writeString(temp, GSON.toJson(new Snapshot(1, List.copyOf(next.values()))), StandardCharsets.UTF_8);
   try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) { channel.force(true); }
   Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  } finally { Files.deleteIfExists(temp); }
 }
}
