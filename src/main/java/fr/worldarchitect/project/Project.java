package fr.worldarchitect.project;
import java.util.UUID;
import java.util.Objects;
public record Project(UUID id, String name, WorldType worldType, int size, String profile, boolean automaticBackups) {
 public enum WorldType { NATURAL, FLAT, VOID, IMPORTED }
 public Project {
  Objects.requireNonNull(id);
  Objects.requireNonNull(worldType);
  name = Objects.requireNonNull(name).strip();
  profile = Objects.requireNonNull(profile).strip();
  if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("Invalid project name");
  if (size != 0 && (size < 16 || size > 60000)) throw new IllegalArgumentException("Invalid project size");
  if (!profile.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid profile");
 }
 public String dimensionId() { return "worldarchitect:project_" + id.toString().replace("-", ""); }
}
